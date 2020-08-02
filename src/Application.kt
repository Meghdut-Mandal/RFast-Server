package com.meghdut.rfast

import com.google.gson.Gson
import io.ktor.application.Application
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.features.CORS
import io.ktor.features.CallLogging
import io.ktor.features.ContentNegotiation
import io.ktor.gson.gson
import io.ktor.html.respondHtml
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.cio.websocket.*
import io.ktor.http.content.resources
import io.ktor.http.content.static
import io.ktor.locations.Location
import io.ktor.locations.Locations
import io.ktor.locations.post
import io.ktor.request.path
import io.ktor.request.receive
import io.ktor.request.receiveText
import io.ktor.response.respond
import io.ktor.routing.get
import io.ktor.routing.routing
import io.ktor.websocket.WebSockets
import io.ktor.websocket.webSocket
import kotlinx.coroutines.channels.consumeEach
import kotlinx.html.body
import kotlinx.html.h1
import kotlinx.html.li
import kotlinx.html.ul
import org.slf4j.event.Level
import java.security.MessageDigest
import java.time.Duration
import java.util.*


fun main(args: Array<String>): Unit = io.ktor.server.netty.EngineMain.main(args)


val clientMap = hashMapOf<String, ClientConnection>()
data class SessionInfo(val deviceType:String,val instanceKey:String)

@Location("client/new")
class NewClient()


fun hash(text: String): String {
    val data = text.toByteArray()
    val digest = MessageDigest.getInstance("SHA-256")
    digest.update(data)
    return Base64.getEncoder().encodeToString(digest.digest())
}
@Suppress("unused") // Referenced in application.conf
@kotlin.jvm.JvmOverloads
fun Application.module(testing: Boolean = false) {
    install(Locations)

    install(CallLogging) {
        level = Level.INFO
        filter { call -> call.request.path().startsWith("/") }
    }

    install(CORS) {
        method(HttpMethod.Options)
        method(HttpMethod.Put)
        method(HttpMethod.Delete)
        method(HttpMethod.Patch)
        header(HttpHeaders.Authorization)
        allowCredentials = true
    }

    install(WebSockets) {
        pingPeriod = Duration.ofSeconds(15)
        timeout = Duration.ofSeconds(15)
        maxFrameSize = Long.MAX_VALUE
        masking = false
    }

    install(ContentNegotiation) {
        gson { }
    }

    routing {

        get("/") {
            call.respondHtml {
                body {
                    h1 { +"HTML" }
                    ul {
                        for (n in 1..10) {
                            li { +"$n" }
                        }
                    }
                }
            }
        }

        // Static feature. Try to access `/static/ktor_logo.svg`
        static("/static") {
            resources("static")
        }

        post<NewClient> {
            val clientInfo = call.receiveText()
            if (clientInfo.isBlank())
                return@post call.respond(HttpStatusCode.BadRequest, "Client Info is needed ")
            val hash = hash(clientInfo)
            clientMap.computeIfAbsent(hash) { ClientConnection(hash) }
            println("com.meghdut.rfast>>module  Client  key=$hash $clientInfo")
            return@post call.respond(HttpStatusCode.OK, hash)
        }


        webSocket("/desktop_client") {
            val infoHeader = call.request.headers["Info"]
            val sessionInfo = Gson().fromJson(infoHeader,SessionInfo::class.java)
            if (!clientMap.keys.contains(sessionInfo.instanceKey)) {
                close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "No session"))
                return@webSocket
            }
            val server = clientMap[sessionInfo.instanceKey] ?: return@webSocket
            server.memberJoin(sessionInfo.deviceType,this)
            try {
                // We starts receiving messages (frames).
                // Since this is a coroutine. This coroutine is suspended until receiving frames.
                // Once the connection is closed, this consumeEach will finish and the code will continue.
                incoming.consumeEach { frame ->
                    // Frames can be [Text], [Binary], [Ping], [Pong], [Close].
                    // We are only interested in textual messages, so we filter it.
                    if (frame is Frame.Text) {
                        // Now it is time to process the text sent from the user.
                        // At this point we have context about this connection, the session, the text and the server.
                        // So we have everything we need.
                        server.message(sessionInfo.deviceType,frame.readText())
                    }
                }
            } finally {
                // Either if there was an error, of it the connection was closed gracefully.
                // We notify the server that the member left.
                server.memberLeft(sessionInfo.deviceType, this)
            }
        }

    }
}

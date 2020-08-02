package com.meghdut.rfast

import io.ktor.http.cio.websocket.Frame
import io.ktor.http.cio.websocket.WebSocketSession
import java.util.*
import java.util.concurrent.ConcurrentHashMap

class ClientConnection(val instanceKey: String) {
//    val instanceKey = "${System.currentTimeMillis()}"

    val memberLeft = "left"
    val memberJoined = "joined"
    val messageOpcode = "message"

    val socketSessions = ConcurrentHashMap<String, WebSocketSession>()
    val lastMessages = LinkedList<String>()


    suspend fun memberJoin(member: String, socket: WebSocketSession) {
        socketSessions[member] = socket
        codeMessage(memberJoined, member)
        println("com.meghdut.rfast>ClientConnection>memberJoin  Joinned $member  ")
    }

    /**
     * Handles that a [member] with a specific [socket] left the server.
     */
    suspend fun memberLeft(member: String, socket: WebSocketSession) {
        // Removes the socket connection for this member
        socketSessions.remove(member)
        codeMessage(memberLeft, member)
    }


    /**
     * Handles a [message] sent from a [sender] by notifying the rest of the users.
     */
    suspend fun message(sender: String, message: String) {
        codeMessage(messageOpcode, sender, message)
    }


    suspend fun codeMessage(vararg data: String) {
        val message = data.toList().joinToString("|")
        broadcast(message)
        synchronized(lastMessages) {
            lastMessages.add(message)
            if (lastMessages.size > 100) {
                lastMessages.removeFirst()
            }
        }
    }


    /**
     * Sends a [message] to all the members in the server, including all the connections per member.
     */
    private suspend fun broadcast(message: String) {
        socketSessions.values.forEach { socket ->
            socket.send(Frame.Text(message))
        }
    }

}



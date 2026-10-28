package com.tananaev.passportreader

import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Test helper (finding #52): a real HTTP/1.1 server on a loopback socket. `POST /post` answers [postStatus] with a
 * `Location` header pointing at `/landing`; `/landing` answers 200 to anything. Every request is recorded, so a test
 * can prove a redirect was NOT followed (the landing handler saw zero requests). [postBody] is the body of the /post reply.
 */
class RedirectServer(private val postStatus: Int, private val postBody: String = "") : AutoCloseable {

    class Seen(val method: String, val path: String, val body: String)

    val seen = CopyOnWriteArrayList<Seen>()
    private val listener = ServerSocket(0, 5, InetAddress.getByName("127.0.0.1"))
    val postUrl = "http://127.0.0.1:${listener.localPort}/post"
    fun landingHits(): Int = seen.count { it.path == "/landing" }
    fun postHits(): Int = seen.count { it.path == "/post" }

    init {
        Thread {
            try {
                while (true) {
                    listener.accept().use { sock ->
                        val input = sock.getInputStream().buffered()
                        fun line(): String {
                            val sb = StringBuilder()
                            while (true) {
                                val c = input.read()
                                if (c < 0 || c == '\n'.code) break
                                if (c != '\r'.code) sb.append(c.toChar())
                            }
                            return sb.toString()
                        }
                        val parts = line().split(" ")
                        var length = 0
                        while (true) {
                            val h = line()
                            if (h.isEmpty()) break
                            if (h.substringBefore(":").trim().lowercase() == "content-length") length = h.substringAfter(":").trim().toInt()
                        }
                        val body = ByteArray(length).also { buf -> var off = 0; while (off < length) { val n = input.read(buf, off, length - off); if (n < 0) break; off += n } }
                        seen.add(Seen(parts[0], parts[1], body.toString(Charsets.UTF_8)))
                        val out = if (parts[1] == "/post") {
                            "HTTP/1.1 $postStatus X\r\nlocation: http://127.0.0.1:${listener.localPort}/landing\r\ncontent-length: ${postBody.length}\r\nconnection: close\r\n\r\n$postBody"
                        } else {
                            "HTTP/1.1 200 OK\r\ncontent-length: 2\r\nconnection: close\r\n\r\nOK"
                        }
                        sock.getOutputStream().write(out.toByteArray())
                    }
                }
            } catch (e: java.io.IOException) {
                // listener closed: the test is over
            }
        }.apply { isDaemon = true }.start()
    }

    override fun close() = listener.close()
}

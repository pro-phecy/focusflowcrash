package com.example.launcher

import android.util.Log
import kotlinx.coroutines.*
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.Socket
import java.util.Collections

object FocusWebServer {
    private const val TAG = "FocusWebServer"
    private const val PORT = 8082
    private var serverSocket: ServerSocket? = null
    private var serverJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Keep track of active SSE connection streams
    private val activeClients = Collections.synchronizedSet(mutableSetOf<OutputStream>())

    fun start() {
        if (serverJob != null && serverJob?.isActive == true) {
            Log.d(TAG, "Server already running.")
            return
        }

        serverJob = scope.launch {
            try {
                serverSocket = ServerSocket(PORT)
                Log.i(TAG, "FocusFlow Companion Server started on port $PORT")
                while (isActive) {
                    val socket = serverSocket?.accept() ?: break
                    launch(Dispatchers.IO) {
                        handleClient(socket)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in server socket loop", e)
            }
        }
    }

    fun stop() {
        serverJob?.cancel()
        try {
            serverSocket?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing server socket", e)
        }
        serverSocket = null
        serverJob = null
        activeClients.clear()
    }

    private suspend fun handleClient(socket: Socket) {
        withContext(Dispatchers.IO) {
            try {
                val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
                val out = socket.getOutputStream()

                val requestLine = reader.readLine() ?: return@withContext
                Log.d(TAG, "Request: $requestLine")

                // Simple HTTP parser
                val parts = requestLine.split(" ")
                if (parts.size < 2) {
                    socket.close()
                    return@withContext
                }

                val method = parts[0]
                val path = parts[1]

                // Consume remainder of headers
                var line: String? = reader.readLine()
                while (!line.isNullOrEmpty()) {
                    line = reader.readLine()
                }

                if (path == "/events") {
                    // Handle Server-Sent Events (SSE)
                    val headers = """
                        HTTP/1.1 200 OK
                        Content-Type: text/event-stream
                        Cache-Control: no-cache
                        Connection: keep-alive
                        Access-Control-Allow-Origin: *
                        
                        
                    """.trimIndent()
                    out.write(headers.replace("\n", "\r\n").toByteArray())
                    out.flush()

                    activeClients.add(out)
                    Log.d(TAG, "New SSE Client connected. Active clients: ${activeClients.size}")

                    // Keep-alive loop
                    try {
                        while (socket.isConnected && !socket.isClosed) {
                            delay(15000)
                            out.write(": keepalive\r\n\r\n".toByteArray())
                            out.flush()
                        }
                    } catch (e: Exception) {
                        // Client disconnected
                    } finally {
                        activeClients.remove(out)
                        Log.d(TAG, "SSE Client disconnected. Active clients: ${activeClients.size}")
                        try {
                            socket.close()
                        } catch (e: Exception) {}
                    }
                } else {
                    // Serve index.html
                    val html = getHtmlContent()
                    val htmlBytes = html.toByteArray(Charsets.UTF_8)
                    val responseHeaders = """
                        HTTP/1.1 200 OK
                        Content-Type: text/html; charset=UTF-8
                        Content-Length: ${htmlBytes.size}
                        Access-Control-Allow-Origin: *
                        Connection: close
                        
                        
                    """.trimIndent()

                    out.write(responseHeaders.replace("\n", "\r\n").toByteArray())
                    out.write(htmlBytes)
                    out.flush()
                    socket.close()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error handling client socket", e)
                try {
                    socket.close()
                } catch (ex: Exception) {}
            }
        }
    }

    fun broadcastEvent(type: String, goal: String = "") {
        scope.launch(Dispatchers.IO) {
            val json = """{"type":"$type","goal":"$goal"}"""
            val sseData = "data: $json\r\n\r\n"
            val dataBytes = sseData.toByteArray(Charsets.UTF_8)

            synchronized(activeClients) {
                val iterator = activeClients.iterator()
                while (iterator.hasNext()) {
                    val client = iterator.next()
                    try {
                        client.write(dataBytes)
                        client.flush()
                    } catch (e: Exception) {
                        Log.d(TAG, "Removing dead SSE client stream")
                        iterator.remove()
                    }
                }
            }
        }
    }

    private fun getHtmlContent(): String {
        return """
            <!DOCTYPE html>
            <html lang="en">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>FocusFlow Companion</title>
                <style>
                    body {
                        font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
                        background: #09090b;
                        color: #f4f4f5;
                        max-width: 600px;
                        margin: 60px auto;
                        padding: 30px;
                        border-radius: 16px;
                        box-shadow: 0 10px 30px rgba(0,0,0,0.6);
                        text-align: center;
                    }
                    h1 {
                        color: #ffffff;
                        font-size: 28px;
                        margin-bottom: 8px;
                        font-weight: 700;
                    }
                    .subtitle {
                        color: #a1a1aa;
                        font-size: 15px;
                        margin-bottom: 30px;
                    }
                    .btn {
                        background: #2563eb;
                        color: white;
                        border: none;
                        padding: 12px 28px;
                        border-radius: 8px;
                        cursor: pointer;
                        font-size: 16px;
                        font-weight: 600;
                        transition: background 0.2s, transform 0.1s;
                        box-shadow: 0 4px 12px rgba(37, 99, 235, 0.3);
                        margin: 10px;
                    }
                    .btn:hover {
                        background: #1d4ed8;
                    }
                    .btn:active {
                        transform: scale(0.98);
                    }
                    .status {
                        margin: 24px 0;
                        padding: 20px;
                        background: #18181b;
                        border: 1px solid #27272a;
                        border-radius: 12px;
                        font-size: 18px;
                        transition: background 0.3s;
                    }
                    .status.active {
                        background: #064e3b;
                        border-color: #059669;
                    }
                    .info {
                        color: #71717a;
                        font-size: 13px;
                        margin-top: 40px;
                        line-height: 1.5;
                    }
                    strong {
                        color: #ffffff;
                    }
                </style>
            </head>
            <body>
                <h1>FocusFlow Companion</h1>
                <p class="subtitle">This companion webpage triggers browser-native desktop notifications in real-time when your FocusFlow session begins or ends.</p>
                
                <div class="status" id="status">🔄 Connecting to FocusFlow Android app...</div>
                
                <button class="btn" id="notify-btn">🔔 Enable Browser Notifications</button>
                <button class="btn" id="test-btn" style="background: #3f3f46; box-shadow: none;">Test Notification</button>

                <p class="info">
                    Make sure browser notification permissions are allowed.<br>
                    Keep this tab open or running in the background to receive real-time alerts.
                </p>

                <script>
                    const statusDiv = document.getElementById('status');
                    const notifyBtn = document.getElementById('notify-btn');
                    const testBtn = document.getElementById('test-btn');

                    // Request notification permission
                    notifyBtn.addEventListener('click', () => {
                        if (!("Notification" in window)) {
                            alert("This browser does not support desktop notifications.");
                            return;
                        }
                        Notification.requestPermission().then(permission => {
                            if (permission === 'granted') {
                                new Notification('FocusFlow Connected', {
                                    body: 'Browser notifications are now connected and enabled!',
                                    icon: 'https://cdn-icons-png.flaticon.com/512/3569/3569434.png'
                                });
                            } else {
                                alert("Notification permission was denied.");
                            }
                        });
                    });

                    // Test notification
                    testBtn.addEventListener('click', () => {
                        if (Notification.permission === 'granted') {
                            new Notification('FocusFlow Test', {
                                body: 'Your browser notifications are working perfectly!',
                                icon: 'https://cdn-icons-png.flaticon.com/512/3569/3569434.png'
                            });
                        } else {
                            alert("Please enable notifications first by clicking the blue button.");
                        }
                    });

                    // Connect to Server-Sent Events (SSE)
                    function connect() {
                        const eventSource = new EventSource('/events');
                        
                        eventSource.onopen = () => {
                            statusDiv.innerHTML = "🟢 <strong>Companion Connected</strong><br><span style='font-size: 14px; color: #a1a1aa;'>Listening for FocusFlow app events...</span>";
                            statusDiv.className = "status";
                        };

                        eventSource.onmessage = (event) => {
                            console.log("Received event:", event.data);
                            try {
                                const data = JSON.parse(event.data);
                                if (data.type === 'start') {
                                    statusDiv.innerHTML = "🔥 <strong>Focus Session Active</strong><br><span style='font-size: 16px; color: #34d399;'>Goal: " + (data.goal || 'Deep Focus') + "</span>";
                                    statusDiv.className = "status active";
                                    
                                    if (Notification.permission === 'granted') {
                                        new Notification('Focus Session Started!', {
                                            body: 'Time to focus! Current task: ' + (data.goal || 'Deep Focus'),
                                            tag: 'focusflow-session',
                                            requireInteraction: true,
                                            icon: 'https://cdn-icons-png.flaticon.com/512/3569/3569434.png'
                                        });
                                    }
                                } else if (data.type === 'end') {
                                    statusDiv.innerHTML = "😴 <strong>Focus Session Completed!</strong><br><span style='font-size: 14px; color: #a1a1aa;'>Excellent job! You finished your session.</span>";
                                    statusDiv.className = "status";
                                    
                                    if (Notification.permission === 'granted') {
                                        new Notification('Focus Session Completed! 🎉', {
                                            body: 'Fantastic work! You successfully completed your session.',
                                            tag: 'focusflow-session',
                                            requireInteraction: true,
                                            icon: 'https://cdn-icons-png.flaticon.com/512/1164/1164620.png'
                                        });
                                    }
                                } else if (data.type === 'cancel') {
                                    statusDiv.innerHTML = "⏹️ <strong>Focus Session Ended Early</strong><br><span style='font-size: 14px; color: #a1a1aa;'>Ready for the next one.</span>";
                                    statusDiv.className = "status";
                                    
                                    if (Notification.permission === 'granted') {
                                        new Notification('Focus Session Stopped', {
                                            body: 'The active focus session was ended early.',
                                            tag: 'focusflow-session',
                                            icon: 'https://cdn-icons-png.flaticon.com/512/3569/3569434.png'
                                        });
                                    }
                                } else if (data.type === 'pause') {
                                    statusDiv.innerHTML = "⏸️ <strong>Focus Session Paused</strong><br><span style='font-size: 14px; color: #a1a1aa;'>Remaining time is paused.</span>";
                                    statusDiv.className = "status";
                                    
                                    if (Notification.permission === 'granted') {
                                        new Notification('Focus Session Paused', {
                                            body: 'The session has been paused.',
                                            tag: 'focusflow-session',
                                            icon: 'https://cdn-icons-png.flaticon.com/512/3569/3569434.png'
                                        });
                                    }
                                } else if (data.type === 'resume') {
                                    statusDiv.innerHTML = "🔥 <strong>Focus Session Active (Resumed)</strong><br><span style='font-size: 16px; color: #34d399;'>Goal: " + (data.goal || 'Deep Focus') + "</span>";
                                    statusDiv.className = "status active";
                                    
                                    if (Notification.permission === 'granted') {
                                        new Notification('Focus Session Resumed!', {
                                            body: 'Let\'s get back to focusing!',
                                            tag: 'focusflow-session',
                                            icon: 'https://cdn-icons-png.flaticon.com/512/3569/3569434.png'
                                        });
                                    }
                                }
                            } catch(e) {
                                console.error("Error parsing event:", e);
                            }
                        };

                        eventSource.onerror = () => {
                            statusDiv.innerHTML = "🔴 <strong>FocusFlow App Disconnected</strong><br><span style='font-size: 14px; color: #f87171;'>Attempting to reconnect...</span>";
                            statusDiv.className = "status";
                            eventSource.close();
                            setTimeout(connect, 3000);
                        };
                    }

                    connect();
                </script>
            </body>
            </html>
        """.trimIndent()
    }
}

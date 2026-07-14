package com.example.launcher

import android.content.Context
import android.util.Log
import com.example.data.AppDatabase
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
    private var appContext: Context? = null

    // Keep track of active SSE connection streams
    private val activeClients = Collections.synchronizedSet(mutableSetOf<OutputStream>())

    fun start(context: Context) {
        appContext = context.applicationContext
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
                } else if (path == "/sw.js") {
                    // Serve Service Worker
                    val sw = getServiceWorkerContent()
                    val swBytes = sw.toByteArray(Charsets.UTF_8)
                    val responseHeaders = """
                        HTTP/1.1 200 OK
                        Content-Type: application/javascript; charset=UTF-8
                        Content-Length: ${swBytes.size}
                        Access-Control-Allow-Origin: *
                        Connection: close
                        
                        
                    """.trimIndent()

                    out.write(responseHeaders.replace("\n", "\r\n").toByteArray())
                    out.write(swBytes)
                    out.flush()
                    socket.close()
                } else {
                    // Serve index.html
                    val html = getHtmlContent(appContext)
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

    fun broadcastEvent(type: String, goal: String = "", durationSeconds: Int = 0) {
        scope.launch(Dispatchers.IO) {
            val json = """{"type":"$type","goal":"$goal","durationSeconds":$durationSeconds}"""
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

    private suspend fun getHtmlContent(context: Context?): String {
        var notifsEnabled = true
        if (context != null) {
            try {
                val db = AppDatabase.getDatabase(context)
                val profile = db.userProfileDao().getProfile()
                if (profile != null) {
                    notifsEnabled = profile.notifications ?: true
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

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
                    let activeNotifications = [];
                    let appNotificationsEnabled = $notifsEnabled;

                    if (!appNotificationsEnabled) {
                        statusDiv.innerHTML = "🔕 <strong>Notifications Disabled in App</strong><br><span style='font-size: 14px; color: #a1a1aa;'>Please enable notifications in the FocusFlow Android settings.</span>";
                    }

                    // Register Service Worker
                    let swRegistration = null;
                    if ('serviceWorker' in navigator) {
                        navigator.serviceWorker.register('/sw.js')
                            .then(registration => {
                                console.log('Service Worker registered with scope:', registration.scope);
                                swRegistration = registration;
                            })
                            .catch(error => {
                                console.error('Service Worker registration failed:', error);
                            });
                    }

                    function trackNotification(notification) {
                        if (notification) {
                            activeNotifications.push(notification);
                            notification.onclose = () => {
                                activeNotifications = activeNotifications.filter(n => n !== notification);
                            };
                        }
                    }

                    // Helper function to show notifications (routes via Service Worker if available for background reliability)
                    function showWebNotification(title, bodyText, tagId, iconUrl) {
                        if (!appNotificationsEnabled) return;
                        if (Notification.permission !== 'granted') return;

                        const options = {
                            body: bodyText,
                            tag: tagId || 'focusflow-session',
                            requireInteraction: true,
                            icon: iconUrl || 'https://cdn-icons-png.flaticon.com/512/3569/3569434.png'
                        };

                        if (swRegistration && swRegistration.active) {
                            swRegistration.active.postMessage({
                                action: 'show-notification',
                                title: title,
                                body: options.body,
                                tag: options.tag,
                                icon: options.icon
                            });
                        } else {
                            try {
                                const notification = new Notification(title, options);
                                trackNotification(notification);
                            } catch (e) {
                                console.error('Standard Notification failed:', e);
                            }
                        }
                    }

                    // Request notification permission
                    notifyBtn.addEventListener('click', () => {
                        if (!("Notification" in window)) {
                            alert("This browser does not support desktop notifications.");
                            return;
                        }
                        Notification.requestPermission().then(permission => {
                            if (permission === 'granted') {
                                showWebNotification('FocusFlow Connected', 'Browser notifications are now connected and enabled!', 'focusflow-connected');
                            } else {
                                alert("Notification permission was denied.");
                            }
                        });
                    });

                    // Test notification
                    testBtn.addEventListener('click', () => {
                        if (!appNotificationsEnabled) {
                            alert("Notifications are currently disabled in the FocusFlow Android settings.");
                            return;
                        }
                        if (Notification.permission === 'granted') {
                            showWebNotification('FocusFlow Test', 'Your browser notifications are working perfectly!', 'focusflow-test');
                        } else {
                            alert("Please enable notifications first by clicking the blue button.");
                        }
                    });

                    // Connect to Server-Sent Events (SSE)
                    function connect() {
                        const eventSource = new EventSource('/events');
                        
                        eventSource.onopen = () => {
                            if (appNotificationsEnabled) {
                                statusDiv.innerHTML = "🟢 <strong>Companion Connected</strong><br><span style='font-size: 14px; color: #a1a1aa;'>Listening for FocusFlow app events...</span>";
                            } else {
                                statusDiv.innerHTML = "🔕 <strong>Notifications Disabled in App</strong><br><span style='font-size: 14px; color: #a1a1aa;'>Please enable notifications in the FocusFlow Android settings.</span>";
                            }
                            statusDiv.className = "status";
                        };

                        eventSource.onmessage = (event) => {
                            console.log("Received event:", event.data);
                            try {
                                const data = JSON.parse(event.data);
                                
                                if (data.type === 'disable_notifications') {
                                    appNotificationsEnabled = false;
                                    statusDiv.innerHTML = "🔕 <strong>Notifications Disabled in App</strong><br><span style='font-size: 14px; color: #a1a1aa;'>Please enable notifications in the FocusFlow Android settings.</span>";
                                    statusDiv.className = "status";
                                    
                                    // Close and clear standard notifications
                                    activeNotifications.forEach(n => {
                                        try { n.close(); } catch(e) {}
                                    });
                                    activeNotifications = [];

                                    // Cancel Service Worker timers and close SW notifications
                                    if (swRegistration && swRegistration.active) {
                                        swRegistration.active.postMessage({
                                            action: 'cancel-notification',
                                            id: 'session-end'
                                        });
                                        swRegistration.active.postMessage({
                                            action: 'close-all-notifications'
                                        });
                                    }
                                    return;
                                } else if (data.type === 'enable_notifications') {
                                    appNotificationsEnabled = true;
                                    statusDiv.innerHTML = "🟢 <strong>Companion Connected</strong><br><span style='font-size: 14px; color: #a1a1aa;'>Listening for FocusFlow app events...</span>";
                                    statusDiv.className = "status";
                                    return;
                                }

                                if (data.type === 'start') {
                                    statusDiv.innerHTML = "🔥 <strong>Focus Session Active</strong><br><span style='font-size: 16px; color: #34d399;'>Goal: " + (data.goal || 'Deep Focus') + "</span>";
                                    statusDiv.className = "status active";
                                    
                                    showWebNotification('Focus Session Started!', 'Time to focus! Current task: ' + (data.goal || 'Deep Focus'), 'focusflow-session', 'https://cdn-icons-png.flaticon.com/512/3569/3569434.png');

                                    // Schedule end alert via Service Worker to bypass browser-native background throttling
                                    if (data.durationSeconds && data.durationSeconds > 0 && swRegistration && swRegistration.active) {
                                        swRegistration.active.postMessage({
                                            action: 'schedule-notification',
                                            id: 'session-end',
                                            title: 'Focus Session Completed! 🎉',
                                            body: 'Fantastic work! You successfully completed your session.',
                                            delayMs: data.durationSeconds * 1000,
                                            icon: 'https://cdn-icons-png.flaticon.com/512/1164/1164620.png',
                                            tag: 'focusflow-session'
                                        });
                                    }
                                } else if (data.type === 'end') {
                                    statusDiv.innerHTML = "😴 <strong>Focus Session Completed!</strong><br><span style='font-size: 14px; color: #a1a1aa;'>Excellent job! You finished your session.</span>";
                                    statusDiv.className = "status";
                                    
                                    showWebNotification('Focus Session Completed! 🎉', 'Fantastic work! You successfully completed your session.', 'focusflow-session', 'https://cdn-icons-png.flaticon.com/512/1164/1164620.png');

                                    // Cancel any pending timer in SW
                                    if (swRegistration && swRegistration.active) {
                                        swRegistration.active.postMessage({
                                            action: 'cancel-notification',
                                            id: 'session-end'
                                        });
                                    }
                                } else if (data.type === 'cancel') {
                                    statusDiv.innerHTML = "⏹️ <strong>Focus Session Ended Early</strong><br><span style='font-size: 14px; color: #a1a1aa;'>Ready for the next one.</span>";
                                    statusDiv.className = "status";
                                    
                                    showWebNotification('Focus Session Stopped', 'The active focus session was ended early.', 'focusflow-session', 'https://cdn-icons-png.flaticon.com/512/3569/3569434.png');

                                    // Cancel any pending timer in SW
                                    if (swRegistration && swRegistration.active) {
                                        swRegistration.active.postMessage({
                                            action: 'cancel-notification',
                                            id: 'session-end'
                                        });
                                    }
                                } else if (data.type === 'pause') {
                                    statusDiv.innerHTML = "⏸️ <strong>Focus Session Paused</strong><br><span style='font-size: 14px; color: #a1a1aa;'>Remaining time is paused.</span>";
                                    statusDiv.className = "status";
                                    
                                    showWebNotification('Focus Session Paused', 'The session has been paused.', 'focusflow-session', 'https://cdn-icons-png.flaticon.com/512/3569/3569434.png');

                                    // Cancel pending timer since it's paused
                                    if (swRegistration && swRegistration.active) {
                                        swRegistration.active.postMessage({
                                            action: 'cancel-notification',
                                            id: 'session-end'
                                        });
                                    }
                                } else if (data.type === 'resume') {
                                    statusDiv.innerHTML = "🔥 <strong>Focus Session Active (Resumed)</strong><br><span style='font-size: 16px; color: #34d399;'>Goal: " + (data.goal || 'Deep Focus') + "</span>";
                                    statusDiv.className = "status active";
                                    
                                    showWebNotification('Focus Session Resumed!', 'Let\'s get back to focusing!', 'focusflow-session', 'https://cdn-icons-png.flaticon.com/512/3569/3569434.png');

                                    // Schedule end alert with the remaining time via Service Worker
                                    if (data.durationSeconds && data.durationSeconds > 0 && swRegistration && swRegistration.active) {
                                        swRegistration.active.postMessage({
                                            action: 'schedule-notification',
                                            id: 'session-end',
                                            title: 'Focus Session Completed! 🎉',
                                            body: 'Fantastic work! You successfully completed your session.',
                                            delayMs: data.durationSeconds * 1000,
                                            icon: 'https://cdn-icons-png.flaticon.com/512/1164/1164620.png',
                                            tag: 'focusflow-session'
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

    private fun getServiceWorkerContent(): String {
        return """
            // Service Worker to handle notifications in the background
            self.addEventListener('install', (event) => {
                self.skipWaiting();
                console.log('Service Worker installed.');
            });

            self.addEventListener('activate', (event) => {
                event.waitUntil(self.clients.claim());
                console.log('Service Worker activated.');
            });

            // Store scheduled timers in memory
            const scheduledTimers = new Map();

            self.addEventListener('message', (event) => {
                const data = event.data;
                if (!data) return;

                console.log('SW received message:', data);

                if (data.action === 'show-notification') {
                    // Immediate notification
                    showNotificationSafely(data.title, {
                        body: data.body,
                        icon: data.icon || 'https://cdn-icons-png.flaticon.com/512/3569/3569434.png',
                        tag: data.tag || 'focusflow-session',
                        requireInteraction: true
                    });
                } else if (data.action === 'schedule-notification') {
                    // Schedule a notification for later
                    const id = data.id;
                    const title = data.title;
                    const body = data.body;
                    const delayMs = data.delayMs;
                    const icon = data.icon;
                    const tag = data.tag;
                    
                    // Cancel existing timer with same id if any
                    if (scheduledTimers.has(id)) {
                        clearTimeout(scheduledTimers.get(id));
                        scheduledTimers.delete(id);
                    }

                    if (delayMs > 0) {
                        const timerId = setTimeout(() => {
                            showNotificationSafely(title, {
                                body: body,
                                icon: icon || 'https://cdn-icons-png.flaticon.com/512/3569/3569434.png',
                                tag: tag || 'focusflow-session',
                                requireInteraction: true
                            });
                            scheduledTimers.delete(id);
                        }, delayMs);
                        
                        scheduledTimers.set(id, timerId);
                        console.log('Scheduled notification "' + title + '" in ' + delayMs + 'ms with ID: ' + id);
                    }
                } else if (data.action === 'cancel-notification') {
                    const id = data.id;
                    if (scheduledTimers.has(id)) {
                        clearTimeout(scheduledTimers.get(id));
                        scheduledTimers.delete(id);
                        console.log('Cancelled scheduled notification with ID: ' + id);
                    }
                } else if (data.action === 'close-all-notifications') {
                    self.registration.getNotifications().then(notifications => {
                        notifications.forEach(notification => {
                            notification.close();
                        });
                    });
                }
            });

            function showNotificationSafely(title, options) {
                self.registration.showNotification(title, options)
                    .then(() => console.log('Notification displayed: ' + title))
                    .catch(err => console.error('Error showing notification in SW:', err));
            }

            // Handle notification click to focus/open the companion app
            self.addEventListener('notificationclick', (event) => {
                event.notification.close();
                event.waitUntil(
                    self.clients.matchAll({ type: 'window', includeUncontrolled: true }).then((clientList) => {
                        if (clientList.length > 0) {
                            return clientList[0].focus();
                        }
                        return self.clients.openWindow('/');
                    })
                );
            });
        """.trimIndent()
    }
}

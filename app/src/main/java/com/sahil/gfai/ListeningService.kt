package com.sahil.gfai

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat

class ListeningService : Service() {
    override fun onBind(i: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("listen", "Listening", NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(this, "listen")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("${Prefs.name(this)} sun rahi hai ❤")
            .setContentText("Sahil GF AI background listening on")
            .setContentIntent(open).setOngoing(true).build()
        if (android.os.Build.VERSION.SDK_INT >= 29)
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE) else startForeground(1, n)
        VoiceSession.start(this)
        return START_STICKY
    }

    override fun onDestroy() { VoiceSession.stop(); super.onDestroy() }
}

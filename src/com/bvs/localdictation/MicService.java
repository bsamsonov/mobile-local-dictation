package com.bvs.localdictation;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.IBinder;
import android.util.Log;

/**
 * Foreground service of type "microphone" that runs while dictation is on, so the microphone keeps
 * working after the screen locks. It does no work itself: the keyboard records and recognizes in the
 * same process. The notification has a Stop action.
 */
public class MicService extends Service {

    private static final String TAG = "LocalDictation";
    private static final String CHANNEL = "dictation";
    private static final String ACTION_STOP = "com.bvs.localdictation.STOP";
    private static final int ID = 1;

    /** Called on the main thread when the user taps Stop in the notification. */
    interface StopListener { void onStopFromNotification(); }

    private static StopListener stopListener;

    /** Starts the service; must be called while the keyboard is visible (foreground-service start rules). */
    static void start(Context c, StopListener l) {
        stopListener = l;
        try {
            c.startForegroundService(new Intent(c, MicService.class));
        } catch (RuntimeException e) {
            Log.w(TAG, "mic service not started: " + e);
        }
    }

    static void stop(Context c) {
        c.stopService(new Intent(c, MicService.class));
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            Log.d(TAG, "stop from notification");
            if (stopListener != null) stopListener.onStopFromNotification();
            stopSelf();
            return START_NOT_STICKY;
        }
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel(CHANNEL,
                getString(R.string.notif_channel), NotificationManager.IMPORTANCE_LOW));
        PendingIntent stop = PendingIntent.getService(this, 0,
                new Intent(this, MicService.class).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE);
        Notification n = new Notification.Builder(this, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setContentTitle(getString(R.string.notif_title))
                .setContentText(getString(R.string.notif_text))
                .setOngoing(true)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .addAction(new Notification.Action.Builder(null, getString(R.string.notif_stop), stop).build())
                .build();
        try {
            startForeground(ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
        } catch (RuntimeException e) {
            Log.w(TAG, "startForeground failed: " + e);
            stopSelf();
        }
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        stopForeground(STOP_FOREGROUND_REMOVE);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}

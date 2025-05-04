package orion.app.timer;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import java.util.Locale;
import java.util.Timer;

public class TimerService extends Service {
    private static final String TAG = "TimerService";

    // ACTION STATE
    public static final String ACTION_START = "ACTION_START";
    public static final String ACTION_PAUSE = "ACTION_PAUSE";
    public static final String ACTION_RESUME = "ACTION_RESUME";
    public static final String ACTION_STOP = "ACTION_STOP";
    public static final String TIMER_TIME = "TIMER_TIME";

    public static final String ACTION_MUTE = "ACTION_MUTE";
    public static final String ACTION_UNMUTE = "ACTION_UNMUTE";

    public static final String BROADCAST_ACTION_TIMER_UPDATE = "TIMER_UPDATE";
    public static final String BROADCAST_ACTION_TIMER_STOP = "TIMER_STOP";
    public static final String BROADCAST_EXTRA_REMAINING = "REMAINING";

    // Notification
    private static final String CHANNEL_ID = "timer_channel";
    private static final String FINISHED_CHANNEL_ID = "timer_finished_channel";
    private static final int NOTIFICATION_ID = 1;
    private static final int FINISHED_NOTIFICATION_ID = 2;

    // Time
    private Handler handler;
    private Runnable timerRunnable;

    private long startTimeMillis = 0L;
    private long durationMillis = 0L;
    private long remainingMillis = 0L;

    private boolean isTimerRunning = false;
    private boolean isPaused = false;
    private boolean isMuted = false;


    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "onCreate");
        handler = new Handler(Looper.getMainLooper());
        createNotificationChannel();
        initTimerState();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || intent.getAction() == null) {
            Log.w(TAG, "onStartCommand: Intent or Action is null");
            stopSelf();
            return START_NOT_STICKY;
        }

        String action = intent.getAction();
        Log.d(TAG, "Action Received : " + action);


        switch (action) {
            case ACTION_START:
                long duration = intent.getLongExtra(TIMER_TIME, 0L);
                if (duration > 0 && !isTimerRunning) {
                    showTempStopNotification(this, "Timer is running");
                    startTimer(duration);
                } else if (isTimerRunning) {
                    Log.w(TAG, "Timer is already running");
                    showTempStopNotification(this, "Timer is already running");
                } else {
                    Log.w(TAG, "Invalid duration: " + duration);
                    stopSelf();
                }
                break;
            case ACTION_PAUSE:
                pauseTimer();
                break;
            case ACTION_RESUME:
                resumeTimer();
                break;
            case ACTION_STOP:
                stopTimer();
                break;
            case ACTION_MUTE:
                isMuted = true;
                break;
            case ACTION_UNMUTE:
                isMuted = false;
                break;
        }

        return START_NOT_STICKY;
    }

    private void initTimerState() {
        timerRunnable = null;

        isPaused = false;
        isTimerRunning = false;

        remainingMillis = 0;
        startTimeMillis = 0;
        durationMillis = 0;
    }

    private void startTimer(long duration) {
        Log.d(TAG, "Starting timer for " + duration + " seconds");
        if (handler != null && timerRunnable != null)
            handler.removeCallbacks(timerRunnable);

        isTimerRunning = true;
        isPaused = false;

        durationMillis = duration * 1000L;
        startTimeMillis = SystemClock.elapsedRealtime();

        startForeground(NOTIFICATION_ID, createNotification(formatMillis(durationMillis)));
        startPeriodicUpdates();
        sendTimerUpdateBroadcast(durationMillis);
    }

    private void pauseTimer() {
        if (isTimerRunning && !isPaused) {
            Log.d(TAG, "Pausing Timer");
            isPaused = true;

            if (handler != null && timerRunnable != null)
                handler.removeCallbacks(timerRunnable);

            remainingMillis = durationMillis - (SystemClock.elapsedRealtime() - startTimeMillis);
            if (remainingMillis < 0) remainingMillis = 0;
            updateNotification(formatMillis(remainingMillis));
            showTempStopNotification(this, "Timer is paused");
            sendTimerUpdateBroadcast(remainingMillis);
            Log.d(TAG, "Paused. Remaining millis: " + remainingMillis);
        } else {
            Log.d(TAG, "Timer not running or already paused");
        }
    }

    private void resumeTimer() {
        if (isTimerRunning && isPaused) {
            if (remainingMillis <= 0) {
                Log.w(TAG, "Cannot resume, remaining time is zero or negative");
                stopTimer();
                return;
            }

            Log.d(TAG, "Resuming timer with " + remainingMillis + " ms remaining");
            isPaused = false;
            startPeriodicUpdates();
            updateNotification(formatMillis(remainingMillis));
            sendTimerUpdateBroadcast(remainingMillis);
        } else
            Log.w(TAG, "Timer not running or not paused");
    }

    private void stopTimer() {
        Log.d(TAG, "Stopping timer and service");
        if (isTimerRunning) {
            showTempStopNotification(this, "Timer is stopped");
            Intent intent = new Intent(BROADCAST_ACTION_TIMER_STOP);
            LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
        } else showTempStopNotification(this, "Timer is already stopped");

        initTimerState();

        stopForeground(true);
        stopSelf();
    }

    private void startPeriodicUpdates() {
        if (handler != null && timerRunnable != null)
            handler.removeCallbacks(timerRunnable);

        timerRunnable = new Runnable() {
            @Override
            public void run() {
                if (!isTimerRunning || isPaused) return;

                long currentTimeMillis = SystemClock.elapsedRealtime();
                long elapsedTimeMillis = currentTimeMillis - startTimeMillis;
                remainingMillis = durationMillis - elapsedTimeMillis;

                if (remainingMillis <= 0) {
                    Log.d(TAG, "Handler check: Time is up or passed");
                    stopTimer();
                } else {
                    updateNotification(formatMillis(remainingMillis));
                    sendTimerUpdateBroadcast(remainingMillis);
                    handler.postDelayed(this, 1000);
                }
            }
        };
        handler.post(timerRunnable);
    }

    private void sendTimerUpdateBroadcast(long remainingMillis) {
        Intent intent = new Intent(BROADCAST_ACTION_TIMER_UPDATE);
        if (remainingMillis < 0) remainingMillis = 0;
        intent.putExtra(BROADCAST_EXTRA_REMAINING, remainingMillis);

        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
        Log.d(TAG, "Send Broadcast");
    }

    private Notification createNotification(String contentText) {

        Intent intent = new Intent(this, MainActivity.class);
        int intentFlags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, intent,
                intentFlags
        );

        Intent pauseResumeIntent = new Intent(this, TimerService.class);
        String pauseResumeAction = isPaused ? ACTION_RESUME : ACTION_PAUSE;
        String pauseResumeTitle = isPaused ? "Resume" : "Pause";
        int pauseResumeIcon = isPaused ? R.drawable.outline_pause_black_24 : R.drawable.outline_play_arrow_black_24;
        pauseResumeIntent.setAction(pauseResumeAction);
        PendingIntent pauseResumePendingIntent = PendingIntent.getService(this, 1, pauseResumeIntent, intentFlags);

        Intent stopIntent = new Intent(this, TimerService.class);
        stopIntent.setAction(ACTION_STOP);
        PendingIntent stopPendingIntent = PendingIntent.getService(this, 2, stopIntent, intentFlags);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(isPaused ? "Timer Paused" : "Timer Running")
                .setContentText(contentText)
                .setSmallIcon(R.drawable.outline_timer_black_24)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setOnlyAlertOnce(true);

        builder.addAction(pauseResumeIcon, pauseResumeTitle, pauseResumePendingIntent);
        builder.addAction(R.drawable.outline_stop_black_24, "Stop", stopPendingIntent);

        return builder.build();
    }


    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            String name = "Timer Channel";
            String description = "Timer Alarm Channel";
            int importance = NotificationManager.IMPORTANCE_LOW;

            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
            channel.setDescription(description);

            NotificationManager notificationManager = ContextCompat.getSystemService(getApplicationContext(), NotificationManager.class);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
            }
            createFinishedNotificationChannel(this);
        }
    }

    private void createFinishedNotificationChannel(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = "Timer Finished/Stopped Alerts";
            String description = "TImer finished or Stopped Alerts";
            int importance = NotificationManager.IMPORTANCE_HIGH;

            NotificationChannel channel = new NotificationChannel(FINISHED_CHANNEL_ID, name, importance);
            channel.setDescription(description);
            channel.setSound(Settings.System.DEFAULT_NOTIFICATION_URI,
                    new AudioAttributes.Builder()
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                            .build());
            channel.enableVibration(true);
            channel.setVibrationPattern(new long[]{0, 250, 100, 250});

            NotificationManager notificationManager = ContextCompat.getSystemService(context, NotificationManager.class);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
            }
        }
    }

    private void showTempStopNotification(Context context, String message) {
        createFinishedNotificationChannel(context);

        Intent intent = new Intent(context, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent pendingIntent = PendingIntent.getActivity(context, 3, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, FINISHED_CHANNEL_ID)
                .setSmallIcon(R.drawable.outline_timer_black_24)
                .setContentTitle("Timer")
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent);

        if (isMuted) {
            Log.d("TimerService", "Notification Sound & Vibrate Off");
            builder
                    .setSound(null)
                    .setVibrate(null);
        } else {
            Log.d("TimerService", "Notification Sound & Vibrate On");
            builder
                    .setSound(Settings.System.DEFAULT_NOTIFICATION_URI)
                    .setVibrate(new long[]{0, 250, 100, 250});
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder.setTimeoutAfter(2000);
        }

        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);
        notificationManager.notify(FINISHED_NOTIFICATION_ID, builder.build());
    }

    private void updateNotification(String newText) {
        Notification notification = createNotification(newText);
        NotificationManagerCompat.from(this).notify(1, notification);
    }

    private static String formatMillis(long millis) {
        long seconds = millis / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;

        seconds %= 60;
        minutes %= 60;

        if (hours > 0)
            return String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds);
        else return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (handler != null && timerRunnable != null)
            handler.removeCallbacks(timerRunnable);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
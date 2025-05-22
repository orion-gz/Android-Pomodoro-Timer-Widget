package orion.app.timer;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioAttributes;
import android.os.Build;
import android.os.Bundle;
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

public class TimerService extends Service {
    // Tag for Logging
    private static final String TAG = "TimerService";

    // ACTION STATE
    public static final String ACTION_START = "ACTION_START";
    public static final String ACTION_PAUSE = "ACTION_PAUSE";
    public static final String ACTION_RESUME = "ACTION_RESUME";
    public static final String ACTION_ADJUST_TIME = "ACTION_ADJUST_TIME";
    public static final String ACTION_STOP = "ACTION_STOP";
    public static final String ACTION_MUTE = "ACTION_MUTE";
    public static final String ACTION_UNMUTE = "ACTION_UNMUTE";

    // Broadcast Actions
    public static final String BROADCAST_ACTION_TIMER_STATE_CHANGED = "TIMER_STATE_CHANGED";
    public static final String BROADCAST_ACTION_TIMER_UPDATE = "TIMER_UPDATE";
    public static final String BROADCAST_ACTION_TIMER_STOP = "TIMER_STOP";

    // Shared Preference Keys
    public static final String PREFS_NAME = "TimerServiceState";
    public static final String KEY_SELECTED_SUBJECT_ID = "selectedSubjectId";
    public static final String KEY_DURATION_TIME = "durationTime";
    public static final String KEY_REMAINING_TIME = "remainingTime";
    public static final String KEY_START_TIME = "startTime";
    public static final String KEY_IS_RUNNING = "isRunning";
    public static final String KEY_IS_PAUSED = "isPaused";
    public static final String KEY_IS_MUTED = "isMuted";

    // Intent Extra Keys
    public static final String BROADCAST_EXTRA_REMAINING_TIME = "REMAINING_TIME";
    public static final String EXTRA_ADJUSTMENT_TIME = "EXTRA_ADJUSTMENT_TIME";
    public static final String EXTRA_SHOW_TIMER_FRAGMENT = "EXTRA_SHOW_TIMER_FRAGMENT";

    // Bundle Keys
    public static final String BUNDLE_TIMER_TIME = "BUNLDE_TIMER_TIME";
    public static final String BUNDLE_SUBJECT_NAME = "SUBJECT_NAME";
    public static final String BUNDLE_SUBJECT_ID = "SUBJECT_ID";
    public static final String BUNDLE_START_TIME = "START_TIME";

    // Notification Channels and IDs
    private static final String CHANNEL_ID = "timer_channel";
    private static final String TEMP_CHANNEL_ID = "timer_temp_channel";
    private static final String TEMP_MUTE_CHANNEL_ID = "timer_temp_muted_channel";
    private static final int NOTIFICATION_ID = 1;
    private static final int TEMP_NOTIFICATION_ID = 2;

    // Time Variables
    private Handler handler;
    private Runnable timerRunnable;

    private long startTimeMillis = 0L;
    private long durationMillis = 0L;
    private long remainingMillis = 0L;

    // State Variables
    private boolean isTimerRunning = false;
    private boolean isPaused = false;
    private boolean isMuted = false;
    private String startTime;

    // Save Timer State to SharedPreference
    // Using when restore timer state
    private void saveStateToPrefs() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();

        editor.putLong(KEY_DURATION_TIME, durationMillis / 1000L);
        editor.putString(KEY_START_TIME, startTime);
        editor.putBoolean(KEY_IS_RUNNING, isTimerRunning);
        editor.putBoolean(KEY_IS_PAUSED, isPaused);
        editor.putBoolean(KEY_IS_MUTED, isMuted);

        editor.apply();
    }

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
                Bundle bundle = intent.getExtras();
                long duration = bundle.getLong(BUNDLE_TIMER_TIME);
                if (duration > 0 && !isTimerRunning) {
                    startTime = intent.getStringExtra(BUNDLE_START_TIME);

                    showTempNotification(this, "Session Start");
                    startTimer(duration);
                    saveStateToPrefs();
                } else if (isTimerRunning) {
                    Log.w(TAG, "Timer is already running");
                    // showTempNotification(this, "Timer is already running");
                } else {
                    Log.w(TAG, "Invalid duration: " + duration);
                    stopSelf();
                }
                break;
            case ACTION_ADJUST_TIME:
                long adjustDuration = intent.getLongExtra(EXTRA_ADJUSTMENT_TIME, 0L);
                if (isTimerRunning && adjustDuration != 0) {
                    adjustTimerTime(adjustDuration);
                    saveStateToPrefs();
                } else
                    Log.w(TAG, "Cannot Adjust Time");
                break;
            case ACTION_PAUSE:
                pauseTimer();
                saveStateToPrefs();
                break;
            case ACTION_RESUME:
                resumeTimer();
                saveStateToPrefs();
                break;
            case ACTION_STOP:
                stopTimer();
                saveStateToPrefs();
                break;
            case ACTION_MUTE:
                isMuted = true;
                saveStateToPrefs();
                break;
            case ACTION_UNMUTE:
                isMuted = false;
                saveStateToPrefs();
                break;
        }

        return START_REDELIVER_INTENT;
    }

    // Initialize Timer State
    private void initTimerState() {
        timerRunnable = null;

        isPaused = false;
        isTimerRunning = false;

        remainingMillis = 0;
        startTimeMillis = 0;
        durationMillis = 0;
    }

    // Start Timer
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

    // Adjust Timer Time
    private void adjustTimerTime(long adjustDuration) {
        Log.d(TAG, "Adjusting timer time by " + adjustDuration + " seconds");
        long adjustMillis = adjustDuration * 1000;
        durationMillis += adjustMillis;
        remainingMillis += adjustMillis;
        sendTimerUpdateBroadcast(remainingMillis);
    }

    // Pause Timer
    private void pauseTimer() {
        if (isTimerRunning && !isPaused) {
            Log.d(TAG, "Pausing Timer");
            isPaused = true;

            if (handler != null && timerRunnable != null)
                handler.removeCallbacks(timerRunnable);

            remainingMillis = durationMillis - (SystemClock.elapsedRealtime() - startTimeMillis);
            if (remainingMillis < 0) remainingMillis = 0;
            updateNotification(formatMillis(remainingMillis));
            showTempNotification(this, "The session has been paused");
            sendTimerUpdateBroadcast(remainingMillis);
            Log.d(TAG, "Paused. Remaining millis: " + remainingMillis);
        } else {
            Log.d(TAG, "Timer not running or already paused");
        }
    }

    // Resume Timer
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

    // Stop Timer
    private void stopTimer() {
        Log.d(TAG, "Stopping timer and service");
        if (isTimerRunning) {
            showTempNotification(this, "Session has ended");
            Intent intent = new Intent(BROADCAST_ACTION_TIMER_STOP);
            LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
        }

        initTimerState();
        stopForeground(true);
        stopSelf();
    }

    // Start Periodic Updates
    // Using Runnable Object to implements timer
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

    // Send Remaining time to Broadcast Receiver
    private void sendTimerUpdateBroadcast(long remainingMillis) {
        Intent intent = new Intent(BROADCAST_ACTION_TIMER_UPDATE);
        if (remainingMillis < 0) remainingMillis = 0;
        intent.putExtra(BROADCAST_EXTRA_REMAINING_TIME, remainingMillis);

        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
        Log.d(TAG, "Send Broadcast");
    }

    // Create Notification Channel
    private void createNotificationChannel() {
        String name = "Timer Channel";
        String description = "Timer Service Channel";
        int importance = NotificationManager.IMPORTANCE_LOW;

        NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
        channel.setDescription(description);

        NotificationManager notificationManager = ContextCompat.getSystemService(getApplicationContext(), NotificationManager.class);
        if (notificationManager != null) {
            notificationManager.createNotificationChannel(channel);
        }
        createTempNotificationChannel(this);
    }

    // Create Temporary Notification Channel
    // Normal Channel & Muted Channel
    private void createTempNotificationChannel(Context context) {
        NotificationManager notificationManager = ContextCompat.getSystemService(context, NotificationManager.class);

        // Normal Channel
        String nameNormal = "Timer State Change Alerts";
        String descriptionNormal = "Timer State Change Channel";
        int importance = NotificationManager.IMPORTANCE_HIGH;

        NotificationChannel channelNormal = new NotificationChannel(TEMP_CHANNEL_ID, nameNormal, importance);
        AudioAttributes audioAttributes = new AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                .build();
        channelNormal.setSound(Settings.System.DEFAULT_NOTIFICATION_URI, audioAttributes);
        channelNormal.enableVibration(true);
        channelNormal.setVibrationPattern(new long[]{0, 250, 100, 250});
        channelNormal.setDescription(descriptionNormal);

        if (notificationManager != null) {
            notificationManager.createNotificationChannel(channelNormal);
        }

        // Muted Channel
        String nameMuted = "Timer State Change Alerts (Muted)";
        String descriptionMuted = "Timer State Change Channel (Muted)";

        NotificationChannel channelMuted = new NotificationChannel(TEMP_MUTE_CHANNEL_ID, nameMuted, importance);
        channelMuted.setSound(null, null);
        channelMuted.enableVibration(false);
        channelMuted.setDescription(descriptionMuted);

        if (notificationManager != null) {
            notificationManager.createNotificationChannel(channelMuted);
        }
    }

    // Create Notification for foreground service
    private Notification createNotification(String timeText) {
        Intent intent = new Intent(this, MainActivity.class);
        int intentFlags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, intentFlags) ;
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Timer Session")
                .setContentText(timeText)
                .setSmallIcon(R.drawable.outline_hourglass_top_black_24)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(pendingIntent);

        return builder.build();
    }

    // Show Temporary Notification
    private void showTempNotification(Context context, String message) {
        createTempNotificationChannel(context);

        Intent intent = new Intent(context, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent pendingIntent = PendingIntent.getActivity(context, 3, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        String channelId = TEMP_CHANNEL_ID;
        if (isMuted) channelId = TEMP_MUTE_CHANNEL_ID;
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, channelId)
                .setSmallIcon(R.drawable.outline_hourglass_top_black_24)
                .setContentTitle("Timer Session")
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent);

        builder.setTimeoutAfter(2500);
        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);
        notificationManager.notify(TEMP_NOTIFICATION_ID, builder.build());
    }

    // Update Notification
    private void updateNotification(String timeText) {
        Notification notification = createNotification(timeText);
        NotificationManagerCompat.from(this).notify(1, notification);
    }

    // Format Remaining Time
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
        saveStateToPrefs();
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
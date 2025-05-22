package orion.app.timer;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.ColorUtils;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import android.content.IntentFilter;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Locale;

import orion.gz.pomodorotimer.OnTimerChangeListener;
import orion.gz.pomodorotimer.TimerView;

public class MainActivity extends AppCompatActivity {
    // Session State Constants
    private static final int SESSION_START = 0;
    private static final int SESSION_END = 1;
    private static final int SESSION_PAUSE = 2;
    private static final int SESSION_RESUME = 3;
    private static final int MUTE = 4;
    private static final int UNMUTE = 5;

    // Default Timer Duration
    private static final long DEFAULT_MINUTES = 25;

    // UI Components
    private TimerView timerView;
    private TextView timeTextview;
    private Button sessionStartBtn;
    private LinearLayout timeControlLayout;
    private LinearLayout sessionControlLayout;
    private FloatingActionButton subtractMinuteFab;
    private FloatingActionButton addMinuteFab;
    private FloatingActionButton sessionControlFab;
    private FloatingActionButton sessionEndFab;
    private FloatingActionButton muteFab;

    // State Variables
    private boolean isTimerPause = false;
    private boolean isMuted = false;
    private int sessionState = -1;
    private long sessionDuration;
    private long currentMinutes = 25;
    private long currentSeconds = 0;
    private LocalTime startTime;
    private LocalTime endTime;

    // Broadcast Receiver for Timer Update
    private final BroadcastReceiver timerUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d("TimerService", "Receive Broadcast :" + intent.getAction());
            final String action = TimerService.BROADCAST_ACTION_TIMER_UPDATE;

            if (intent != null && action.equals(intent.getAction())) {
                // Restore Timer State
                if (sessionState == -1)
                    restoreTimerState();
                else {
                    long remainingTime = intent.getLongExtra(TimerService.BROADCAST_EXTRA_REMAINING_TIME, 0L) / 1000;
                    setTimerTime(remainingTime);
                }
            }
        }
    };

    // Broadcast Receiver for Timer Stop
    private final BroadcastReceiver timerStopReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            timerView.setTime(DEFAULT_MINUTES, 0);
            Log.d("TimerService", "Receive Broadcast :" + intent.getAction());
            final String action = TimerService.BROADCAST_ACTION_TIMER_STOP;

            if (intent != null && action.equals(intent.getAction())) {
                endTime = LocalTime.now();
                resetTimer();
            }
        }
    };

    // Restore Main Layout
    private void restoreTimerState() {
        SharedPreferences prefs = getApplicationContext().getSharedPreferences(TimerService.PREFS_NAME, Context.MODE_PRIVATE);
        long remainingTime = prefs.getLong(TimerService.KEY_REMAINING_TIME, 0);
        isMuted = prefs.getBoolean(TimerService.KEY_IS_MUTED, false);
        isTimerPause = prefs.getBoolean(TimerService.KEY_IS_PAUSED, false);
        sessionDuration = prefs.getLong(TimerService.KEY_DURATION_TIME, 0);
        startTime = prefs.getString(TimerService.KEY_START_TIME, null) == null ? null : LocalTime.parse(prefs.getString(TimerService.KEY_START_TIME, null));

        setTimerTime(remainingTime);
        sessionState = SESSION_START;
        viewControl(sessionState);

        if (isMuted) viewControl(MUTE);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        initViews();
        setupTimer();
        setupTimeControl();
        setupTimerControl();
        updateTimerView();
    }

    @Override
    public void onResume() {
        super.onResume();
        Context context = getApplicationContext();
        String update = TimerService.BROADCAST_ACTION_TIMER_UPDATE;
        String stop = TimerService.BROADCAST_ACTION_TIMER_STOP;
        String state = TimerService.BROADCAST_ACTION_TIMER_STATE_CHANGED;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Register Broadcast Receiver
            LocalBroadcastManager.getInstance(context).registerReceiver(timerUpdateReceiver, new IntentFilter(update));
            LocalBroadcastManager.getInstance(context).registerReceiver(timerStopReceiver, new IntentFilter(stop));
            Log.d("TimerService", "registerReceiver");
        } else {
            Log.w("TimerService", "registerReceiver Falied");
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        Context context = getApplicationContext();

        // Unregister Broadcast Receiver
        LocalBroadcastManager.getInstance(context).unregisterReceiver(timerUpdateReceiver);
        LocalBroadcastManager.getInstance(context).unregisterReceiver(timerStopReceiver);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Context context = getApplicationContext();

        // Save Current State When View is destroy
        SharedPreferences prefs = context.getSharedPreferences(TimerService.PREFS_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putLong(TimerService.KEY_REMAINING_TIME, currentMinutes * 60 + currentSeconds);
        editor.apply();
    }

    // Initializae View Components
    private void initViews() {
        String formattedTimeText = String.format(Locale.getDefault(), "%02d:%02d", currentMinutes, currentSeconds);
        timerView = findViewById(R.id.timer_view);
        timeTextview = findViewById(R.id.time_textview);
        timeTextview.setText(formattedTimeText);

        timeControlLayout = findViewById(R.id.timer_time_control_layout);
        addMinuteFab = findViewById(R.id.add_minute_fab);
        subtractMinuteFab = findViewById(R.id.subtract_minute_fab);

        sessionControlLayout = findViewById(R.id.timer_session_control_layout);
        sessionStartBtn = findViewById(R.id.session_start_btn);
        sessionControlFab = findViewById(R.id.session_control_fab);
        sessionEndFab = findViewById(R.id.session_end_fab);
        muteFab = findViewById(R.id.mute_fab);
    }

    // View control based on timer status
    private void viewControl(int state) {
        switch (state) {
            case SESSION_START:
                sessionState = SESSION_START;
                sessionStartBtn.setVisibility(View.GONE);
                sessionControlLayout.setVisibility(View.VISIBLE);
                addMinuteFab.setVisibility(View.VISIBLE);
                subtractMinuteFab.setVisibility(View.VISIBLE);
                timerView.setTouchable(false);
                break;
            case SESSION_END:
                sessionState = SESSION_END;
                sessionControlFab.setImageResource(R.drawable.outline_pause_black_24);
                muteFab.setImageResource(R.drawable.outline_volume_up_black_24);
                sessionControlLayout.setVisibility(View.GONE);
                addMinuteFab.setVisibility(View.GONE);
                subtractMinuteFab.setVisibility(View.GONE);
                sessionStartBtn.setVisibility(View.VISIBLE);
                break;
            case SESSION_PAUSE:
                sessionState = SESSION_PAUSE;
                timerView.setAlpha(0.5F);
                sessionControlFab.setImageResource(R.drawable.outline_play_arrow_black_24);
                break;
            case SESSION_RESUME:
                sessionState = SESSION_RESUME;
                timerView.setAlpha(1F);
                sessionControlFab.setImageResource(R.drawable.outline_pause_black_24);
                break;
            case MUTE:
                muteFab.setImageResource(R.drawable.outline_volume_off_black_24);
                break;
            case UNMUTE:
                muteFab.setImageResource(R.drawable.outline_volume_up_black_24);
                break;
        }
    }

    // Reset Timer to inital state
    private void resetTimer() {
        if (isTimerPause) isTimerPause = false;
        timerView.resetRotation();
        timerView.setTime(DEFAULT_MINUTES, 0);
        timerView.setTouchable(true);
        timerView.setAlpha(1F);

        viewControl(SESSION_END);
    }

    // Change Timer Component's Color
    // I STRONGLY recommend this color pattern
    private void setupTimer() {
        int color = getResources().getColor(R.color.c5);
        int brightColor = ColorUtils.blendARGB(color, Color.WHITE, 0.2F);
        int darkColor = ColorUtils.blendARGB(color, Color.BLACK, 0.2F);

        sessionStartBtn.setBackgroundColor(color);
        timerView.setCirlceColor(color);
        timerView.setHandColor(darkColor);
        timerView.setKnobColor(brightColor);
    }

    // Listener for Timer Control
    private void setupTimeControl() {
        Context context = getApplicationContext();
        addMinuteFab.setOnClickListener(v -> {
            Intent addTimeIntent = new Intent(context, TimerService.class);
            addTimeIntent.setAction(TimerService.ACTION_ADJUST_TIME);
            addTimeIntent.putExtra(TimerService.EXTRA_ADJUSTMENT_TIME, 60L);
            context.startService(addTimeIntent);
            sessionDuration += 1;
        });

        subtractMinuteFab.setOnClickListener(v -> {
            Intent subtractTimeIntent = new Intent(context, TimerService.class);
            subtractTimeIntent.setAction(TimerService.ACTION_ADJUST_TIME);
            subtractTimeIntent.putExtra(TimerService.EXTRA_ADJUSTMENT_TIME, -60L);
            context.startService(subtractTimeIntent);
            sessionDuration -= 1;
        });
    }

    // Listener for Session Control
    private void setupTimerControl() {
        Context context = getApplicationContext();
        sessionStartBtn.setOnClickListener(v -> {
            sessionDuration = currentMinutes;
            startTime = LocalTime.now();

            Bundle bundle = new Bundle();
            bundle.putLong(TimerService.BUNDLE_TIMER_TIME, sessionDuration * 60);
            bundle.putString(TimerService.BUNDLE_START_TIME, startTime.toString());

            Intent startTimerIntent = new Intent(context, TimerService.class);
            startTimerIntent.setAction(TimerService.ACTION_START);
            startTimerIntent.putExtras(bundle);
            context.startService(startTimerIntent);

            viewControl(SESSION_START);
        });
        sessionControlFab.setOnClickListener(v -> {
            if (isTimerPause) {
                Intent resumeTimerIntent = new Intent(context, TimerService.class);
                resumeTimerIntent.setAction(TimerService.ACTION_RESUME);
                context.startService(resumeTimerIntent);

                viewControl(SESSION_RESUME);
                isTimerPause = false;
            } else {
                Intent pauseTimerIntent = new Intent(context, TimerService.class);
                pauseTimerIntent.setAction(TimerService.ACTION_PAUSE);
                context.startService(pauseTimerIntent);

                viewControl(SESSION_PAUSE);
                isTimerPause = true;

            }
        });

        sessionEndFab.setOnClickListener(v -> {
            Intent stopTimerIntent = new Intent(context, TimerService.class);
            stopTimerIntent.setAction(TimerService.ACTION_STOP);
            context.startService(stopTimerIntent);
            endTime = LocalTime.now();
            resetTimer();
        });

        muteFab.setOnClickListener(v -> {
            if (!isMuted) {
                viewControl(MUTE);
                isMuted = true;
                Intent muteAlarmIntent = new Intent(context, TimerService.class);
                muteAlarmIntent.setAction(TimerService.ACTION_MUTE);
                context.startService(muteAlarmIntent);
            } else {
                viewControl(UNMUTE);
                isMuted = false;
                Intent unmuteAlarmIntent = new Intent(context, TimerService.class);
                unmuteAlarmIntent.setAction(TimerService.ACTION_UNMUTE);
                context.startService(unmuteAlarmIntent);
            }
        });
    }

    // Set Timer Time & Time TextView
    private void setTimerTime(long remainingTime) {
        long minutes = remainingTime / 60;
        long seconds = remainingTime % 60;
        currentMinutes = minutes;
        currentSeconds = seconds;

        timerView.setTime(currentMinutes, currentSeconds);
        updateTimeText();
    }

    // TimerView setOnTimerChangeListener
    // Listener that controls the minute hand of the TimerView
    private void updateTimerView() {
        timerView.setOnTimerChangeListener(new OnTimerChangeListener() {
            @Override
            public void onTimerChanged(long minutes, long seconds) {
                currentMinutes = minutes;
                currentSeconds = seconds;
                updateTimeText();
            }
        });
    }

    // Update Time Text
    private void updateTimeText() {
        String formattedTimeText = String.format(Locale.getDefault(), "%02d:%02d", currentMinutes, currentSeconds);
        timeTextview.setText(formattedTimeText);
    }
}
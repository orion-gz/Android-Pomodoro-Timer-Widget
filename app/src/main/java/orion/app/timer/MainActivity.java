package orion.app.timer;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import android.content.IntentFilter;

import java.util.Locale;

import orion.gz.pomodorotimer.OnTimerChangeListener;
import orion.gz.pomodorotimer.TimerView;

public class MainActivity extends AppCompatActivity {
    private TimerView timerView;
    private TextView timeTextView;
    private Button startBtn, pauseBtn, stopBtn;
    private ImageButton alarmBtn;
    private boolean isTimerPause = false;
    private boolean isMuted = false;

    private long defaultMinutes = 25;
    private long currentMinutes = 25;
    private long currentSeconds = 0;

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

        init();
        updateTimerView();
        setupTimerControl();
    }


    private final BroadcastReceiver timerUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d("TimerService", "Receive Broadcast :" + intent.getAction());
            final String action = TimerService.BROADCAST_ACTION_TIMER_UPDATE;

            if (intent != null && action.equals(intent.getAction())) {
                long remaining = intent.getLongExtra(TimerService.BROADCAST_EXTRA_REMAINING, 0L) / 1000;
                long minutes = remaining / 60;
                long seconds = remaining % 60;
                currentMinutes = (int) minutes;
                currentSeconds = (int) seconds;
                timeTextView.setText(String.format("%2d:%2d", currentMinutes, currentSeconds));
                timerView.setTime(currentMinutes, currentSeconds);
            }
        }
    };

    private final BroadcastReceiver timerStopReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            timerView.setTime(defaultMinutes, 0);
            Log.d("TimerService", "Receive Broadcast :" + intent.getAction());
            final String action = TimerService.BROADCAST_ACTION_TIMER_STOP;

            if (intent != null && action.equals(intent.getAction())) {
                timerView.resetRotation();
                timerView.setTime(defaultMinutes, 0);
                timerView.setTouchable(true);
            }
        }
    };

    @Override
    protected void onResume() {
        super.onResume();
        String update = TimerService.BROADCAST_ACTION_TIMER_UPDATE;
        String stop = TimerService.BROADCAST_ACTION_TIMER_STOP;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            LocalBroadcastManager.getInstance(this).registerReceiver(timerUpdateReceiver, new IntentFilter(update));
            LocalBroadcastManager.getInstance(this).registerReceiver(timerStopReceiver, new IntentFilter(stop));
            Log.d("TimerService", "registerReceiver");
        } else {
            Log.w("TimerService", "registerReceiver Falied");
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(timerUpdateReceiver);
        unregisterReceiver(timerStopReceiver);
    }

    private void init() {
        // TimerView
        timerView = findViewById(R.id.timer_view);
        // Minute View
        timeTextView = findViewById(R.id.time_textview);

        // Timer Btn
        startBtn = findViewById(R.id.start_timer);
        pauseBtn = findViewById(R.id.pause_timer);
        stopBtn = findViewById(R.id.stop_timer);

        alarmBtn = findViewById(R.id.alarm_btn);
    }

    private void setupTimerControl() {
        startBtn.setOnClickListener(v -> {
            // 타이머가 중지중일 때
            if (isTimerPause) {
                Intent resumeTimerIntent = new Intent(this, TimerService.class);
                resumeTimerIntent.setAction(TimerService.ACTION_RESUME);
                startService(resumeTimerIntent);

                startBtn.setText("Start");
                isTimerPause = false;
            } else {
                Intent startTimerIntent = new Intent(this, TimerService.class);
                startTimerIntent.setAction(TimerService.ACTION_START);
                startTimerIntent.putExtra(TimerService.TIMER_TIME, currentMinutes * 60);
                startService(startTimerIntent);
            }
            timerView.setTouchable(false);
        });

        pauseBtn.setOnClickListener(v -> {
            Intent pauseTimerIntent = new Intent(this, TimerService.class);
            pauseTimerIntent.setAction(TimerService.ACTION_PAUSE);
            startService(pauseTimerIntent);

            isTimerPause = true;
            startBtn.setText("Resume");
        });

        stopBtn.setOnClickListener(v -> {
            Intent stopTimerIntent = new Intent(this, TimerService.class);
            stopTimerIntent.setAction(TimerService.ACTION_STOP);
            startService(stopTimerIntent);

            if (isTimerPause) {
                startBtn.setText("Start");
                isTimerPause = false;
            }
            timerView.resetRotation();
            timerView.setTime(defaultMinutes, 0);
            timerView.setTouchable(true);
        });

        alarmBtn.setOnClickListener(v -> {
            if (!isMuted) {
                alarmBtn.setImageResource(R.drawable.outline_volume_off_black_24);
                isMuted = true;
                Intent muteAlarmIntent = new Intent(this, TimerService.class);
                muteAlarmIntent.setAction(TimerService.ACTION_MUTE);
                startService(muteAlarmIntent);
            } else {
                alarmBtn.setImageResource(R.drawable.outline_volume_up_black_24);
                isMuted = false;
                Intent unmuteAlarmIntent = new Intent(this, TimerService.class);
                unmuteAlarmIntent.setAction(TimerService.ACTION_UNMUTE);
                startService(unmuteAlarmIntent);
            }
        });
    }

    private void updateTimerView() {
        // timer change listener
        timerView.setOnTimerChangeListener(new OnTimerChangeListener() {
            @Override
            public void onTimerChanged(long minutes, long seconds) {
                currentMinutes = minutes;
                currentSeconds = seconds;
                Log.i("Time", "onTimeChanged:");
                updateTimeText();
            }
        });
    }

    private void updateTimeText() {
        String formatted = String.format(Locale.getDefault(), "%02d:%02d", currentMinutes, currentSeconds);
        timeTextView.setText(formatted);
    }
}
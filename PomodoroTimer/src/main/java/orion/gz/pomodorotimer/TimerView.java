package orion.gz.pomodorotimer;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Vibrator;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.Nullable;


public class TimerView extends View {

    // CONSTANTS
    private static final float CIRCLE_MARGIN = 150F;
    private static final float GRADUATION_MARGIN = 20F;
    private static final float KNOB_MARGIN = 425F;
    private static final float HAND_MARGIN = 450F;
    private static final float HAND_WIDTH = 22.5F;
    private static final float HAND_RADIUS = 8F;

    // Paint Objects
    private Paint redCircle, whiteCircle, knobCircle, knobStroke, hand, handCircle, tick;

    // RectF Objects
    private RectF circleBounds, graduationCircleBounds, knobCircleBounds, handBounds, handCircleBounds;

    // Color Attr
    private int circleColor;
    private int knobColor;
    private int handColor;

    // Center X, Y Coordinate
    private float centerX, centerY;
    // Angle of Arc - Default is 25 minutes (25 / 60 = 0.416777)
    private float remainingRatio = 0.416777F;
    // Last Touch Angle
    private float lastAngle = -1;
    // Sum of the number of rotations and the current angle
    private float totalRotation = 0;
    // Number of times the minute hand has rotated
    private int turns = 0;

    private long minutes = 25, seconds;
    // State
    private boolean isTouchable = true;

    // Util
    private Vibrator vibrator;
    // Listener
    private OnTimerChangeListener timeListener;

    /** Constructer **/
    public TimerView(Context context) {
        super(context);
        init(null);
    }

    public TimerView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(attrs);
    }

    public TimerView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(attrs);
    }

    public TimerView(Context context, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init(attrs);
    }
    /** Constructer **/

    /** Listener **/
    public void setOnTimerChangeListener(OnTimerChangeListener timeListener) {
        this.timeListener = timeListener;
    }
    /** Listener **/

    /** Setter for Attr **/
    public void setCirlceColor(int color) {
        circleColor = color;
        invalidate();
        requestLayout();
    }

    public void setKnobColor(int color) {
        knobColor = color;
        invalidate();
        requestLayout();
    }

    public void setHandColor(int color) {
        handColor = color;
        invalidate();
        requestLayout();
    }
    /** Setter for Attr **/

    // Initialize Objects (Attributes, Paint, RectF)
    private void init(AttributeSet attrs) {
        TypedArray attr = getContext().obtainStyledAttributes(attrs, R.styleable.TimerView);
        circleColor = attr.getColor(R.styleable.TimerView_circle_color, Color.parseColor("#EF5350"));
        knobColor = attr.getColor(R.styleable.TimerView_knob_color, Color.parseColor("#E57373"));
        handColor = attr.getColor(R.styleable.TimerView_hand_color, Color.parseColor("#F44336"));

        redCircle = new Paint();
        redCircle.setColor(circleColor);
        redCircle.setStyle(Paint.Style.FILL);
        redCircle.setAntiAlias(true);

        whiteCircle = new Paint();
        whiteCircle.setColor(Color.WHITE);
        whiteCircle.setStyle(Paint.Style.FILL);
        whiteCircle.setAntiAlias(true);

        knobCircle = new Paint();
        knobCircle.setColor(knobColor);
        knobCircle.setStyle(Paint.Style.FILL_AND_STROKE);
        knobCircle.setAntiAlias(true);

        knobStroke = new Paint();
        knobStroke.setColor(Color.parseColor("#DDDDDD"));
        knobStroke.setStyle(Paint.Style.STROKE);
        knobStroke.setStrokeWidth(3F);
        knobStroke.setAntiAlias(true);

        hand = new Paint();
        hand.setColor(handColor);
        hand.setStyle(Paint.Style.FILL);
        hand.setAntiAlias(true);

        handCircle = new Paint();
        handCircle.setColor(Color.parseColor("#DDDDDD"));
        handCircle.setStyle(Paint.Style.FILL);
        handCircle.setAntiAlias(true);

        tick = new Paint();
        tick.setColor(Color.GRAY);
        tick.setStrokeWidth(5F);
        tick.setAntiAlias(true);

        circleBounds = new RectF();
        graduationCircleBounds = new RectF();
        knobCircleBounds = new RectF();
        handCircleBounds = new RectF();
        handBounds = new RectF();
    }

    // Update Time by angle of arc
    // Function to get time according to angle
    public void updateTime() {
        int totalSeconds = (int) (remainingRatio * 60 * 60);
        minutes = totalSeconds / 60 + turns * 60;
        seconds = totalSeconds % 60;

        if (timeListener != null)
            timeListener.onTimerChanged(minutes, seconds);
    }

    // Set State
    public void setTouchable(boolean touchable) {
        isTouchable = touchable;
    }

    // Reset rotation variables
    public void resetRotation() {
        totalRotation = 0;
        lastAngle = -1;
    }

    // Set View by Time
    public void setTime(long minutes, long seconds) {
        this.minutes = minutes;
        this.seconds = seconds;
        float ratio = (minutes * 60 + seconds) / 3600F;
        setRemainingRatio(ratio);
    }

    // Set angle of Arc and Update time
    public void setRemainingRatio(float ratio) {
        this.remainingRatio = ratio;
        updateTime();
        invalidate();
    }

    // Touch Handle Function
    private void handleTouch(float x, float y) {
        float dx = x - centerX;
        float dy = y - centerY;

        // atan2 [-pi, pi]
        double angleRadian = Math.atan2(dy, dx);
        // change radian to degree and fitting with timerview
        int angleDegree = (int) Math.toDegrees(angleRadian) + 90;

        if (angleDegree < 0) angleDegree += 360;
        angleDegree -= (angleDegree % 6);

        if (lastAngle >= 0) {
            float delta = angleDegree - lastAngle;

            if (delta > 180) delta -= 360;
            else if (delta < -180) delta += 360;
            totalRotation += delta;

            if (totalRotation < 0) totalRotation = 0;
            setRemainingRatio(totalRotation / 360F);
            updateTime();
            lastAngle = angleDegree;
        } else {
            lastAngle = angleDegree;
            if (totalRotation == 0)
                totalRotation = lastAngle;
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        vibrator = (Vibrator) getContext().getSystemService(Context.VIBRATOR_SERVICE);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (isTouchable) {
            float x = event.getX();
            float y = event.getY();

            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                case MotionEvent.ACTION_MOVE:
                /*
                if (vibrator != null && vibrator.hasVibrator()) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                        vibrator.vibrate(VibrationEffect.createOneShot(10, VibrationEffect.DEFAULT_AMPLITUDE));
                    else
                        vibrator.vibrate(5);
                }*/
                    if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
                    handleTouch(x, y);
                    return true;
                case MotionEvent.ACTION_UP:
                    if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(false);
                    lastAngle = -1;
                    return true;
            }

            return super.onTouchEvent(event);
        }
        return super.onTouchEvent(event);
    }

    /*
    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        centerX = w / 2f;
        centerY = h / 2f;
    }


    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int desiredHeight = MeasureSpec.getSize(heightMeasureSpec);
        int desiredWidth = MeasureSpec.getSize(widthMeasureSpec);

        int width = resolveSize(desiredWidth, widthMeasureSpec);
        int height = resolveSize(desiredHeight, heightMeasureSpec);

        setMeasuredDimension(width, height);
    }
    */

    // Calculate Layout
    private void calculateLayout(float graduationRadius) {
        float radius = Math.min(centerX, centerY);
        float circleRadius = radius - CIRCLE_MARGIN;
        float knobRadius = radius - KNOB_MARGIN;
        float handRadius = radius - HAND_MARGIN;
        float handWidth = HAND_WIDTH;
        float handLength = graduationRadius;

        circleBounds.set(centerX - circleRadius, centerY - circleRadius, centerX + circleRadius, centerY + circleRadius);
        graduationCircleBounds.set(centerX - graduationRadius, centerY - graduationRadius, centerX + graduationRadius, centerY + graduationRadius);
        knobCircleBounds.set(centerX - knobRadius, centerY - knobRadius, centerX + knobRadius, centerY + knobRadius);
        handBounds.set(-(handWidth / 2), -handLength, (handWidth / 2), 0);
        handCircleBounds.set(-(handRadius / 2), handRadius / 2, handRadius / 2, -(handRadius / 2));
    }

    // Draw Circle and Arc
    private void drawCircle(Canvas canvas, float sweepAngle) {
        // Draw white circle
        canvas.drawArc(circleBounds, 0, 360, true, whiteCircle);
        // Draw red circle
        canvas.drawArc(circleBounds, -90, sweepAngle, true, redCircle);
        // Draw knob circle
        canvas.drawOval(knobCircleBounds, knobCircle);
        canvas.drawOval(knobCircleBounds, knobStroke);
    }

    // Draw Minute Hand
    private void drawHand(Canvas canvas, float sweepAngle) {
        canvas.save();
        canvas.translate(centerX, centerY);
        canvas.rotate(sweepAngle);
        canvas.drawRoundRect(handBounds, HAND_RADIUS, HAND_RADIUS, hand);
        canvas.drawOval(handCircleBounds, handCircle);
        canvas.restore();
    }

    // Draw hand graduation(marks)
    private void drawGraduation(Canvas canvas, float graduationRadius) {
        canvas.save();
        canvas.translate(centerX, centerY);

        for (int i = 0; i < 60; i++) {
            tick.setColor(Color.GRAY);
            float startY = -graduationRadius;
            float stopY = -graduationRadius - 20F;

            // Draw longer graduation(marks) every 5 minutes
            if (i % 5 == 0) {
                tick.setColor(Color.DKGRAY);
                startY = -graduationRadius - 20F;
                stopY = -graduationRadius + 20F;
            }

            canvas.drawLine(0, startY, 0, stopY, tick);
            canvas.rotate(6);
        }
        canvas.restore();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        centerX = getWidth() / 2F;
        centerY = getHeight() / 2F;
        float graduationRadius = Math.min(centerX, centerY) - GRADUATION_MARGIN;

        calculateLayout(graduationRadius);
        float sweepAngle = 360 * remainingRatio;

        drawCircle(canvas, sweepAngle);
        drawHand(canvas, sweepAngle);
        drawGraduation(canvas, graduationRadius);
    }
}

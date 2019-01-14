package com.vorsk.binaryanalog.watchface;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.wearable.complications.ComplicationData;
import android.support.wearable.complications.rendering.ComplicationDrawable;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.util.Log;
import android.util.SparseArray;
import android.view.Gravity;
import android.view.SurfaceHolder;

import com.vorsk.binaryanalog.MaterialColors;
import com.vorsk.binaryanalog.R;
import com.vorsk.binaryanalog.config.ConfigRecyclerViewAdapter;
import com.vorsk.binaryanalog.model.ConfigData;

import java.util.Calendar;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;


public class BinaryAnalogWatchFaceService extends CanvasWatchFaceService {
    private static final String TAG = "BinaryAnalogWatchFaceService";

    // Unique IDs for each complication. The settings activity that supports allowing users
    // to select their complication data provider requires numbers to be >= 0.

    private static final int CENTER_COMPLICATION_ID = 100;

    // Background, Left and right complication IDs as array for Complication API.
    private static final int[] COMPLICATION_IDS = {CENTER_COMPLICATION_ID};

    // Left and right dial supported types.
    private static final int[][] COMPLICATION_SUPPORTED_TYPES = {
            {
                    // center
                    ComplicationData.TYPE_RANGED_VALUE,
                    ComplicationData.TYPE_ICON,
                    ComplicationData.TYPE_SHORT_TEXT,
                    ComplicationData.TYPE_SMALL_IMAGE,
                    ComplicationData.TYPE_NO_PERMISSION
            }
    };

    /*
     * Update rate in milliseconds for interactive mode. We update once a second to advance the
     * second hand.
     */
    private static final long INTERACTIVE_UPDATE_RATE_MS = TimeUnit.SECONDS.toMillis(1);

    // Used by {@link ConfigRecyclerViewAdapter} to check if complication location
    // is supported in settings config_list activity.
    public static int getComplicationId(
            ConfigRecyclerViewAdapter.ComplicationLocation complicationLocation) {
        // Add any other supported locations here.
        switch (complicationLocation) {
            case CENTER:
                return CENTER_COMPLICATION_ID;
            default:
                return -1;
        }
    }

    // Used by {@link ConfigRecyclerViewAdapter} to retrieve all complication ids.
    public static int[] getComplicationIds() {
        return COMPLICATION_IDS;
    }

    // Used by {@link ConfigRecyclerViewAdapter} to see which complication types
    // are supported in the settings config_list activity.
    public static int[] getSupportedComplicationTypes(
            ConfigRecyclerViewAdapter.ComplicationLocation complicationLocation) {
        // Add any other supported locations here.
        switch (complicationLocation) {
            case CENTER:
                return COMPLICATION_SUPPORTED_TYPES[0];
            default:
                return new int[]{};
        }
    }

    @Override
    public Engine onCreateEngine() {
        return new Engine();
    }

    private class Engine extends CanvasWatchFaceService.Engine {
        private static final int MSG_UPDATE_TIME = 0;

        private static final float HAND_STROKE_WIDTH = 6f;
        private float CENTER_COMPLICATION_CIRCLE_RADIUS;// = 80f;
        private float CENTER_HOUR_CIRCLE_RADIUS;// = CENTER_COMPLICATION_CIRCLE_RADIUS * 2;

        private static final int SHADOW_RADIUS = 3;
        // Used to pull user's preferences for background color, highlight color, and visual
        // indicating there are unread notifications.
        SharedPreferences mSharedPref;
        private Calendar mCalendar;
        private final BroadcastReceiver mTimeZoneReceiver =
                new BroadcastReceiver() {
                    @Override
                    public void onReceive(Context context, Intent intent) {
                        mCalendar.setTimeZone(TimeZone.getDefault());
                        invalidate();
                    }
                };
        private boolean mRegisteredTimeZoneReceiver = false;
        private boolean mMuteMode;

        private float mCenterX;
        private float mCenterY;
        private float mBinarySegmentSize;

        private boolean mIsBackgroundDark;

        private int mWatchHandShadowColor;
        private int mBackgroundInnerColor;
        private int mBackgroundOuterColor;
        private int mWatchBinary0Color;
        private int mWatchBinary1Color;

        private Paint mBinaryHand1Paint;
        private Paint mBinaryHand0Paint;
        private Paint mBackgroundInnerPaint;
        private Paint mBackgroundOuterPaint;
        private Paint mWatchHandShadowLinePaint;
        private MaterialColors.Color mBackgroundMaterialColor;

        private boolean mLowBitAmbient;
        private boolean mBurnInProtection;

        /* Maps active complication ids to the data for that complication. Note: Data will only be
         * present if the user has chosen a provider via the settings activity for the watch face.
         */
        private SparseArray<ComplicationData> mActiveComplicationDataSparseArray;

        /* Maps complication ids to corresponding ComplicationDrawable that renders the
         * the complication data on the watch face.
         */
        private SparseArray<ComplicationDrawable> mComplicationDrawableSparseArray;
        private boolean mAmbient;
        // Handler to update the time once a second in interactive mode.
        private final Handler mUpdateTimeHandler =
                new Handler() {
                    @Override
                    public void handleMessage(Message message) {
                        invalidate();
                        if (shouldTimerBeRunning()) {
                            long timeMs = System.currentTimeMillis();
                            long delayMs =
                                    INTERACTIVE_UPDATE_RATE_MS
                                            - (timeMs % INTERACTIVE_UPDATE_RATE_MS);
                            mUpdateTimeHandler.sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayMs);
                        }
                    }
                };


        @Override
        public void onCreate(SurfaceHolder holder) {
            Log.d(TAG, "onCreate");

            super.onCreate(holder);

            // Used throughout watch face to pull user's preferences.
            Context context = getApplicationContext();
            mSharedPref =
                    context.getSharedPreferences(
                            getString(R.string.preference_file_key),
                            Context.MODE_PRIVATE);

            mCalendar = Calendar.getInstance();

            setWatchFaceStyle(
                    new WatchFaceStyle.Builder(BinaryAnalogWatchFaceService.this)
                            .setAcceptsTapEvents(true)
                            .setHideNotificationIndicator(false)
                            .setShowUnreadCountIndicator(true)
                            .setStatusBarGravity(Gravity.CENTER_HORIZONTAL)
                            .setViewProtectionMode(WatchFaceStyle.PROTECT_STATUS_BAR)
                            .build());

            loadSavedPreferences();
            initializeComplications();
            initializeWatchFace();
        }

        // Pulls all user's preferences for watch face appearance.
        private void loadSavedPreferences() {
            //mSharedPref.edit().clear().commit(); // used for testing, resets all settings to default

            String backgroundColorResourceName =
                    getApplicationContext().getString(R.string.saved_background_color);
            String backgroundColorName = mSharedPref.getString(backgroundColorResourceName, ConfigData.DEFAULT_BACKGROUND_COLOR);
            mBackgroundMaterialColor = MaterialColors.Get(backgroundColorName);

            mBackgroundInnerColor = mBackgroundMaterialColor.Color(500);
            mBackgroundOuterColor = mBackgroundMaterialColor.Color(800);

            // Initialize background color (in case background complication is inactive).
            mBackgroundInnerPaint = new Paint();
            mBackgroundInnerPaint.setColor(mBackgroundInnerColor);
            mBackgroundOuterPaint = new Paint();
            mBackgroundOuterPaint.setColor(mBackgroundOuterColor);

            mIsBackgroundDark = MaterialColors.isColorDark(mBackgroundOuterColor);

            // TODO material
            mWatchBinary1Color = Color.BLACK;
            mWatchBinary0Color = Color.WHITE;

            // this is not ideal with black hands on dark background, but changing it to white looks worse
            mWatchHandShadowColor = Color.BLACK;
        }

        private void initializeComplications() {
            Log.d(TAG, "initializeComplications()");

            mActiveComplicationDataSparseArray = new SparseArray<>(COMPLICATION_IDS.length);

            // Creates a ComplicationDrawable for each location where the user can render a
            // complication on the watch face.
            // All styles for the complications are defined in
            // drawable/custom_complication_styles.xml.
            ComplicationDrawable centerComplicationDrawable =
                    (ComplicationDrawable) getDrawable(R.drawable.custom_complication_styles);
            if (centerComplicationDrawable != null) {
                centerComplicationDrawable.setContext(getApplicationContext());
            }

            // Adds new complications to a SparseArray to simplify setting styles and ambient
            // properties for all complications, i.e., iterate over them all.
            mComplicationDrawableSparseArray = new SparseArray<>(COMPLICATION_IDS.length);
            mComplicationDrawableSparseArray.put(CENTER_COMPLICATION_ID, centerComplicationDrawable);

            // set default values
            setDefaultSystemComplicationProvider(CENTER_COMPLICATION_ID, ConfigData.DEFAULT_CENTER_COMPLICATION[0], ConfigData.DEFAULT_CENTER_COMPLICATION[1]);

            setComplicationsActiveAndAmbientColors();
            setActiveComplications(COMPLICATION_IDS);
        }

        private void initializeWatchFace() {
            mBinaryHand1Paint = new Paint();
            mBinaryHand1Paint.setColor(mWatchBinary1Color);
            //mBinaryHand1Paint.setStrokeWidth(HAND_STROKE_WIDTH);
            mBinaryHand1Paint.setAntiAlias(true);
            mBinaryHand1Paint.setStrokeCap(Paint.Cap.BUTT);
            //mBinaryHand1Paint.setShadowLayer(SHADOW_RADIUS, 0, 0, mWatchHandShadowColor);

            mBinaryHand0Paint = new Paint();
            mBinaryHand0Paint.setColor(mWatchBinary0Color);
            //mBinaryHand0Paint.setStrokeWidth(HAND_STROKE_WIDTH);
            mBinaryHand0Paint.setAntiAlias(true);
            mBinaryHand0Paint.setStrokeCap(Paint.Cap.BUTT);
            //mBinaryHand0Paint.setShadowLayer(SHADOW_RADIUS, 0, 0, mWatchHandShadowColor);

            mWatchHandShadowLinePaint = new Paint();
            mWatchHandShadowLinePaint.setColor(Color.BLACK); // covered by binary lines
            mWatchHandShadowLinePaint.setStrokeWidth(mBinarySegmentSize);
            mWatchHandShadowLinePaint.setAntiAlias(true);
            mWatchHandShadowLinePaint.setStrokeCap(Paint.Cap.BUTT);
            mWatchHandShadowLinePaint.setShadowLayer(SHADOW_RADIUS, 0, 0, mWatchHandShadowColor);
        }

        /* Sets active/ambient mode colors for all complications.
         *
         * Note: With the rest of the watch face, we update the paint colors based on
         * ambient/active mode callbacks, but because the ComplicationDrawable handles
         * the active/ambient colors, we only set the colors twice. Once at initialization and
         * again if the user changes the highlight color via ConfigActivity.
         */
        private void setComplicationsActiveAndAmbientColors() {
            ComplicationDrawable complicationDrawable;

            for (int complicationId : COMPLICATION_IDS) {
                complicationDrawable = mComplicationDrawableSparseArray.get(complicationId);
                if (mIsBackgroundDark) {
                    // TODO revisit these colors and use material
                    complicationDrawable.setTextColorActive(Color.WHITE);
                    complicationDrawable.setTitleColorActive(Color.WHITE);
                } else {
                    complicationDrawable.setTextColorActive(Color.BLACK);
                    complicationDrawable.setTitleColorActive(Color.BLACK);
                }

            }
        }

        @Override
        public void onDestroy() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            super.onDestroy();
        }

        @Override
        public void onPropertiesChanged(Bundle properties) {
            super.onPropertiesChanged(properties);
            Log.d(TAG, "onPropertiesChanged: low-bit ambient = " + mLowBitAmbient);

            mLowBitAmbient = properties.getBoolean(PROPERTY_LOW_BIT_AMBIENT, false);
            mBurnInProtection = properties.getBoolean(PROPERTY_BURN_IN_PROTECTION, false);

            // Updates complications to properly render in ambient mode based on the
            // screen's capabilities.
            ComplicationDrawable complicationDrawable;

            for (int COMPLICATION_ID : COMPLICATION_IDS) {
                complicationDrawable = mComplicationDrawableSparseArray.get(COMPLICATION_ID);

                complicationDrawable.setLowBitAmbient(mLowBitAmbient);
                complicationDrawable.setBurnInProtection(mBurnInProtection);
            }
        }

        /*
         * Called when there is updated data for a complication id.
         */
        @Override
        public void onComplicationDataUpdate(
                int complicationId, ComplicationData complicationData) {
            Log.d(TAG, "onComplicationDataUpdate() id: " + complicationId);

            // Adds/updates active complication data in the array.
            mActiveComplicationDataSparseArray.put(complicationId, complicationData);

            // Updates correct ComplicationDrawable with updated data.
            ComplicationDrawable complicationDrawable =
                    mComplicationDrawableSparseArray.get(complicationId);
            complicationDrawable.setComplicationData(complicationData);

            invalidate();
        }

        @Override
        public void onTapCommand(int tapType, int x, int y, long eventTime) {
            Log.d(TAG, "OnTapCommand()");
            switch (tapType) {
                case TAP_TYPE_TAP:

                    // If your background complication is the first item in your array, you need
                    // to walk backward through the array to make sure the tap isn't for a
                    // complication above the background complication.
                    for (int i = COMPLICATION_IDS.length - 1; i >= 0; i--) {
                        int complicationId = COMPLICATION_IDS[i];
                        ComplicationDrawable complicationDrawable =
                                mComplicationDrawableSparseArray.get(complicationId);

                        boolean successfulTap = complicationDrawable.onTap(x, y);

                        if (successfulTap) {
                            return;
                        }
                    }
                    break;
            }
        }

        @Override
        public void onTimeTick() {
            super.onTimeTick();
            invalidate();
        }

        @Override
        public void onAmbientModeChanged(boolean inAmbientMode) {
            super.onAmbientModeChanged(inAmbientMode);
            Log.d(TAG, "onAmbientModeChanged: " + inAmbientMode);

            mAmbient = inAmbientMode;

            updateWatchPaintStyles();

            // Update drawable complications' ambient state.
            // Note: ComplicationDrawable handles switching between active/ambient colors, we just
            // have to inform it to enter ambient mode.
            ComplicationDrawable complicationDrawable;

            for (int COMPLICATION_ID : COMPLICATION_IDS) {
                complicationDrawable = mComplicationDrawableSparseArray.get(COMPLICATION_ID);
                complicationDrawable.setInAmbientMode(mAmbient);
            }

            // Check and trigger whether or not timer should be running (only in active mode).
            updateTimer();
        }

        private void updateWatchPaintStyles() {
            if (mAmbient) {
                mBackgroundInnerPaint.setColor(Color.BLACK);
                mBackgroundOuterPaint.setColor(Color.BLACK);
                mBinaryHand1Paint.setColor(getApplicationContext().getColor(R.color.elements_digital_grey));
                //mBinaryHand0Paint.setColor(Color.WHITE);

                mBinaryHand1Paint.setAntiAlias(!mLowBitAmbient);
                mBinaryHand0Paint.setAntiAlias(!mLowBitAmbient);
                mBackgroundInnerPaint.setAntiAlias(!mLowBitAmbient);
                mBackgroundOuterPaint.setAntiAlias(!mLowBitAmbient);
                mWatchHandShadowLinePaint.setAntiAlias(!mLowBitAmbient);

                mWatchHandShadowLinePaint.clearShadowLayer();
            } else {
                mBackgroundInnerPaint.setColor(mBackgroundInnerColor);
                mBackgroundOuterPaint.setColor(mBackgroundOuterColor);
                mBinaryHand1Paint.setColor(mWatchBinary1Color);
                mBinaryHand0Paint.setColor(mWatchBinary0Color);

                mBinaryHand1Paint.setAntiAlias(true);
                mBinaryHand0Paint.setAntiAlias(true);
                mBackgroundInnerPaint.setAntiAlias(true);
                mBackgroundOuterPaint.setAntiAlias(true);
                mWatchHandShadowLinePaint.setAntiAlias(true);

                mWatchHandShadowLinePaint.setShadowLayer(SHADOW_RADIUS, 0, 0, mWatchHandShadowColor);
            }
        }

        @Override
        public void onInterruptionFilterChanged(int interruptionFilter) {
            super.onInterruptionFilterChanged(interruptionFilter);
            boolean inMuteMode = (interruptionFilter == WatchFaceService.INTERRUPTION_FILTER_NONE);

            /* Dim display in mute mode. */
            if (mMuteMode != inMuteMode) {
                mMuteMode = inMuteMode;
                mBinaryHand1Paint.setAlpha(inMuteMode ? 100 : 255);
                mBinaryHand0Paint.setAlpha(inMuteMode ? 100 : 255);
                invalidate();
            }
        }

        @Override
        public void onSurfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            super.onSurfaceChanged(holder, format, width, height);

            /*
             * Find the coordinates of the center point on the screen, and ignore the window
             * insets, so that, on round watches with a "chin", the watch face is centered on the
             * entire screen, not just the usable portion.
             */
            mCenterX = width / 2f;
            mCenterY = height / 2f;

            /*
             * Calculates location bounds for right and left circular complications. Please note,
             * we are not demonstrating a long text complication in this watch face.
             *
             * We suggest using at least 1/4 of the screen width for circular (or squared)
             * complications and 2/3 of the screen width for wide rectangular complications for
             * better readability.
             */

            // For most Wear devices, width and height are the same, so we just chose one (width).
            int sizeOfComplication = width / 4;
            CENTER_COMPLICATION_CIRCLE_RADIUS = sizeOfComplication/2;
            mBinarySegmentSize = (mCenterX - CENTER_COMPLICATION_CIRCLE_RADIUS) / 10;
            mWatchHandShadowLinePaint.setStrokeWidth(mBinarySegmentSize);
            CENTER_HOUR_CIRCLE_RADIUS = CENTER_COMPLICATION_CIRCLE_RADIUS + mBinarySegmentSize * 4;

            Rect centerBounds =
                    // Left, Top, Right, Bottom
                    new Rect(
                            (int) (mCenterX - CENTER_COMPLICATION_CIRCLE_RADIUS),
                            (int) (mCenterY - CENTER_COMPLICATION_CIRCLE_RADIUS),
                            (int) (mCenterX + CENTER_COMPLICATION_CIRCLE_RADIUS),
                            (int) (mCenterY + CENTER_COMPLICATION_CIRCLE_RADIUS));

            ComplicationDrawable centerComplicationDrawable =
                    mComplicationDrawableSparseArray.get(CENTER_COMPLICATION_ID);
            centerComplicationDrawable.setBounds(centerBounds);
        }

        @Override
        public void onDraw(Canvas canvas, Rect bounds) {
            long now = System.currentTimeMillis();
            mCalendar.setTimeInMillis(now);

            drawBackground(canvas);
            drawComplications(canvas, now);
            drawWatchFace(canvas);
        }

        private void drawBackground(Canvas canvas) {
            if (mAmbient && (mLowBitAmbient || mBurnInProtection)) {
                canvas.drawColor(Color.BLACK);
            } else {
                canvas.drawColor(mBackgroundInnerPaint.getColor());
                canvas.drawCircle(mCenterX, mCenterY, CENTER_HOUR_CIRCLE_RADIUS, mBackgroundOuterPaint);
                canvas.drawCircle(mCenterX, mCenterY, CENTER_COMPLICATION_CIRCLE_RADIUS, mBackgroundInnerPaint);
            }
        }

        private void drawComplications(Canvas canvas, long currentTimeMillis) {
            ComplicationDrawable complicationDrawable;

            for (int complicationId : COMPLICATION_IDS) {
                complicationDrawable = mComplicationDrawableSparseArray.get(complicationId);
                complicationDrawable.draw(canvas, currentTimeMillis);
            }
        }

        private void drawWatchFace(Canvas canvas) {
            drawWatchHands(canvas);
        }

        private Paint[] getBinaryPaint(int num, int bits) {
            Paint[] binaryPaint = new Paint[bits];
            for (int i = 0; i < bits; i++) {
                if ((num & (1<<i)) == 0) {
                    binaryPaint[i] = mBinaryHand0Paint;
                } else {
                    binaryPaint[i] = mBinaryHand1Paint;
                }
            }
            return binaryPaint;
        }

        private void drawBinaryLine(Canvas canvas, float startX, float startY, int num, int bits) {
            Paint[] bitPaints = getBinaryPaint(num, bits);
            final float binaryRadius = mBinarySegmentSize / 2;

            // shadow line
            canvas.drawLine(startX, startY, startX,startY - (bits * mBinarySegmentSize), mWatchHandShadowLinePaint);

            for (int i = 0; i < bits; i++) {
                canvas.drawRect(startX - binaryRadius,
                        startY - mBinarySegmentSize * i,
                        startX + binaryRadius,
                        startY - mBinarySegmentSize * (i+1),
                        bitPaints[i]);
            }
        }

        private void drawWatchHands(Canvas canvas) {
            /*
             * These calculations reflect the rotation in degrees per unit of time, e.g.,
             * 360 / 60 = 6 and 360 / 12 = 30.
             */
            final float minuteHandOffset = mCalendar.get(Calendar.SECOND) / 10f;
            final int minute = mCalendar.get(Calendar.MINUTE);
            final float minutesRotation = minute * 6f + minuteHandOffset;

            final float hourHandOffset = minute / 2f;
            final int hour = mCalendar.get(Calendar.HOUR);
            final float hoursRotation = (hour * 30) + hourHandOffset;

            /*
             * Save the canvas state before we can begin to rotate it.
             */
            canvas.save();

            // hours
            canvas.rotate(hoursRotation, mCenterX, mCenterY);
            drawBinaryLine(canvas, mCenterX, mCenterY - CENTER_COMPLICATION_CIRCLE_RADIUS, hour, 4);

            // minutes
            canvas.rotate(minutesRotation - hoursRotation, mCenterX, mCenterY);
            drawBinaryLine(canvas, mCenterX, mCenterY - CENTER_HOUR_CIRCLE_RADIUS, minute, 6);

            /* Restore the canvas' original orientation. */
            canvas.restore();

        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);

            if (visible) {

                // Preferences might have changed since last time watch face was visible.
                loadSavedPreferences();

                // With the rest of the watch face, we update the paint colors based on
                // ambient/active mode callbacks, but because the ComplicationDrawable handles
                // the active/ambient colors, we only need to update the complications' colors when
                // the user actually makes a change to the highlight color, not when the watch goes
                // in and out of ambient mode.
                setComplicationsActiveAndAmbientColors();
                updateWatchPaintStyles();

                registerReceiver();
                // Update time zone in case it changed while we weren't visible.
                mCalendar.setTimeZone(TimeZone.getDefault());
                invalidate();
            } else {
                unregisterReceiver();
            }

            /* Check and trigger whether or not timer should be running (only in active mode). */
            updateTimer();
        }

        private void registerReceiver() {
            if (mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = true;
            IntentFilter filter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
            BinaryAnalogWatchFaceService.this.registerReceiver(mTimeZoneReceiver, filter);
        }

        private void unregisterReceiver() {
            if (!mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = false;
            BinaryAnalogWatchFaceService.this.unregisterReceiver(mTimeZoneReceiver);
        }

        /**
         * Starts/stops the {@link #mUpdateTimeHandler} timer based on the state of the watch face.
         */
        private void updateTimer() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            if (shouldTimerBeRunning()) {
                mUpdateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME);
            }
        }

        /**
         * Returns whether the {@link #mUpdateTimeHandler} timer should be running. The timer should
         * only run in active mode.
         */
        private boolean shouldTimerBeRunning() {
            return isVisible() && !mAmbient;
        }
    }
}

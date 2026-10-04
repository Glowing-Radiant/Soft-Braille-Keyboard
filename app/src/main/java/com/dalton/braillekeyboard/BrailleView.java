/*
 * Copyright (C) 2016 The Soft Braille Keyboard Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.dalton.braillekeyboard;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Paint.FontMetrics;
import android.graphics.Paint.Style;
import android.graphics.Point;
import android.graphics.Rect;
import android.os.SystemClock;
import android.os.Vibrator;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.Display;
import android.view.MotionEvent;
import android.view.SoundEffectConstants;
import android.view.Surface;
import android.view.View;
import android.view.accessibility.AccessibilityManager;

import com.dalton.braillekeyboard.Options.KeyboardFeedback;
import com.dalton.braillekeyboard.Pad.Coords;
import com.dalton.braillekeyboard.Pad.Swipe;

/**
 * This View facilitates displaying a Braille keyboard to the user on the entire
 * screen and handling taps and swipes by using the ActionHandler.
 * 
 * This View holds an instance to an implementation of Pad and parses all of
 * it's touch events to the Pad to resolve which dots were hit or nearest the
 * user's touch.
 * 
 * The View will then pass the appropriate swipe and dot pressed events to an
 * ActionHandler to perform the appropriate action.
 * 
 * This View also implements the OnActionListener callback and will display
 * results, send notifications or change View states appropriate to the
 * callbacks received from the ActionHandler.
 * 
 * You should register an IME listener with this View in order for the
 * ActionHandler and this View to function. See
 * onInitialiseForInput(KeyboardListener listener).
 * 
 * You should always call close() when you are done with the View to release
 * resources.
 */
public class BrailleView extends View {
    private static final String TAG = "BrailleView";
    private static final long LONG_VIBRATION = 300;
    private static final long MEDIUM_VIBRATION = 125;
    private static final byte NO_DOTS = 0;
    private static final long LONG_HOLD_DELAY = 1200;
    private static final long QUICK_VIBRATION = 25;
    // Phones' multi-finger system gestures, like screenshots, use three or
    // more fingers.
    private static final int MIN_SYSTEM_GESTURE_FINGERS = 3;

    private final AccessibilityManager accessibilityManager;
    private final List<Coords> lastDotList = new ArrayList<Coords>();
    private final Paint circlePaint;
    private final Paint paint;
    private final Rect circleTextBounds = new Rect();
    private final Point displaySize = new Point();
    private final int[] point = new int[2];
    private final Vibrator vibrator;
    private final SoundThemes sounds;
    private final ActionHandler.OnActionListener actionListener = new ActionHandler.OnActionListener() {

        @Override
        public void onSetDots(boolean dot7, boolean dot8) {
            setDotsSevenEight(dot7, dot8);
        }

        @Override
        public void onText(String format, String text, boolean isPasswordField) {
            speech.readConsiderPassword(getContext(), format, text,
                    isPasswordField, Speech.QUEUE_FLUSH);
        }

        @Override
        public void onText(String format, String text, boolean isPasswordField,
                int mode) {
            speech.readConsiderPassword(getContext(), format, text,
                    isPasswordField, mode);
        }

        @Override
        public void onNotify(boolean vibrate, Earcons.Sound sound) {
            sendNotification(vibrate, sound);
        }

        @Override
        public void onSetLocale(Locale locale) {
            setLocale(locale);
        }

        @Override
        public void onShrink() {
            setLocale(Locale.getDefault());
            shrinkKeyboard = true;
            invalidate();
            requestLayout();
            listener.updateFullscreenMode();
        }

        @Override
        public void onPrivacy() {
            setPrivacy();
        }

        @Override
        public void onShutup() {
            speech.stop();
        }

        @Override
        public boolean onShowMenu(KeyboardMenu.Model model,
                final KeyboardMenu.OnCloseListener closeListener) {
            // Without a screen reader the menu can't be explored.
            if (!accessibilityManager.isTouchExplorationEnabled()
                    || getWindowToken() == null) {
                return false;
            }
            dismissMenu();
            menu = new KeyboardMenu(getContext(), model,
                    new KeyboardMenu.OnCloseListener() {
                        @Override
                        public void onClose(boolean performing) {
                            menu = null;
                            listener.onMenuShown(false);
                            closeListener.onClose(performing);
                        }
                    });
            listener.onMenuShown(true);
            menu.show(BrailleView.this);
            return true;
        }
    };

    private ActionHandler actionHandler;
    // The keyboard menu while it is shown on screen.
    private KeyboardMenu menu;
    private DisplayParams displayParams = null;
    private boolean dot7;
    private boolean dot8;
    private Coords[] dotsDown = new Coords[8];
    private boolean handledSwipe = false;
    private KeyboardListener listener;
    private Pad pad;
    private long requiredTouchTime = 0;
    private boolean shrinkKeyboard;
    private boolean systemGestureHintSpoken;
    private Speech speech;

    public BrailleView(Context context, AttributeSet attrs) {
        super(context, attrs);
        paint = new Paint();
        circlePaint = new Paint();
        accessibilityManager = (AccessibilityManager) context
                .getSystemService(Context.ACCESSIBILITY_SERVICE);
        vibrator = (Vibrator) context
                .getSystemService(Context.VIBRATOR_SERVICE);
        sounds = new SoundThemes(context);
    }

    /**
     * Gets the View ready to receive touch events from the user and facilitates
     * communication with the underlying IME.
     * 
     * @param listener
     *            The KeyboardListener implementation of to communicate with the
     *            IME.
     */
    public void onInitialiseForInput(Context context, KeyboardListener listener) {
        this.listener = listener;
        systemGestureHintSpoken = false;

        // Set up speech and announce when it's ready to the user.
        speech = new Speech(getContext(), new Speech.OnReadyListener() {

            @Override
            public void ttsReady() {
                setLocale(BrailleView.this.listener.getLocale());
                speech.speak(getContext(),
                        getContext().getString(R.string.ready),
                        Speech.QUEUE_FLUSH);
                if (GesturePractice.isOn(getContext())) {
                    speech.speak(getContext(), getContext().getString(
                            R.string.gesture_practice_reminder),
                            Speech.QUEUE_ADD);
                }
            }
        });

        // When we launch the keyboard it should take up the full screen.
        if (shrinkKeyboard) {
            expandKeyboard();
        }

        if (displayParams != null) {
            setDisplayParams(getWidth(), getHeight());
            loadDefaultPad(getWidth(), getHeight());
            invalidate();
            requestLayout();
        }
        sounds.prepare();
        actionHandler = new ActionHandler(context);
        actionHandler.setCallback(actionListener);
        actionHandler.setKeyboardListener(listener);
    }

    /**
     * Release resources and vibrate the device to tell the user we are closing.
     */
    public void close() {
        dismissMenu();
        if (Options.getBooleanPreference(
                getContext(),
                R.string.pref_vibrate_on_exit_key,
                Boolean.parseBoolean(getContext().getString(
                        R.string.pref_vibrate_on_exit_default)))) {
            vibrator.vibrate(LONG_VIBRATION * 2);
        }
        speech.shutdown(getContext().getString(R.string.closing_keyboard));
        actionHandler.shutdown();
        sounds.release();
        setLocale(Locale.getDefault(), false);
    }

    private void dismissMenu() {
        if (menu != null) {
            menu.dismiss();
            menu = null;
            listener.onMenuShown(false);
        }
    }

    @Override
    public void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        setDisplayParams(w, h);
        loadDefaultPad(w, h);
    }

    @Override
    public void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (!shrinkKeyboard
                && displayParams != null
                && Options.getBooleanPreference(
                        getContext(),
                        R.string.pref_show_circles_key,
                        Boolean.parseBoolean(getContext().getString(
                                R.string.pref_show_circles_default)))) {
            // We should show a visual representation of the view according to
            // user preference.
            
            if (accessibilityManager.isTouchExplorationEnabled()) {
                setContentDescription(TouchPassthroughService.isRunning() ? getContext()
                        .getString(R.string.braille_keyboard_ready)
                        : getTouchExplorationHint());
            } else {
                setContentDescription(null);
            }

            List<Coords> keys = getKeys();
            // For each dot draw a circle on the screen at it's position and
            // write the corresponding dot number in the circle.
            for (int i = 0; i < keys.size(); i++) {
                point[0] = keys.get(i).x;
                point[1] = keys.get(i).y;
                toView(point);
                int x = point[0];
                int y = point[1];
                String text = String.valueOf(i + 1);
                paint.getTextBounds(text, 0, text.length(), circleTextBounds);
                canvas.drawCircle(x, y, displayParams.radius, circlePaint);
                canvas.drawText(text, x, y, paint);
            }
        } else if (shrinkKeyboard) {
            String text = getContext().getString(R.string.expand_keyboard);
            if (accessibilityManager.isTouchExplorationEnabled()) {
                // Use more descriptive text for screen reader users
                text = getContext()
                        .getString(R.string.expand_keyboard_talkback);
            }
            canvas.drawText(text, displayParams.x, displayParams.y, paint);
            setContentDescription(text);
        }
        setPrivacy();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        final int SHRINK_FACTOR = 2;
        int widthMode = MeasureSpec.getMode(widthMeasureSpec);
        int widthSize = MeasureSpec.getSize(widthMeasureSpec);
        int heightMode = MeasureSpec.getMode(heightMeasureSpec);
        int heightSize = MeasureSpec.getSize(heightMeasureSpec);

        int width;
        int height;

        int desiredWidth = widthSize;
        int desiredHeight = heightSize;
        if (shrinkKeyboard) {
            desiredHeight /= SHRINK_FACTOR;
        }
        if (widthMode == MeasureSpec.EXACTLY) {
            width = widthSize;
        } else if (widthMode == MeasureSpec.AT_MOST) {
            width = Math.min(desiredWidth, widthSize);
        } else {
            width = desiredWidth;
        }

        if (heightMode == MeasureSpec.EXACTLY) {
            height = heightSize;
        } else if (heightMode == MeasureSpec.AT_MOST) {
            height = Math.min(desiredHeight, heightSize);
        } else {
            height = desiredHeight;
        }
        setMeasuredDimension(width, height);
    }

    @Override
    public boolean onHoverEvent(MotionEvent event) {
        // Hover events mean explore by touch is getting the touches instead of
        // the keyboard. If touch pass-through is on, another accessibility
        // service replaced its region or touch exploration restarted, so set
        // it again. Otherwise explain once per touch how to fix that.
        if (!shrinkKeyboard && TouchPassthroughService.isRunning()) {
            if (event.getActionMasked() == MotionEvent.ACTION_HOVER_ENTER) {
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "Touch pass-through region lost, setting it again");
                }
                TouchPassthroughService.reassertSoon();
            }
        } else if (accessibilityManager.isTouchExplorationEnabled()
                && event.getActionMasked() == MotionEvent.ACTION_HOVER_ENTER) {
            speech.speak(getContext(), shrinkKeyboard ? getContext().getString(
                    R.string.expand_keyboard_talkback)
                    : getTouchExplorationHint(), Speech.QUEUE_FLUSH);
        }
        return super.onHoverEvent(event);
    }

    // What a screen reader user must do for the keyboard to get their touches.
    private String getTouchExplorationHint() {
        return getContext().getString(
                TouchPassthroughService.isSupported() ? R.string.touch_passthrough_needed
                        : R.string.switch_off_talkback);
    }

    @Override
    public boolean onTouchEvent(MotionEvent motionEvent) {
        super.onTouchEvent(motionEvent);
        // Get the height and width of the keyboard.
        // If the keyboard follows the screen the standard dimensions are
        // correct. Otherwise the width is the maximum of the height and the
        // width and the height is the minimum of the two.
        // This is because the user holds the phone in landscape mode, but the
        // screen might be fixed to portrait mode. It makes more sense to use
        // the keyboard in landscape mode and the user doesn't care about
        // orientation of the screen.
        int width = followsScreen() ? getWidth() : Math.max(getWidth(),
                getHeight());
        int height = followsScreen() ? getHeight() : Math.min(getWidth(),
                getHeight());
        int action = motionEvent.getActionMasked();
        int index = motionEvent.getActionIndex();
        if (BuildConfig.DEBUG) {
            logTouchLatency(motionEvent);
        }
        if ((action == MotionEvent.ACTION_UP
                || action == MotionEvent.ACTION_CANCEL)
                && TouchPassthroughService.isRunning()) {
            TouchPassthroughService.reassertAfterTouch();
        }
        int id = motionEvent.getPointerId(index);
        point[0] = (int) motionEvent.getX(index);
        point[1] = (int) motionEvent.getY(index);
        // The view may be used perpendicular to its intended purpose, see
        // above, or locked to the device.
        toKeyboard(point);
        int x = point[0];
        int y = point[1];
        Swipe swipe;
        switch (action) {
        case MotionEvent.ACTION_DOWN:
        case MotionEvent.ACTION_POINTER_DOWN:
            if (shrinkKeyboard) {
                expandKeyboard();
            } else {
                // store the time at which the user must hold their fingers down
                // for if they want to calibrate.
                // Only record the time for the first touch.
                requiredTouchTime = requiredTouchTime == 0 ? System
                        .currentTimeMillis() + LONG_HOLD_DELAY
                        : requiredTouchTime;

                if (!updatePointer(dotsDown, id, x, y, true)
                        && id < dotsDown.length) {
                    // add a new unique dot to the list of dots that were
                    // pushed.
                    dotsDown[id] = new Coords(id, x, y);
                }
            }
            break;
        case MotionEvent.ACTION_HOVER_EXIT:
        case MotionEvent.ACTION_UP:
            if (!handleVoiceInput()) {
                GestureStyle style = getGestureStyle();
                if (pad != null && pressedDotString() != NO_DOTS
                        && style == GestureStyle.TALKBACK) {
                    setDots();
                    handleTalkBackGesture();
                    lastDotList.clear();
                } else if (pad != null && pressedDotString() != NO_DOTS
                        && style == GestureStyle.MIXED) {
                    setDots();
                    if (!handledSwipe) {
                        handleMixedGesture();
                    }
                    lastDotList.clear();
                } else if (pad != null && pressedDotString() != NO_DOTS) {
                    setDots();
                    if (!handledSwipe && actionHandler.isMenuOpen()) {
                        // The spoken menu is navigated with TalkBack gestures.
                        handleTalkBackGesture();
                    } else if (!handledSwipe && !handleClassicMenuGesture()) {
                        // single finger flicks
                        if ((swipe = handledSwipeAction(dotsDown,
                                isPortraitLayout())) != Swipe.NONE) {
                            actionHandler.handleSwipe(getContext(), swipe);
                        } else { // all swipe attempts failed so resort to
                            // entering character
                            handleTypedCharacter();
                        }
                    }
                    lastDotList.clear();
                }
            }
            resetDots();

            if (pad != null) {
                pad.updateKeys(isPortraitLayout());
            }

            if (Options.getBooleanPreference(
                    getContext(),
                    R.string.pref_show_circles_key,
                    Boolean.parseBoolean(getContext().getString(
                            R.string.pref_show_circles_default)))) {
                // redraw to show the new positions of the Braille dots.
                invalidate();
            }
            break;
        case MotionEvent.ACTION_HOVER_MOVE:
        case MotionEvent.ACTION_MOVE:
            // Update all active pointers for better multi-touch support
            // This is especially important for dots 1, 2, 3 combinations
            int pointerCount = motionEvent.getPointerCount();
            for (int i = 0; i < pointerCount; i++) {
                int pointerId = motionEvent.getPointerId(i);
                point[0] = (int) motionEvent.getX(i);
                point[1] = (int) motionEvent.getY(i);
                // Apply same coordinate transformation as for ACTION_DOWN
                toKeyboard(point);
                updatePointer(dotsDown, pointerId, point[0], point[1], false);
            }
            break;
        case MotionEvent.ACTION_CANCEL:
            // The system took the touch, for example for a multi-finger
            // screenshot gesture. Nothing more is delivered for it, so forget
            // its fingers rather than typing them into the next character.
            resetDots();
            lastDotList.clear();
            if (motionEvent.getPointerCount() >= MIN_SYSTEM_GESTURE_FINGERS
                    && !systemGestureHintSpoken) {
                // Many phones have three finger screenshot gestures that take
                // every such touch. Explain once each time the keyboard opens.
                systemGestureHintSpoken = true;
                speech.speak(getContext(), getContext().getString(
                        R.string.multi_finger_touch_taken), Speech.QUEUE_ADD);
            }
            break;
        case MotionEvent.ACTION_POINTER_UP:
            if (getGestureStyle() == GestureStyle.TALKBACK) {
                // TalkBack gestures are recognised once all fingers lift, so
                // just remember where this finger left the screen.
                if (!setPad(id, width, height)) {
                    updatePointer(dotsDown, id, x, y, false);
                }
            } else if (!setPad(id, width, height)) {
                updatePointer(dotsDown, id, x, y, false);
                setDots();
                if (getGestureStyle() == GestureStyle.CLASSIC
                        && actionHandler.isMenuOpen()) {
                    // The spoken menu's gestures are recognised once all
                    // fingers lift.
                } else if (getGestureStyle() == GestureStyle.CLASSIC
                        && !handledSwipe && handleClassicMenuGesture()) {
                    handledSwipe = true;
                } else if (isClassicHoldSwipeNow()
                        && (swipe = handledSwipeAction(dotsDown,
                        isPortraitLayout())) != Swipe.NONE) {
                    // Hold one finger while swiping with another
                    handledSwipe = true;
                    actionHandler.handleSwipe(getContext(), swipe);
                }
            }
            break;
        default:
        }
        return true;
    }

    // For diagnosing sluggish input: how long touches took to reach the
    // keyboard, for example through a screen reader's touch handling.
    private static void logTouchLatency(MotionEvent event) {
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_MOVE) {
            return;
        }
        Log.d(TAG, MotionEvent.actionToString(action) + " pointers="
                + event.getPointerCount() + " latency="
                + (SystemClock.uptimeMillis() - event.getEventTime()) + "ms");
    }

    /**
     * Goes back a step in the keyboard menu if it is shown on screen.
     *
     * @return false if the menu isn't shown.
     */
    public boolean backInMenu() {
        if (menu == null) {
            return false;
        }
        menu.back();
        return true;
    }

    public boolean getShrinkKeyboard() {
        return shrinkKeyboard;
    }

    public boolean setLocale(Locale locale) {
        return setLocale(locale, true);
    }

    private boolean setLocale(Locale locale, boolean setTTSLocale) {
        if (locale != null) {
            Resources resources = getContext().getResources();
            DisplayMetrics displayMetrics = resources.getDisplayMetrics();
            android.content.res.Configuration conf = resources
                    .getConfiguration();
            if (!conf.locale.equals(locale)) {
                if (!setTTSLocale || (setTTSLocale && speech.setLocale(locale))) {
                    conf.locale = locale;
                    resources.updateConfiguration(conf, displayMetrics);

                    return true;
                }
            }
        }
        return false;
    }

    private void loadDefaultPad(int w, int h) {
        int width = followsScreen() ? w : Math.max(w, h);
        int height = followsScreen() ? h : Math.min(w, h);
        if (!setDefaultPad(w, h, width, height)) {
            speech.speak(getContext(),
                    getContext().getString(R.string.keyboard_error),
                    Speech.QUEUE_FLUSH);
        }
    }

    private boolean setPad(int id, int width, int height) {
        final int TOTAL_DOTS = 6;
        final int ONE_SIDE = 3;
        // For whatever reason we won't be able to set a pad
        if (requiredTouchTime > System.currentTimeMillis()
                || countDotsDown(dotsDown) != ONE_SIDE) {
            return false;
        }
        if (lastDotList.size() != ONE_SIDE && lastDotList.size() != 0) {
            lastDotList.clear();
            return false;
        }

        // Add the first three dots to the current dot list.
        for (int i = 0; i < lastDotList.size(); i++) {
            Coords coord = lastDotList.get(i);
            dotsDown[ONE_SIDE + i] = new Coords(ONE_SIDE + id, coord.x, coord.y);
        }

        if (countDotsDown(dotsDown) == TOTAL_DOTS) {
            setDotsSevenEight(false, false);
            Coords[] sixDots = new Coords[TOTAL_DOTS];

            for (int i = 0, j = 0; i < dotsDown.length && j < sixDots.length; i++) {
                if (dotsDown[i] != null) {
                    int localX = dotsDown[i].getSecondX();
                    int localY = dotsDown[i].getSecondY();
                    sixDots[j++] = new Coords(localX, localY);
                }
            }
            boolean result;
            if ((result = selectPad(sixDots, width, height))) {
                speech.speak(getContext(), getContext()
                        .getString(pad.padString), Speech.QUEUE_FLUSH);
                vibrator.vibrate(MEDIUM_VIBRATION);
            } else {
                speech.speak(getContext(),
                        getContext().getString(R.string.keyboard_error),
                        Speech.QUEUE_FLUSH);
                vibrator.vibrate(QUICK_VIBRATION);
            }
            lastDotList.clear();
            resetDots();
            return result;
        } else {
            // Add the first three dots that have been tuched to a member
            // variable for reference on the second touch of three fingers
            for (int i = 0; i < dotsDown.length; i++) {
                if (dotsDown[i] != null) {
                    lastDotList.add(dotsDown[i]);
                    dotsDown[i] = null;
                }
            }
            speech.speak(getContext(),
                    getContext().getString(R.string.keyboard_next_three),
                    Speech.QUEUE_FLUSH);
            vibrator.vibrate(MEDIUM_VIBRATION);
            return true;
        }
    }

    // Set the pad using a default pad.
    private boolean setDefaultPad(int w, int h, int padWidth, int padHeight) {
        boolean useEightDots = Options.getBooleanPreference(
                getContext(),
                R.string.pref_use_eight_dots_key,
                Boolean.parseBoolean(getContext().getString(
                        R.string.pref_use_eight_dots_default)));
        try {
            pad = PadUtilities
                    .displayDefaultPad(getContext(), padWidth, padHeight,
                            isPortraitLayout(h > w), useEightDots);
            return true;
        } catch (IllegalArgumentException e) {
            // handled below
        }
        return false;
    }

    // Display a pad according to the possitioning of the fingers (user
    // calibration)
    private boolean selectPad(Coords[] dots, int width, int height) {
        boolean useEightDots = Options.getBooleanPreference(
                getContext(),
                R.string.pref_use_eight_dots_key,
                Boolean.parseBoolean(getContext().getString(
                        R.string.pref_use_eight_dots_default)));
        try {
            pad = PadUtilities.selectPad(getContext(), dots, width, height,
                    isPortraitLayout(), useEightDots);
            return true;
        } catch (IllegalArgumentException e) {
            // handled below
        }
        return false;
    }

    // Whether the keyboard turns with the screen.
    private boolean followsScreen() {
        return displayParams.autoRotate && !displayParams.forceLock;
    }

    // Whether a portrait screen is used as a landscape keyboard by swapping
    // the axes, which pads lay out and read swipes for differently.
    private boolean isPortraitLayout() {
        return isPortraitLayout(getHeight() > getWidth());
    }

    private boolean isPortraitLayout(boolean portraitScreen) {
        return portraitScreen && !displayParams.autoRotate
                && !displayParams.forceLock;
    }

    // Maps a point on the view to the keyboard.
    private void toKeyboard(int[] p) {
        if (displayParams.forceLock) {
            mapLocked(p, false);
        } else if (isPortraitLayout()) {
            swap(p);
        }
    }

    // Maps a point on the keyboard to the view.
    private void toView(int[] p) {
        if (displayParams.forceLock) {
            mapLocked(p, true);
        } else if (isPortraitLayout()) {
            swap(p);
        }
    }

    private static void swap(int[] p) {
        int x = p[0];
        p[0] = p[1];
        p[1] = x;
    }

    // With the orientation lock forced the keyboard is fixed to the device:
    // it is laid out as on a landscape screen with the top of the device on
    // the left, whichever way the screen has turned. Maps between that and
    // the view.
    private void mapLocked(int[] p, boolean toView) {
        int w = getWidth();
        int h = getHeight();
        int x = p[0];
        int y = p[1];
        switch (getLockRotation()) {
        case Surface.ROTATION_0:
            p[0] = toView ? w - y : y;
            p[1] = toView ? x : w - x;
            break;
        case Surface.ROTATION_180:
            p[0] = toView ? y : h - y;
            p[1] = toView ? h - x : x;
            break;
        case Surface.ROTATION_270:
            p[0] = w - x;
            p[1] = h - y;
            break;
        default: // ROTATION_90 is the keyboard's own layout.
        }
    }

    // The screen's rotation from the device's portrait orientation. Tablets
    // are naturally landscape, so their rotation is counted from a quarter
    // turn earlier.
    private int getLockRotation() {
        Display display = getDisplay();
        if (display == null) {
            return getWidth() >= getHeight() ? Surface.ROTATION_90
                    : Surface.ROTATION_0;
        }
        int rotation = display.getRotation();
        display.getRealSize(displaySize);
        boolean sideways = rotation == Surface.ROTATION_90
                || rotation == Surface.ROTATION_270;
        boolean naturallyLandscape = sideways ? displaySize.y > displaySize.x
                : displaySize.x > displaySize.y;
        return naturallyLandscape ? (rotation + 1) % 4 : rotation;
    }

    private List<Coords> getKeys() {
        List<Coords> keys = new ArrayList<Coords>();
        List<Coords> padKeys = pad.getKeys();
        int dots = listener.getDots();
        // should always be == dots, but handle errors cleanly
        if (dots == -1) {
            dots = padKeys.size();
        }
        int dotsInUse = Math.min(padKeys.size(), dots);
        keys.addAll(padKeys.subList(0, dotsInUse));
        return keys;
    }

    private void setDots() {
        if (pad == null) {
            return;
        }
        // Sort the dots into their actual positions eg. dotsDown[0] = dot1
        // dotsDown[1] = dot 2 etc.
        // Previous ordering is based on the order that fingers hit the screen.
        dotsDown = pad.getBrailleDots(dotsDown, listener.getDots());
    }

    private void resetDots() {
        requiredTouchTime = 0;
        for (int i = 0; i < dotsDown.length; i++) {
            dotsDown[i] = null;
        }
        handledSwipe = false;
    }

    private void setDotsSevenEight(boolean dot7, boolean dot8) {
        if (!dot7 && !dot8) {
            this.dot7 = dot7;
            this.dot8 = dot8;
        }

        if (dot7) {
            this.dot7 = dot7;
        }
        if (dot8) {
            this.dot8 = dot8;
        }
    }

    private byte pressedDotString() {
        byte mask = 1;
        byte value = 0;

        // See what dots of the first six are pressed.
        for (int i = 0; i < dotsDown.length - 2; i++) {
            if (dotsDown[i] != null) {
                // it's present so set the bit in the bitstring.
                value |= mask;
            }
            mask <<= 1;
        }

        // special case for setting dots 7 and 8.
        // They can be activated by pressing them on the screen or using a swipe
        // gesture.
        if (dot7 || dotsDown[6] != null) {
            value |= mask;
        }
        mask <<= 1;
        if (dot8 || dotsDown[7] != null) {
            value |= mask;
        }
        return value;
    }

    private void sendNotification(boolean vibrate, Earcons.Sound sound) {
        if (vibrate
                && (KeyboardFeedback.VIBRATE.value & Integer.parseInt(Options
                        .getStringPreference(getContext(),
                                R.string.pref_keyboard_feedback_key,
                                KeyboardFeedback.ALL.getValue()))) != 0) {
            vibrator.vibrate(QUICK_VIBRATION);
        }
        if (sound != null
                && (KeyboardFeedback.SOUND.value & Integer.parseInt(Options
                        .getStringPreference(getContext(),
                                R.string.pref_keyboard_feedback_key,
                                KeyboardFeedback.ALL.getValue()))) != 0) {
            if (!sounds.play(sound)) {
                playSoundEffect(SoundEffectConstants.CLICK);
            }
        }
    }

    private void expandKeyboard() {
        speech.speak(getContext(),
                getContext().getString(R.string.keyboard_full_screen),
                Speech.QUEUE_FLUSH);
        shrinkKeyboard = false;
        setLocale(listener.getLocale());
        invalidate();
        requestLayout();
        listener.updateFullscreenMode();
    }

    private static int countDotsDown(Coords[] dots) {
        int count = 0;
        for (Coords coords : dots) {
            if (coords != null) {
                ++count;
            }
        }
        return count;
    }

    private static boolean updatePointer(Coords[] coords, int id, int x, int y,
            boolean reset) {
        for (int i = 0; i < coords.length; i++) {
            if (coords[i] != null) {
                if (coords[i].id == id) {
                    if (reset) {
                        coords[i] = new Coords(id, x, y);
                    }
                    coords[i].setSecondCords(x, y);
                    return true;
                }
            }
        }
        return false;
    }

    private Swipe handledSwipeAction(Coords[] coords, boolean swap) {
        Swipe value;
        try {
            value = pad.getSwipe(coords, swap);
            return value;
        } catch (NullPointerException npe) { // can be null if invalidate
            // somehow is called
        }
        return Swipe.NONE;
    }

    // The gesture styles the user can choose in the settings.
    private enum GestureStyle {
        CLASSIC, TALKBACK, MIXED
    }

    private GestureStyle getGestureStyle() {
        String style = Options.getStringPreference(getContext(),
                R.string.pref_gesture_style_key,
                getContext().getString(R.string.pref_gesture_style_default));
        if (getContext().getString(R.string.pref_gesture_style_talkback_value)
                .equals(style)) {
            return GestureStyle.TALKBACK;
        } else if (getContext().getString(
                R.string.pref_gesture_style_mixed_value).equals(style)) {
            return GestureStyle.MIXED;
        }
        return GestureStyle.CLASSIC;
    }

    private byte[] getDotDirections() {
        return pad.getDotDirections(dotsDown, isPortraitLayout());
    }

    // Whether a finger lifting now completes a classic "hold a dot and swipe
    // with another finger" gesture. In the mixed style that is only the case
    // when exactly one finger swiped, as two or more swiping fingers are a
    // TalkBack gesture recognised once all fingers lift.
    private boolean isClassicHoldSwipeNow() {
        if (getGestureStyle() != GestureStyle.MIXED) {
            return true;
        }
        byte[] directions = getDotDirections();
        return !actionHandler.isMenuOpen()
                && TalkBackGesture.countSwipingFingers(directions) == 1
                && TalkBackGesture.countHeldDots(directions) > 0;
    }

    // In the mixed style one swiping finger is a classic gesture and two or
    // more are a TalkBack gesture. The TalkBack keyboard menu, navigated with
    // one finger, takes every gesture while it is open.
    private void handleMixedGesture() {
        byte[] directions = getDotDirections();
        int fingers = TalkBackGesture.countSwipingFingers(directions);
        if (fingers == 0) {
            handleTypedCharacter();
        } else if (fingers > 1 || actionHandler.isMenuOpen()) {
            TalkBackGesture.Action action = TalkBackGesture.classify(directions);
            if (action != null
                    && !TalkBackGesture.isAvailableInMixedStyle(action)) {
                action = null;
            }
            actionHandler.handleTalkBackAction(getContext(), action,
                    directions);
        } else {
            Swipe swipe = handledSwipeAction(dotsDown, isPortraitLayout());
            if (swipe != Swipe.NONE) {
                actionHandler.handleSwipe(getContext(), swipe);
            } else {
                handleTypedCharacter();
            }
        }
    }

    // With the classic gestures, swiping up with three fingers opens the
    // keyboard menu, as with TalkBack's. Returns true if it was that gesture.
    private boolean handleClassicMenuGesture() {
        byte[] directions = getDotDirections();
        if (TalkBackGesture.classify(directions)
                != TalkBackGesture.Action.HELP_AND_OTHER_ACTIONS) {
            return false;
        }
        actionHandler.handleTalkBackAction(getContext(),
                TalkBackGesture.Action.HELP_AND_OTHER_ACTIONS, directions);
        return true;
    }

    // Types the pressed dots, or performs the TalkBack gesture if any finger
    // swiped.
    private void handleTalkBackGesture() {
        byte[] directions = getDotDirections();
        if (TalkBackGesture.countSwipingFingers(directions) == 0) {
            handleTypedCharacter();
            return;
        }
        actionHandler.handleTalkBackAction(getContext(),
                TalkBackGesture.classify(directions), directions);
    }

    private void handleTypedCharacter() {
        byte value = pressedDotString();
        actionHandler.handleCharacter(getContext(), value);
    }

    private boolean setPrivacy() {
        if (Options.getBooleanPreference(
                getContext(),
                R.string.pref_privacy_key,
                Boolean.parseBoolean(getContext().getString(
                        R.string.pref_privacy_default)))) {
            setBackgroundColor(getContext().getResources().getColor(
                    android.R.color.black));
            return true;
        } else {
            setBackgroundColor(getContext().getResources().getColor(
                    android.R.color.transparent));
            return false;
        }
    }

    private void setDisplayParams(int w, int h) {
        final int CIRCLE_RADIUS = 40;
        final int STROKE_WIDTH = 8;
        final int TEXT_SIZE = 20;
        int strokeWidth = (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, STROKE_WIDTH, getContext()
                        .getResources().getDisplayMetrics());
        int textSize = (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, TEXT_SIZE, getContext()
                        .getResources().getDisplayMetrics());
        int radius = (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, CIRCLE_RADIUS, getContext()
                        .getResources().getDisplayMetrics());
        boolean autoRotate = Options.getBooleanPreference(
                getContext(),
                R.string.pref_auto_rotate_keyboard_key,
                Boolean.parseBoolean(getContext().getString(
                        R.string.pref_auto_rotate_keyboard_default)));
        boolean forceLock = Options.getBooleanPreference(
                getContext(),
                R.string.pref_force_orientation_lock_key,
                Boolean.parseBoolean(getContext().getString(
                        R.string.pref_force_orientation_lock_default)));
        displayParams = new DisplayParams(strokeWidth, textSize, radius,
                autoRotate, forceLock);
        paint.setColor(getContext().getResources().getColor(
                android.R.color.black));
        paint.setTextSize(displayParams.textSize);
        paint.setAntiAlias(true);
        paint.setTextAlign(Paint.Align.CENTER);
        circlePaint.setColor(getContext().getResources().getColor(
                android.R.color.black));
        circlePaint.setAntiAlias(true);
        circlePaint.setStyle(Style.STROKE);
        circlePaint.setStrokeWidth(displayParams.strokeWidth);

        FontMetrics metrics = paint.getFontMetrics();
        float height = Math.abs(metrics.top - metrics.bottom);
        displayParams.x = getWidth() / 2;
        displayParams.y = (getHeight() / 2) + (height / 2);
    }

    private boolean handleVoiceInput() {
        if (System.currentTimeMillis() > requiredTouchTime
                && countDotsDown(dotsDown) == 1
                && Options.getBooleanPreference(
                        getContext(),
                        R.string.pref_voice_shortcut_key,
                        Boolean.parseBoolean(getContext().getString(
                                R.string.pref_voice_shortcut_default)))) {
            actionHandler.doVoiceInput(getContext(), false);
            return true;
        }
        return false;
    }

    private static class DisplayParams {
        public final int strokeWidth;
        public final int textSize;
        public final int radius;
        public final boolean autoRotate;
        // The keyboard stays fixed to the device whichever way the screen
        // turns, overriding autoRotate.
        public final boolean forceLock;

        public float x;
        public float y;

        public DisplayParams(int strokeWidth, int textSize, int radius,
                boolean autoRotate, boolean forceLock) {
            this.strokeWidth = strokeWidth;
            this.textSize = textSize;
            this.radius = radius;
            this.autoRotate = autoRotate;
            this.forceLock = forceLock;
        }
    }
}

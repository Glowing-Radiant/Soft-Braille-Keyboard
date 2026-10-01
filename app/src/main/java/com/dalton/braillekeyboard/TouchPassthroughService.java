/*
 * Copyright (C) 2026 The Soft Braille Keyboard Authors
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

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.annotation.TargetApi;
import android.content.Intent;
import android.graphics.Rect;
import android.graphics.Region;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;

import java.util.List;

/**
 * Lets the Braille keyboard receive raw multi-touch input while a screen
 * reader such as TalkBack has explore by touch turned on.
 *
 * Explore by touch normally turns a single finger into hover events and
 * swallows multi-finger touches as screen reader gestures, which makes six
 * finger Braille entry impossible. From Android 11 an accessibility service
 * can ask the system to pass touches that start inside a region of the screen
 * straight through to the app underneath. This service does nothing except
 * set that region to the bounds of the expanded keyboard while it is showing
 * and clear it as soon as the keyboard hides. It doesn't request touch
 * exploration, listen to accessibility events or read window content, so the
 * screen reader keeps working normally everywhere else.
 *
 * The keyboard (running in the same process) drives it through
 * {@link #setKeyboardRegion(int, Rect)} and {@link #clearKeyboardRegion()}.
 *
 * There is only one passthrough region per display, shared by all
 * accessibility services. Some screen readers, such as Jieshuo, set it
 * themselves or restart touch exploration at runtime. So while the keyboard is
 * shown the region is set again when touch exploration or the enabled
 * services change, and when the keyboard receives an explore by touch hover
 * event, which shows the region was lost. Jieshuo replaces it after every
 * touch the keyboard handles, so while Jieshuo is on it is also set again
 * many times after each touch.
 */
public class TouchPassthroughService extends AccessibilityService {
    private static final String TAG = "TouchPassthrough";
    // Jieshuo's packages, such as com.nirenr.talkman.
    private static final String JIESHUO_PACKAGE_PREFIX = "com.nirenr.";
    // Touch exploration restarts asynchronously, so set the region again a
    // little later too.
    private static final long REASSERT_DELAY_MS = 300;
    // Jieshuo replaces the region at some point in the first few dozen
    // milliseconds after each touch ends, and sometimes again seconds later,
    // so while it is on the region is set again at these times after each
    // touch ends. Fast typists start the next touch 50 to 150 ms after
    // lifting their fingers, hence every 5 ms at first.
    private static final long[] AFTER_TOUCH_DELAYS_MS = { 5, 10, 15, 20, 25,
            30, 35, 40, 45, 50, 60, 70, 80, 100, 150, 250, 400, 700, 1000,
            1500, 2500, 4000 };

    private static final Handler mainHandler = new Handler(
            Looper.getMainLooper());
    // Setting a region blocks for a millisecond or more, and with Jieshuo it
    // is set many times after each touch, so it is done off the keyboard's
    // main thread.
    private static Handler regionHandler;

    // All static state is only touched on the main thread.
    private static TouchPassthroughService instance;
    // The region the keyboard wants, applied when the service connects.
    private static int pendingDisplayId = -1;
    private static Rect pendingBounds;
    // Whether the Jieshuo screen reader is on, updated when the enabled
    // services change.
    private static boolean jieshuoOn;
    // The region currently applied to the system.
    private static int appliedDisplayId = -1;
    private static Rect appliedBounds;

    private static final Runnable reassertRunnable = new Runnable() {
        @Override
        public void run() {
            reassert();
        }
    };

    private static int afterTouchStep;
    private static final Runnable afterTouchRunnable = new Runnable() {
        @Override
        public void run() {
            reassert();
            if (++afterTouchStep < AFTER_TOUCH_DELAYS_MS.length) {
                mainHandler.postDelayed(this,
                        AFTER_TOUCH_DELAYS_MS[afterTouchStep]
                                - AFTER_TOUCH_DELAYS_MS[afterTouchStep - 1]);
            }
        }
    };

    private AccessibilityManager accessibilityManager;
    private final AccessibilityManager.TouchExplorationStateChangeListener touchExplorationListener = new AccessibilityManager.TouchExplorationStateChangeListener() {
        @Override
        public void onTouchExplorationStateChanged(boolean enabled) {
            updateJieshuoOn();
            reassertSoon();
        }
    };
    private Object servicesListener; // AccessibilityServicesStateChangeListener

    /**
     * Returns true if this device supports touch passthrough regions.
     */
    public static boolean isSupported() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.R;
    }

    /**
     * Returns true if the user has enabled this service and it is connected.
     */
    public static boolean isRunning() {
        return instance != null;
    }

    /**
     * Pass touches inside the given screen bounds straight through to the
     * keyboard. Replaces any previously set region.
     */
    public static void setKeyboardRegion(final int displayId, Rect bounds) {
        final Rect copy = new Rect(bounds);
        runOnMainThread(new Runnable() {
            @Override
            public void run() {
                pendingDisplayId = displayId;
                pendingBounds = copy;
                apply();
            }
        });
    }

    /**
     * Stop passing touches through, restoring normal screen reader behaviour.
     */
    public static void clearKeyboardRegion() {
        runOnMainThread(new Runnable() {
            @Override
            public void run() {
                pendingDisplayId = -1;
                pendingBounds = null;
                mainHandler.removeCallbacks(reassertRunnable);
                mainHandler.removeCallbacks(afterTouchRunnable);
                apply();
            }
        });
    }

    /**
     * Sets the keyboard's region again, now and shortly after, in case
     * another accessibility service replaced it or touch exploration
     * restarted. Does nothing when the keyboard isn't shown.
     */
    public static void reassertSoon() {
        runOnMainThread(new Runnable() {
            @Override
            public void run() {
                reassert();
                mainHandler.removeCallbacks(reassertRunnable);
                mainHandler.postDelayed(reassertRunnable, REASSERT_DELAY_MS);
            }
        });
    }

    /**
     * Call when the keyboard handled a touch. While Jieshuo is on, sets the
     * keyboard's region again several times over the next moments. Does
     * nothing otherwise, or when the keyboard isn't shown.
     */
    public static void reassertAfterTouch() {
        if (!jieshuoOn) {
            return;
        }
        runOnMainThread(new Runnable() {
            @Override
            public void run() {
                mainHandler.removeCallbacks(afterTouchRunnable);
                afterTouchStep = 0;
                mainHandler.postDelayed(afterTouchRunnable,
                        AFTER_TOUCH_DELAYS_MS[0]);
            }
        });
    }

    private static void reassert() {
        TouchPassthroughService service = instance;
        if (service != null && pendingDisplayId != -1) {
            service.setRegion(pendingDisplayId, pendingBounds);
            appliedDisplayId = pendingDisplayId;
            appliedBounds = pendingBounds;
        }
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        appliedDisplayId = -1;
        appliedBounds = null;
        apply();
        registerListeners();
        updateJieshuoOn();
    }

    private void updateJieshuoOn() {
        boolean on = false;
        if (accessibilityManager != null) {
            List<AccessibilityServiceInfo> services = accessibilityManager
                    .getEnabledAccessibilityServiceList(
                            AccessibilityServiceInfo.FEEDBACK_ALL_MASK);
            for (AccessibilityServiceInfo service : services) {
                String id = service.getId();
                if (id != null && id.startsWith(JIESHUO_PACKAGE_PREFIX)) {
                    on = true;
                    break;
                }
            }
        }
        if (BuildConfig.DEBUG && on != jieshuoOn) {
            Log.d(TAG, "Jieshuo on: " + on);
        }
        jieshuoOn = on;
    }

    @TargetApi(Build.VERSION_CODES.TIRAMISU)
    private void registerListeners() {
        accessibilityManager = (AccessibilityManager) getSystemService(ACCESSIBILITY_SERVICE);
        accessibilityManager
                .addTouchExplorationStateChangeListener(touchExplorationListener);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            AccessibilityManager.AccessibilityServicesStateChangeListener listener = new AccessibilityManager.AccessibilityServicesStateChangeListener() {
                @Override
                public void onAccessibilityServicesStateChanged(
                        AccessibilityManager manager) {
                    updateJieshuoOn();
                    reassertSoon();
                }
            };
            accessibilityManager.addAccessibilityServicesStateChangeListener(
                    getMainExecutor(), listener);
            servicesListener = listener;
        }
    }

    @TargetApi(Build.VERSION_CODES.TIRAMISU)
    private void unregisterListeners() {
        if (accessibilityManager == null) {
            return;
        }
        accessibilityManager
                .removeTouchExplorationStateChangeListener(touchExplorationListener);
        if (servicesListener != null
                && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            accessibilityManager
                    .removeAccessibilityServicesStateChangeListener((AccessibilityManager.AccessibilityServicesStateChangeListener) servicesListener);
            servicesListener = null;
        }
        accessibilityManager = null;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        release();
        return super.onUnbind(intent);
    }

    @Override
    public void onDestroy() {
        release();
        super.onDestroy();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // This service doesn't subscribe to any events.
    }

    @Override
    public void onInterrupt() {
    }

    private void release() {
        unregisterListeners();
        if (instance == this) {
            setRegion(appliedDisplayId, null);
            appliedDisplayId = -1;
            appliedBounds = null;
            instance = null;
        }
    }

    // Brings the system passthrough region in line with what the keyboard
    // last asked for.
    private static void apply() {
        TouchPassthroughService service = instance;
        if (service == null) {
            return;
        }
        if (pendingDisplayId == appliedDisplayId
                && (pendingBounds == null ? appliedBounds == null
                        : pendingBounds.equals(appliedBounds))) {
            return;
        }
        if (appliedDisplayId != -1 && appliedDisplayId != pendingDisplayId) {
            // The keyboard moved to another display, clear the old one.
            service.setRegion(appliedDisplayId, null);
        }
        if (pendingDisplayId != -1) {
            service.setRegion(pendingDisplayId, pendingBounds);
        } else if (appliedDisplayId != -1) {
            service.setRegion(appliedDisplayId, null);
        }
        appliedDisplayId = pendingDisplayId;
        appliedBounds = pendingBounds;
    }

    // Sets both passthrough regions on the display, in order on a background
    // thread. A null or empty bounds clears them.
    private void setRegion(final int displayId, Rect bounds) {
        if (!isSupported() || displayId == -1) {
            return;
        }
        final Region region = bounds != null ? new Region(bounds)
                : new Region();
        if (regionHandler == null) {
            HandlerThread thread = new HandlerThread(TAG);
            thread.start();
            regionHandler = new Handler(thread.getLooper());
        }
        regionHandler.post(new Runnable() {
            @Override
            public void run() {
                applyRegion(displayId, region);
            }
        });
    }

    @TargetApi(Build.VERSION_CODES.R)
    private void applyRegion(int displayId, Region region) {
        try {
            // Touches starting in the region skip the touch explorer...
            setTouchExplorationPassthroughRegion(displayId, region);
            // ...and the screen reader's own gesture detection.
            setGestureDetectionPassthroughRegion(displayId, region);
        } catch (RuntimeException e) {
            // The connection to the system can go away while unbinding.
            Log.w(TAG, "Failed to set passthrough region", e);
        }
    }

    private static void runOnMainThread(Runnable runnable) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            runnable.run();
        } else {
            mainHandler.post(runnable);
        }
    }
}

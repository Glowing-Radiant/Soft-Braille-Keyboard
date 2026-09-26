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
import android.annotation.TargetApi;
import android.content.Intent;
import android.graphics.Rect;
import android.graphics.Region;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;

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
 */
public class TouchPassthroughService extends AccessibilityService {
    private static final String TAG = "TouchPassthrough";

    private static final Handler mainHandler = new Handler(
            Looper.getMainLooper());

    // All static state is only touched on the main thread.
    private static TouchPassthroughService instance;
    // The region the keyboard wants, applied when the service connects.
    private static int pendingDisplayId = -1;
    private static Rect pendingBounds;
    // The region currently applied to the system.
    private static int appliedDisplayId = -1;
    private static Rect appliedBounds;

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
                apply();
            }
        });
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        appliedDisplayId = -1;
        appliedBounds = null;
        apply();
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

    // Sets both passthrough regions on the display. A null or empty bounds
    // clears them.
    @TargetApi(Build.VERSION_CODES.R)
    private void setRegion(int displayId, Rect bounds) {
        if (!isSupported() || displayId == -1) {
            return;
        }
        Region region = bounds != null ? new Region(bounds) : new Region();
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

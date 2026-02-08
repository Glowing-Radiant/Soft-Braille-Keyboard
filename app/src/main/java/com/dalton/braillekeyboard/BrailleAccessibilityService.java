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

import android.accessibilityservice.AccessibilityService;
import android.view.accessibility.AccessibilityEvent;
import android.util.Log;

/**
 * Accessibility service for Soft Braille Keyboard.
 * 
 * This service allows the Braille keyboard to overlay on top of TalkBack's
 * explore by touch functionality. When enabled, it provides better integration
 * with screen readers and allows users to use the Braille keyboard without
 * conflicts with TalkBack's touch exploration.
 * 
 * The service intercepts touch events when the Braille keyboard is active,
 * preventing TalkBack from interfering with multi-touch Braille input.
 */
public class BrailleAccessibilityService extends AccessibilityService {
    private static final String TAG = "BrailleA11yService";
    
    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // Handle accessibility events
        // This method is called when an accessibility event occurs
        if (event == null) {
            return;
        }
        
        // Log events for debugging
        if (Options.getBooleanPreference(this,
                R.string.pref_debug_mode_key, false)) {
            Log.d(TAG, "Accessibility event: " + event.toString());
        }
        
        // We primarily use this service for touch exploration override
        // The actual keyboard input is handled by BrailleIME
    }
    
    @Override
    public void onInterrupt() {
        // Called when the system wants to interrupt the feedback this service is providing
        Log.d(TAG, "Accessibility service interrupted");
    }
    
    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        Log.d(TAG, "Braille Accessibility Service connected");
        
        // The service is now ready and can receive accessibility events
        // Configuration is defined in the accessibility_service_config.xml
    }
    
    @Override
    public boolean onUnbind(android.content.Intent intent) {
        Log.d(TAG, "Braille Accessibility Service disconnected");
        return super.onUnbind(intent);
    }
}

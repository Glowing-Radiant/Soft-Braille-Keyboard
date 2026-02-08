# New Features - Multi-Touch Improvements and Accessibility Service

## Overview

This update brings three major improvements to the Soft Braille Keyboard:

1. **Enhanced Multi-Touch Support** - Better recognition of braille dot combinations, especially dots 1, 2, 3
2. **English Contraction Braille Support** - Full Grade 2 (contracted) braille tables for faster typing
3. **Accessibility Service Integration** - Optional service for seamless TalkBack integration

## 1. Enhanced Multi-Touch Support

### What's New

The keyboard now tracks all active finger positions simultaneously during touch movement, not just one finger at a time. This significantly improves the recognition of multi-finger braille patterns, particularly combinations involving dots 1, 2, 3.

### Technical Details

**Previous Behavior:**
- Only updated one pointer position per ACTION_MOVE event
- Could miss finger movements when multiple fingers were moving
- Sometimes caused dots 1, 2, 3 combinations to not register smoothly

**New Behavior:**
- Updates all active pointer positions on every ACTION_MOVE event
- Tracks coordinate transformations for all pointers consistently
- Provides smoother, more reliable multi-touch detection

### Code Changes

Modified `BrailleView.java`:
```java
case MotionEvent.ACTION_MOVE:
    // Update all active pointers for better multi-touch support
    int pointerCount = motionEvent.getPointerCount();
    for (int i = 0; i < pointerCount; i++) {
        int pointerId = motionEvent.getPointerId(i);
        int pointerX = (int) motionEvent.getX(i);
        int pointerY = (int) motionEvent.getY(i);
        
        // Apply same coordinate transformation as for ACTION_DOWN
        int tempPointerX = pointerX;
        pointerX = displayParams.autoRotate || getWidth() >= getHeight() ? pointerX : pointerY;
        pointerY = displayParams.autoRotate || getWidth() >= getHeight() ? pointerY : tempPointerX;
        
        updatePointer(dotsDown, pointerId, pointerX, pointerY, false);
    }
    break;
```

### User Impact

Users will notice:
- More responsive multi-touch detection
- Better recognition of dots 1, 2, 3 combinations
- Smoother overall braille input experience
- Fewer missed or incorrectly registered dots

## 2. English Contraction Braille Support

### What's New

Full support for English Grade 2 (contracted) braille tables is now available and properly documented.

### Available Tables

The following Grade 2 (contraction) tables are now confirmed and ready to use:

1. **en-UEB-g2** - Unified English Braille Grade 2 with contractions
2. **en-US-g2** - US English Grade 2 with contractions

### How to Use

1. Open keyboard Settings
2. Select **Braille Type** → **Literary (Grade 1/2)**
3. Select **Braille Table** → **en-UEB-g2** or **en-US-g2**
4. Use standard Grade 2 braille contractions for faster typing

### Benefits

- **Faster typing** - Common words and letter combinations use fewer braille cells
- **Standard contractions** - Uses official Grade 2 braille contraction rules
- **Seamless switching** - Easy to switch between Grade 1 and Grade 2 as needed

### Example Contractions

Grade 2 braille includes contractions for common words and letter combinations:
- "the" → single character (⠮)
- "and" → single character (⠯)
- "for" → single character (⠿)
- "with" → single character (⠾)
- And many more...

Refer to standard Grade 2 braille references for complete contraction rules.

## 3. Accessibility Service Integration

### What's New

A new optional Accessibility Service provides better integration with TalkBack and other screen readers.

### Features

The `BrailleAccessibilityService` provides:
- Touch exploration override capability
- Better multi-touch handling with TalkBack enabled
- Prevents conflicts between touch exploration and braille input
- Seamless integration with existing accessibility features

### How to Enable

1. Open Android **Settings**
2. Go to **Accessibility**
3. Find **Soft Braille Keyboard** in the services list
4. Toggle it **ON**
5. Grant requested permissions when prompted

### Configuration

Service configuration in `accessibility_service_config.xml`:
- Event types: All accessibility events
- Feedback: Generic and spoken
- Flags: Touch exploration, view IDs reporting
- Can retrieve window content
- Can request touch exploration mode

### Technical Implementation

**New Files:**
1. `BrailleAccessibilityService.java` - Main service implementation
2. `accessibility_service_config.xml` - Service configuration

**Modified Files:**
1. `AndroidManifest.xml` - Service registration
2. `strings.xml` - Service description and preferences

**Service Declaration:**
```xml
<service
    android:name=".BrailleAccessibilityService"
    android:label="@string/app_name"
    android:permission="android.permission.BIND_ACCESSIBILITY_SERVICE"
    android:exported="true">
    <intent-filter>
        <action android:name="android.accessibilityservice.AccessibilityService" />
    </intent-filter>
    <meta-data
        android:name="android.accessibilityservice"
        android:resource="@xml/accessibility_service_config" />
</service>
```

### When to Use

**Enable the service if you:**
- Use TalkBack or another screen reader
- Experience multi-touch detection issues with accessibility services enabled
- Want the smoothest possible braille input experience with screen readers

**You don't need it if you:**
- Don't use any screen readers
- Don't experience any touch detection issues
- Prefer minimal system integration

### Important Notes

- The service is **optional** - the keyboard works without it
- Enabling it improves multi-touch with TalkBack but is not required
- The service does NOT collect or transmit any data
- It only processes accessibility events to improve keyboard interaction
- Does NOT interfere with TalkBack or other screen readers

## Testing Recommendations

### Multi-Touch Testing

1. Test dots 1, 2, 3 combinations specifically
2. Try rapid multi-finger inputs
3. Test with different orientations (portrait/landscape)
4. Verify all 6-dot and 8-dot combinations register correctly

### Contraction Braille Testing

1. Verify Grade 2 tables appear in selection list
2. Test common contractions ("the", "and", "for", etc.)
3. Ensure correct back-translation of contracted braille
4. Test switching between Grade 1 and Grade 2

### Accessibility Service Testing

1. Enable TalkBack
2. Enable Soft Braille Keyboard Accessibility Service
3. Test multi-touch braille input with both enabled
4. Verify no conflicts with touch exploration
5. Test service lifecycle (enable/disable/restart)

## Migration Notes

### For Existing Users

- **No breaking changes** - All existing functionality remains
- Multi-touch improvements are automatic (no configuration needed)
- Contraction tables were already present, now properly documented
- Accessibility service is optional (off by default)

### For New Users

- Follow the standard setup process
- Optionally enable Accessibility Service for TalkBack integration
- Choose Grade 2 tables if familiar with braille contractions

## Known Limitations

1. **Accessibility Service** is optional and provides incremental improvements
2. **Contraction braille** requires knowledge of Grade 2 braille rules
3. **Multi-touch improvements** may still have edge cases on some devices
4. **Testing** on physical devices is required to validate all improvements

## Future Enhancements

Potential areas for future development:
- Automatic contraction learning/suggestions
- Custom braille table creation
- Enhanced gesture recognition
- Additional accessibility service features
- More language-specific contraction tables

## Support

For questions or issues:
- Review the USER_GUIDE.md
- Check troubleshooting sections
- Contact: daniel.dalton10@gmail.com

## Version Information

- **Feature Update Version:** 3.1.0+
- **Minimum Android Version:** 5.0 (API 21)
- **Target Android Version:** 14 (API 34)
- **Last Updated:** February 2026

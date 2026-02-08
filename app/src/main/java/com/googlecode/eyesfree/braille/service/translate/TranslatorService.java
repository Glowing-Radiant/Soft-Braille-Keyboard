package com.googlecode.eyesfree.braille.service.translate;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

/**
 * Stub service for braille translation.
 * Note: Full braille translation requires native library support.
 */
public class TranslatorService extends Service {
    
    @Override
    public IBinder onBind(Intent intent) {
        // Stub implementation
        return null;
    }
    
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_NOT_STICKY;
    }
}

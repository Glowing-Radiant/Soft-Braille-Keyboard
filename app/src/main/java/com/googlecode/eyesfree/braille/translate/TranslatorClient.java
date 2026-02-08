package com.googlecode.eyesfree.braille.translate;

import android.content.Context;
import android.os.Handler;

import java.util.ArrayList;
import java.util.List;

public class TranslatorClient {
    
    // Status constants
    public static final int SUCCESS = 0;
    public static final int ERROR = -1;
    
    public interface OnInitListener {
        void onInit(int status);
    }
    
    public TranslatorClient(Context context, boolean unused) {
        // Stub implementation
    }
    
    public void setOnInitListener(OnInitListener listener) {
        // Stub: call listener immediately with success
        if (listener != null) {
            new Handler().post(new Runnable() {
                @Override
                public void run() {
                    listener.onInit(SUCCESS);
                }
            });
        }
    }
    
    public BrailleTranslator getTranslator(TableInfo table) {
        // Return a stub translator
        return new StubBrailleTranslator();
    }
    
    public List<TableInfo> getTables() {
        List<TableInfo> tables = new ArrayList<>();
        tables.add(new TableInfo("en-us-comp8"));
        return tables;
    }
    
    public void destroy() {
        // Stub
    }
    
    private static class StubBrailleTranslator implements BrailleTranslator {
        @Override
        public String backTranslate(byte[] cells) {
            // Simple stub implementation
            if (cells == null) return "";
            StringBuilder sb = new StringBuilder();
            for (byte cell : cells) {
                // Basic ASCII mapping
                if (cell >= 'a' && cell <= 'z') {
                    sb.append((char)cell);
                } else {
                    sb.append('?');
                }
            }
            return sb.toString();
        }
        
        @Override
        public byte[] translate(String text) {
            // Simple stub implementation
            if (text == null) return new byte[0];
            return text.getBytes();
        }
        
        @Override
        public void destroy() {
            // Stub
        }
    }
}

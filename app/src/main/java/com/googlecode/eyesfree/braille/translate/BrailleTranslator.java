package com.googlecode.eyesfree.braille.translate;

public interface BrailleTranslator {
    String backTranslate(byte[] cells);
    byte[] translate(String text);
    void destroy();
}

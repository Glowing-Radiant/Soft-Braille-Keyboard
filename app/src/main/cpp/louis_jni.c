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

/*
 * JNI bridge between com.googlecode.eyesfree.braille.translate.LibLouis and
 * liblouis. liblouis is not thread safe; the Java side serialises all calls.
 */

#include <jni.h>
#include <stdlib.h>
#include <android/log.h>

#include "liblouis.h"

#define LOG_TAG "LibLouis"

static void logCallback(logLevels level, const char *message) {
    int priority;
    if (level >= LOU_LOG_ERROR) {
        priority = ANDROID_LOG_ERROR;
    } else if (level >= LOU_LOG_WARN) {
        priority = ANDROID_LOG_WARN;
    } else {
        priority = ANDROID_LOG_DEBUG;
    }
    __android_log_write(priority, LOG_TAG, message);
}

JNIEXPORT void JNICALL
Java_com_googlecode_eyesfree_braille_translate_LibLouis_nativeInit(
        JNIEnv *env, jclass clazz, jstring dataPath) {
    const char *path = (*env)->GetStringUTFChars(env, dataPath, NULL);
    if (path == NULL) {
        return;
    }
    lou_registerLogCallback(logCallback);
    lou_setLogLevel(LOU_LOG_WARN);
    lou_setDataPath(path);
    (*env)->ReleaseStringUTFChars(env, dataPath, path);
}

JNIEXPORT jboolean JNICALL
Java_com_googlecode_eyesfree_braille_translate_LibLouis_nativeCheckTable(
        JNIEnv *env, jclass clazz, jstring tableList) {
    const char *table = (*env)->GetStringUTFChars(env, tableList, NULL);
    if (table == NULL) {
        return JNI_FALSE;
    }
    int ok = lou_getTable(table) != NULL;
    (*env)->ReleaseStringUTFChars(env, tableList, table);
    return ok ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jstring JNICALL
Java_com_googlecode_eyesfree_braille_translate_LibLouis_nativeBackTranslate(
        JNIEnv *env, jclass clazz, jstring tableList, jbyteArray cells) {
    jsize inLength = (*env)->GetArrayLength(env, cells);
    jbyte *cellBytes = (*env)->GetByteArrayElements(env, cells, NULL);
    if (cellBytes == NULL) {
        return NULL;
    }

    // Each cell is a bit field with dot 1 in the least significant bit, which
    // is the same layout liblouis uses for dot patterns.
    widechar *inbuf = malloc(sizeof(widechar) * (inLength + 1));
    // Contracted braille can expand considerably when back translated.
    int outCapacity = inLength * 16 + 64;
    widechar *outbuf = malloc(sizeof(widechar) * outCapacity);
    jstring result = NULL;

    if (inbuf != NULL && outbuf != NULL) {
        for (jsize i = 0; i < inLength; i++) {
            inbuf[i] = LOU_DOTS | ((unsigned char) cellBytes[i]);
        }
        inbuf[inLength] = 0;

        const char *table = (*env)->GetStringUTFChars(env, tableList, NULL);
        if (table != NULL) {
            int inlen = inLength;
            int outlen = outCapacity;
            if (lou_backTranslateString(table, inbuf, &inlen, outbuf, &outlen,
                    NULL, NULL, dotsIO)) {
                result = (*env)->NewString(env, outbuf, outlen);
            }
            (*env)->ReleaseStringUTFChars(env, tableList, table);
        }
    }

    free(inbuf);
    free(outbuf);
    (*env)->ReleaseByteArrayElements(env, cells, cellBytes, JNI_ABORT);
    return result;
}

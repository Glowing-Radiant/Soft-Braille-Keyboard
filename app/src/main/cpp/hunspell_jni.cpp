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
 * JNI bridge between com.dalton.braillekeyboard.Hunspell and Hunspell.
 * Words are passed as bytes in the dictionary's own encoding, which the Java
 * side converts to and from. A Hunspell object is not thread safe; the Java
 * side serialises calls on each one.
 */

#include <jni.h>
#include <string>
#include <vector>

#include "hunspell.hxx"

static std::string toString(JNIEnv *env, jbyteArray bytes) {
    jsize length = env->GetArrayLength(bytes);
    std::string text(length, '\0');
    env->GetByteArrayRegion(bytes, 0, length,
            reinterpret_cast<jbyte *>(&text[0]));
    return text;
}

static jbyteArray toBytes(JNIEnv *env, const std::string &text) {
    jbyteArray bytes = env->NewByteArray(text.size());
    if (bytes != nullptr) {
        env->SetByteArrayRegion(bytes, 0, text.size(),
                reinterpret_cast<const jbyte *>(text.data()));
    }
    return bytes;
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_dalton_braillekeyboard_Hunspell_nativeOpen(
        JNIEnv *env, jclass, jstring affPath, jstring dicPath) {
    const char *aff = env->GetStringUTFChars(affPath, nullptr);
    const char *dic = env->GetStringUTFChars(dicPath, nullptr);
    Hunspell *hunspell = nullptr;
    if (aff != nullptr && dic != nullptr) {
        hunspell = new Hunspell(aff, dic);
    }
    if (aff != nullptr) {
        env->ReleaseStringUTFChars(affPath, aff);
    }
    if (dic != nullptr) {
        env->ReleaseStringUTFChars(dicPath, dic);
    }
    return reinterpret_cast<jlong>(hunspell);
}

extern "C" JNIEXPORT void JNICALL
Java_com_dalton_braillekeyboard_Hunspell_nativeClose(
        JNIEnv *, jclass, jlong handle) {
    delete reinterpret_cast<Hunspell *>(handle);
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_dalton_braillekeyboard_Hunspell_nativeGetEncoding(
        JNIEnv *env, jclass, jlong handle) {
    Hunspell *hunspell = reinterpret_cast<Hunspell *>(handle);
    return env->NewStringUTF(hunspell->get_dict_encoding().c_str());
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_dalton_braillekeyboard_Hunspell_nativeSpell(
        JNIEnv *env, jclass, jlong handle, jbyteArray word) {
    Hunspell *hunspell = reinterpret_cast<Hunspell *>(handle);
    return hunspell->spell(toString(env, word)) ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_com_dalton_braillekeyboard_Hunspell_nativeSuggest(
        JNIEnv *env, jclass, jlong handle, jbyteArray word) {
    Hunspell *hunspell = reinterpret_cast<Hunspell *>(handle);
    std::vector<std::string> suggestions = hunspell->suggest(
            toString(env, word));
    jclass byteArrayClass = env->FindClass("[B");
    jobjectArray result = env->NewObjectArray(suggestions.size(),
            byteArrayClass, nullptr);
    if (result == nullptr) {
        return nullptr;
    }
    for (size_t i = 0; i < suggestions.size(); i++) {
        jbyteArray bytes = toBytes(env, suggestions[i]);
        env->SetObjectArrayElement(result, i, bytes);
        env->DeleteLocalRef(bytes);
    }
    return result;
}

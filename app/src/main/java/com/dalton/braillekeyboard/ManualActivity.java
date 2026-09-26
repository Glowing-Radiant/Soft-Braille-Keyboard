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

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.io.IOException;
import java.util.Arrays;
import java.util.Locale;

/**
 * Shows the user guide, which is bundled with the app so it works offline.
 *
 * The guide is assets/manual/index.html. A translation can be added as
 * assets/manual/index-LANGUAGE.html, for example index-it.html, and is used
 * when the device language matches.
 */
public class ManualActivity extends Activity {
    /** Section of the guide describing the TalkBack gesture mode. */
    public static final String SECTION_TALKBACK_GESTURES = "talkback-gestures";

    private static final String EXTRA_SECTION = "section";
    private static final String MANUAL_DIR = "manual";

    private WebView webView;

    /**
     * Creates an intent opening the guide, optionally at a section.
     *
     * @param section
     *            The id of a heading in the guide or null for the start.
     */
    public static Intent createIntent(Context context, String section) {
        return new Intent(context, ManualActivity.class).putExtra(
                EXTRA_SECTION, section);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle(R.string.manual_title);
        webView = new WebView(this);
        setContentView(webView);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(false);
        settings.setAllowFileAccess(false);
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view,
                    WebResourceRequest request) {
                return openExternally(request.getUrl());
            }

            @Override
            @Deprecated
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return openExternally(Uri.parse(url));
            }
        });

        if (savedInstanceState != null) {
            webView.restoreState(savedInstanceState);
        } else {
            String url = "file:///android_asset/" + MANUAL_DIR + "/"
                    + manualFile();
            String section = getIntent().getStringExtra(EXTRA_SECTION);
            webView.loadUrl(section != null ? url + "#" + section : url);
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        webView.saveState(outState);
    }

    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    // Links within the guide stay in the WebView; web and email links open
    // in the appropriate app.
    private boolean openExternally(Uri uri) {
        if ("file".equals(uri.getScheme())) {
            return false;
        }
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, uri));
        } catch (ActivityNotFoundException e) {
            // Nothing can open it; stay on the guide.
        }
        return true;
    }

    // Picks a translation of the guide for the device language if there is
    // one.
    private String manualFile() {
        String translated = "index-" + Locale.getDefault().getLanguage()
                + ".html";
        try {
            String[] files = getAssets().list(MANUAL_DIR);
            if (files != null && Arrays.asList(files).contains(translated)) {
                return translated;
            }
        } catch (IOException e) {
            // Fall back to English.
        }
        return "index.html";
    }
}

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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SoundThemeImporterTest {

    @Test
    public void keepsSoundsAndTheThemeProperties() {
        assertTrue(SoundThemeImporter.keep("type.ogg"));
        assertTrue(SoundThemeImporter.keep("menu-open-2.wav"));
        assertTrue(SoundThemeImporter.keep("space.MP3"));
        assertTrue(SoundThemeImporter.keep("theme.properties"));
    }

    @Test
    public void skipsOtherFiles() {
        assertFalse(SoundThemeImporter.keep("readme.txt"));
        assertFalse(SoundThemeImporter.keep("type.txt"));
        assertFalse(SoundThemeImporter.keep("click.ogg"));
        assertFalse(SoundThemeImporter.keep("cover.png"));
    }

    @Test
    public void usesOnlyTheFileNameOfZipEntries() {
        assertEquals("type.ogg", SoundThemeImporter.baseName("my theme/Type.OGG"));
        assertEquals("space.wav", SoundThemeImporter.baseName("../../space.wav"));
        assertEquals("delete.ogg", SoundThemeImporter.baseName("a\\b\\delete.ogg"));
        assertEquals("theme.properties",
                SoundThemeImporter.baseName("x/Theme.Properties"));
    }
}

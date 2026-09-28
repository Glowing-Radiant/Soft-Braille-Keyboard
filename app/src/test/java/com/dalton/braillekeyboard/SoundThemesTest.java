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
import static org.junit.Assert.assertNull;

import com.dalton.braillekeyboard.Earcons.Sound;

import org.junit.Test;

public class SoundThemesTest {

    @Test
    public void namesFilesAfterTheirSound() {
        assertEquals(Sound.TYPE, SoundThemes.soundOf("type.ogg"));
        assertEquals(Sound.SPACE, SoundThemes.soundOf("Space.wav"));
        assertEquals(Sound.MENU_OPEN, SoundThemes.soundOf("menu-open.ogg"));
    }

    @Test
    public void numberedFilesAreVariations() {
        assertEquals(Sound.TYPE, SoundThemes.soundOf("type-1.ogg"));
        assertEquals(Sound.TYPE, SoundThemes.soundOf("type-12.ogg"));
        assertEquals(Sound.MENU_CLOSE, SoundThemes.soundOf("menu-close-2.ogg"));
    }

    @Test
    public void ignoresOtherFiles() {
        assertNull(SoundThemes.soundOf("theme.properties"));
        assertNull(SoundThemes.soundOf("typing.ogg"));
        assertNull(SoundThemes.soundOf("type"));
    }
}

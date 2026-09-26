/*
 * Copyright (C) 2012 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.googlecode.eyesfree.braille.translate;

import java.util.Locale;

/**
 * Meta-data about a braille translation table, as declared in
 * res/xml/tablelist.xml.
 */
public class TableInfo {
    private final String id;
    private final Locale locale;
    private final boolean eightDot;
    private final int grade;
    private final String fileName;

    public TableInfo(String id, Locale locale, boolean eightDot, int grade,
            String fileName) {
        this.id = id;
        this.locale = locale;
        this.eightDot = eightDot;
        this.grade = grade;
        this.fileName = fileName;
    }

    /** Unique identifier of the table, stored in the preferences. */
    public String getId() {
        return id;
    }

    public Locale getLocale() {
        return locale;
    }

    public boolean isEightDot() {
        return eightDot;
    }

    /** Contraction grade, 0 for computer braille tables. */
    public int getGrade() {
        return grade;
    }

    /** The liblouis table file name. */
    public String getFileName() {
        return fileName;
    }

    @Override
    public String toString() {
        return id;
    }
}

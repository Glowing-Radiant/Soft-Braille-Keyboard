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

package com.googlecode.eyesfree.braille.display;

import android.os.Parcel;
import android.os.Parcelable;
import android.util.SparseArray;

import java.util.HashMap;

public class BrailleInputEvent implements Parcelable {

    public static final int CMD_NONE = -1;
    public static final int CMD_NAV_LINE_PREVIOUS = 1;
    public static final int CMD_NAV_LINE_NEXT = 2;
    public static final int CMD_NAV_ITEM_PREVIOUS = 3;
    public static final int CMD_NAV_ITEM_NEXT = 4;
    public static final int CMD_NAV_PAN_LEFT = 5;
    public static final int CMD_NAV_PAN_RIGHT = 6;
    public static final int CMD_NAV_TOP = 7;
    public static final int CMD_NAV_BOTTOM = 8;
    public static final int CMD_ACTIVATE_CURRENT = 20;
    public static final int CMD_LONG_PRESS_CURRENT = 21;
    public static final int CMD_SCROLL_BACKWARD = 30;
    public static final int CMD_SCROLL_FORWARD = 31;
    public static final int CMD_SELECTION_START = 40;
    public static final int CMD_SELECTION_END = 41;
    public static final int CMD_SELECTION_SELECT_ALL = 42;
    public static final int CMD_SELECTION_CUT = 43;
    public static final int CMD_SELECTION_COPY = 44;
    public static final int CMD_SELECTION_PASTE = 45;
    public static final int CMD_ROUTE = 50;
    public static final int CMD_LONG_PRESS_ROUTE = 51;
    public static final int CMD_BRAILLE_KEY = 60;
    public static final int CMD_KEY_ENTER = 70;
    public static final int CMD_KEY_DEL = 71;
    public static final int CMD_KEY_FORWARD_DEL = 72;
    public static final int CMD_GLOBAL_BACK = 90;
    public static final int CMD_GLOBAL_HOME = 91;
    public static final int CMD_GLOBAL_RECENTS = 92;
    public static final int CMD_GLOBAL_NOTIFICATIONS = 93;
    public static final int CMD_HELP = 100;
    public static final int CMD_TOGGLE_BRAILLE_MENU = 117;
    public static final int CMD_TOGGLE_BRAILLE_GRADE = 118;
    public static final int CMD_SECTION_NEXT = 110;
    public static final int CMD_SECTION_PREVIOUS = 111;
    public static final int CMD_CONTROL_NEXT = 112;
    public static final int CMD_CONTROL_PREVIOUS = 113;
    public static final int CMD_LIST_NEXT = 114;
    public static final int CMD_LIST_PREVIOUS = 115;
    public static final int CMD_TOGGLE_INCREMENTAL_SEARCH = 116;

    public static final int ARGUMENT_NONE = 0;
    public static final int ARGUMENT_DOTS = 1;
    public static final int ARGUMENT_POSITION = 2;

    private static final SparseArray<String> CMD_NAMES = new SparseArray<String>();
    private static final HashMap<String, Integer> NAMES_TO_CMDS = new HashMap<String, Integer>();
    static {
        CMD_NAMES.append(CMD_NAV_LINE_PREVIOUS, "CMD_NAV_LINE_PREVIOUS");
        CMD_NAMES.append(CMD_NAV_LINE_NEXT, "CMD_NAV_LINE_NEXT");
        CMD_NAMES.append(CMD_NAV_ITEM_PREVIOUS, "CMD_NAV_ITEM_PREVIOUS");
        CMD_NAMES.append(CMD_NAV_ITEM_NEXT, "CMD_NAV_ITEM_NEXT");
        CMD_NAMES.append(CMD_NAV_PAN_LEFT, "CMD_NAV_PAN_LEFT");
        CMD_NAMES.append(CMD_NAV_PAN_RIGHT, "CMD_NAV_PAN_RIGHT");
        CMD_NAMES.append(CMD_NAV_TOP, "CMD_NAV_TOP");
        CMD_NAMES.append(CMD_NAV_BOTTOM, "CMD_NAV_BOTTOM");
        CMD_NAMES.append(CMD_ACTIVATE_CURRENT, "CMD_ACTIVATE_CURRENT");
        CMD_NAMES.append(CMD_LONG_PRESS_CURRENT, "CMD_LONG_PRESS_CURRENT");
        CMD_NAMES.append(CMD_SCROLL_BACKWARD, "CMD_SCROLL_BACKWARD");
        CMD_NAMES.append(CMD_SCROLL_FORWARD, "CMD_SCROLL_FORWARD");
        CMD_NAMES.append(CMD_SELECTION_START, "CMD_SELECTION_START");
        CMD_NAMES.append(CMD_SELECTION_END, "CMD_SELECTION_END");
        CMD_NAMES.append(CMD_SELECTION_SELECT_ALL, "CMD_SELECTION_SELECT_ALL");
        CMD_NAMES.append(CMD_SELECTION_CUT, "CMD_SELECTION_CUT");
        CMD_NAMES.append(CMD_SELECTION_COPY, "CMD_SELECTION_COPY");
        CMD_NAMES.append(CMD_SELECTION_PASTE, "CMD_SELECTION_PASTE");
        CMD_NAMES.append(CMD_ROUTE, "CMD_ROUTE");
        CMD_NAMES.append(CMD_LONG_PRESS_ROUTE, "CMD_LONG_PRESS_ROUTE");
        CMD_NAMES.append(CMD_BRAILLE_KEY, "CMD_BRAILLE_KEY");
        CMD_NAMES.append(CMD_KEY_ENTER, "CMD_KEY_ENTER");
        CMD_NAMES.append(CMD_KEY_DEL, "CMD_KEY_DEL");
        CMD_NAMES.append(CMD_KEY_FORWARD_DEL, "CMD_KEY_FORWARD_DEL");
        CMD_NAMES.append(CMD_GLOBAL_BACK, "CMD_GLOBAL_BACK");
        CMD_NAMES.append(CMD_GLOBAL_HOME, "CMD_GLOBAL_HOME");
        CMD_NAMES.append(CMD_GLOBAL_RECENTS, "CMD_GLOBAL_RECENTS");
        CMD_NAMES.append(CMD_GLOBAL_NOTIFICATIONS, "CMD_GLOBAL_NOTIFICATIONS");
        CMD_NAMES.append(CMD_HELP, "CMD_HELP");
        CMD_NAMES.append(CMD_SECTION_NEXT, "CMD_SECTION_NEXT");
        CMD_NAMES.append(CMD_SECTION_PREVIOUS, "CMD_SECTION_PREVIOUS");
        CMD_NAMES.append(CMD_CONTROL_NEXT, "CMD_CONTROL_NEXT");
        CMD_NAMES.append(CMD_CONTROL_PREVIOUS, "CMD_CONTROL_PREVIOUS");
        CMD_NAMES.append(CMD_LIST_NEXT, "CMD_LIST_NEXT");
        CMD_NAMES.append(CMD_LIST_PREVIOUS, "CMD_LIST_PREVIOUS");
        CMD_NAMES.append(CMD_TOGGLE_INCREMENTAL_SEARCH, "CMD_TOGGLE_INCREMENTAL_SEARCH");
        CMD_NAMES.append(CMD_TOGGLE_BRAILLE_MENU, "CMD_TOGGLE_BRAILLE_MENU");
        CMD_NAMES.append(CMD_TOGGLE_BRAILLE_GRADE, "CMD_TOGGLE_BRAILLE_GRADE");
        for (int i = 0; i < CMD_NAMES.size(); ++i) {
            NAMES_TO_CMDS.put(CMD_NAMES.valueAt(i), CMD_NAMES.keyAt(i));
        }
    }

    private final int mCommand;
    private final int mArgument;
    private final long mEventTime;

    public BrailleInputEvent(int command, int argument, long eventTime) {
        mCommand = command;
        mArgument = argument;
        mEventTime = eventTime;
    }

    public int getCommand() {
        return mCommand;
    }

    public int getArgument() {
        return mArgument;
    }

    public long getEventTime() {
        return mEventTime;
    }

    public static String commandToString(int command) {
        String ret = CMD_NAMES.get(command);
        return ret != null ? ret : "(unknown)";
    }

    public static int stringToCommand(String commandName) {
        Integer command = NAMES_TO_CMDS.get(commandName);
        if (command == null) {
            return CMD_NONE;
        }
        return command;
    }

    public static int argumentType(int command) {
        switch (command) {
            case CMD_SELECTION_START:
            case CMD_SELECTION_END:
            case CMD_ROUTE:
            case CMD_LONG_PRESS_ROUTE:
                return ARGUMENT_POSITION;
            case CMD_BRAILLE_KEY:
                return ARGUMENT_DOTS;
            default:
                return ARGUMENT_NONE;
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("BrailleInputEvent {");
        sb.append("cmd=");
        sb.append(commandToString(mCommand));
        sb.append(", arg=");
        sb.append(mArgument);
        sb.append("}");
        return sb.toString();
    }

    public static final Parcelable.Creator<BrailleInputEvent> CREATOR =
        new Parcelable.Creator<BrailleInputEvent>() {
            @Override
            public BrailleInputEvent createFromParcel(Parcel in) {
                return new BrailleInputEvent(in);
            }

            @Override
            public BrailleInputEvent[] newArray(int size) {
                return new BrailleInputEvent[size];
            }
        };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel out, int flags) {
        out.writeInt(mCommand);
        out.writeInt(mArgument);
        out.writeLong(mEventTime);
    }

    private BrailleInputEvent(Parcel in) {
        mCommand = in.readInt();
        mArgument = in.readInt();
        mEventTime = in.readLong();
    }
}

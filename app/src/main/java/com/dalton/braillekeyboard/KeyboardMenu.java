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

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * The keyboard menu as a dialog over the keyboard, which screen reader users
 * explore like any other dialog. Choosing an action closes the menu and
 * returns to the keyboard before doing it, as does closing the menu. Settings
 * change in place: choosing one moves to its next value, and its values are
 * listed as accessibility actions and by a long press, to choose from.
 */
public class KeyboardMenu {

    /** The items of the menu and what they do. */
    public interface Model {
        /** The number of items. */
        int getCount();

        /** The name of an item. */
        String getTitle(int item);

        /** The values a setting can have, or null if the item is an action. */
        String[] getValues(int item);

        /** The position of a setting's value in getValues(), or -1. */
        int getValue(int item);

        /**
         * Changes a setting.
         *
         * @return A message for the user, or null to say the new value.
         */
        String setValue(int item, int value);

        /** Performs an action. The menu has closed by then. */
        void perform(int item);
    }

    /** Listener for the menu closing. */
    public interface OnCloseListener {
        /**
         * Called when the menu has closed.
         *
         * @param performing
         *            true if it closed to perform an action, which follows,
         *            rather than being closed by the user.
         */
        void onClose(boolean performing);
    }

    // The accessibility actions choosing a setting's values have ids from
    // this one up, one for each value.
    private static final int VALUE_ACTION_ID = R.id.menu_value;

    private final Context context;
    private final Model model;
    private OnCloseListener listener;
    private View keyboard;
    private AlertDialog dialog;
    private AlertDialog choices;
    // The action to perform once the menu has closed, or -1.
    private int pendingAction = -1;

    public KeyboardMenu(Context context, Model model,
            OnCloseListener listener) {
        this.context = context;
        this.model = model;
        this.listener = listener;
    }

    /**
     * Shows the menu over the keyboard.
     *
     * @param keyboard
     *            The keyboard's view, which must be attached to its window.
     */
    public void show(View keyboard) {
        this.keyboard = keyboard;
        AlertDialog.Builder builder = new AlertDialog.Builder(context,
                android.R.style.Theme_DeviceDefault_Dialog_Alert);
        // The items take the dialog's theme, not the keyboard's.
        Context themed = builder.getContext();
        LinearLayout items = new LinearLayout(themed);
        items.setOrientation(LinearLayout.VERTICAL);
        LayoutInflater inflater = LayoutInflater.from(themed);
        for (int i = 0; i < model.getCount(); i++) {
            View row = inflater.inflate(R.layout.menu_item, items, false);
            setUpRow(row, i);
            items.addView(row);
        }
        ScrollView scroll = new ScrollView(themed);
        scroll.addView(items);

        dialog = builder.setTitle(R.string.menu_title).setView(scroll)
                .setNegativeButton(R.string.menu_close, null).create();
        dialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface d) {
                closed();
            }
        });
        attach(dialog, keyboard);
        dialog.show();
        focus(items.getChildAt(0));
    }

    // Moves the screen reader to a view, as it doesn't go to a window that
    // doesn't take focus by itself.
    private static void focus(final View view) {
        if (view == null) {
            return;
        }
        view.post(new Runnable() {
            @Override
            public void run() {
                view.performAccessibilityAction(
                        AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS, null);
            }
        });
    }

    /**
     * Goes back from the list of a setting's values to the menu, or from the
     * menu to the keyboard. The menu doesn't take the editor's focus, so the
     * keyboard gets the back key.
     */
    public void back() {
        if (choices != null) {
            choices.dismiss();
        } else if (dialog != null) {
            dialog.dismiss();
        }
    }

    /**
     * Closes the menu without telling the listener, such as when the keyboard
     * closes.
     */
    public void dismiss() {
        listener = null;
        if (choices != null) {
            choices.dismiss();
        }
        if (dialog != null) {
            dialog.dismiss();
        }
    }

    private void closed() {
        if (choices != null) {
            choices.dismiss();
            choices = null;
        }
        OnCloseListener closeListener = listener;
        listener = null;
        int action = pendingAction;
        pendingAction = -1;
        if (closeListener != null) {
            closeListener.onClose(action != -1);
            if (action != -1) {
                model.perform(action);
            }
        }
    }

    private void setUpRow(final View row, final int item) {
        bindRow(row, item);
        row.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String[] values = model.getValues(item);
                if (values == null) {
                    pendingAction = item;
                    dialog.dismiss();
                } else {
                    step(row, item, 1);
                }
            }
        });
        row.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                if (model.getValues(item) == null) {
                    return false;
                }
                showChoices(row, item);
                return true;
            }
        });
        row.setAccessibilityDelegate(new View.AccessibilityDelegate() {
            @Override
            public void onInitializeAccessibilityNodeInfo(View host,
                    AccessibilityNodeInfo info) {
                super.onInitializeAccessibilityNodeInfo(host, info);
                // One action for each value, named after it.
                String[] values = model.getValues(item);
                for (int i = 0; values != null && i < values.length; i++) {
                    info.addAction(new AccessibilityAction(VALUE_ACTION_ID
                            + i, values[i]));
                }
            }

            @Override
            public boolean performAccessibilityAction(View host, int action,
                    Bundle args) {
                String[] values = model.getValues(item);
                int value = action - VALUE_ACTION_ID;
                if (values != null && value >= 0 && value < values.length) {
                    change(row, item, value);
                    return true;
                }
                return super.performAccessibilityAction(host, action, args);
            }
        });
    }

    // Shows an item's name, and its value if it is a setting.
    private void bindRow(View row, int item) {
        String title = model.getTitle(item);
        ((TextView) row.findViewById(R.id.menu_item_title)).setText(title);
        TextView valueView = (TextView) row.findViewById(R.id.menu_item_value);
        String value = getValueName(item);
        if (value == null) {
            valueView.setVisibility(View.GONE);
            row.setContentDescription(title);
        } else {
            valueView.setText(value);
            valueView.setVisibility(View.VISIBLE);
            row.setContentDescription(context.getString(
                    R.string.menu_item_value, title, value));
        }
    }

    private String getValueName(int item) {
        String[] values = model.getValues(item);
        if (values == null) {
            return null;
        }
        int value = model.getValue(item);
        return value >= 0 && value < values.length ? values[value] : "";
    }

    // Moves a setting to its next or previous value, going round.
    private boolean step(View row, int item, int step) {
        String[] values = model.getValues(item);
        if (values == null || values.length == 0) {
            return false;
        }
        int value = (model.getValue(item) + step + values.length)
                % values.length;
        change(row, item, value);
        return true;
    }

    private void change(View row, int item, int value) {
        String message = model.setValue(item, value);
        bindRow(row, item);
        row.announceForAccessibility(message != null ? message
                : getValueName(item));
    }

    // Lists a setting's values to choose from.
    private void showChoices(final View row, final int item) {
        String[] values = model.getValues(item);
        choices = new AlertDialog.Builder(context,
                android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(model.getTitle(item))
                .setSingleChoiceItems(values, model.getValue(item),
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface d, int which) {
                                d.dismiss();
                                change(row, item, which);
                            }
                        }).setNegativeButton(android.R.string.cancel, null)
                .create();
        choices.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface d) {
                choices = null;
                focus(row);
            }
        });
        attach(choices, keyboard);
        choices.show();
        // Start on the current value once the list is laid out.
        final ListView list = choices.getListView();
        final int checked = Math.max(0, model.getValue(item));
        list.post(new Runnable() {
            @Override
            public void run() {
                focus(list.getChildAt(checked
                        - list.getFirstVisiblePosition()));
            }
        });
    }

    // An input method has no activity to show dialogs from, so they are
    // attached to the keyboard's window. They don't take focus from the
    // editor, which would hide the keyboard when it got focus back. Screen
    // readers use them all the same.
    private static void attach(AlertDialog dialog, View view) {
        Window window = dialog.getWindow();
        WindowManager.LayoutParams params = window.getAttributes();
        params.token = view.getWindowToken();
        params.type = WindowManager.LayoutParams.TYPE_APPLICATION_ATTACHED_DIALOG;
        window.setAttributes(params);
        window.addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);
    }
}

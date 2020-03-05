/*
 * Copyright (C) 2017 Anton Kaliturin <kaliturin@gmail.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.kaliturin.blacklist.utils;

import android.app.Activity;
import android.content.Context;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.kaliturin.blacklist.R;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Settings name/value persistence container
 */

public class Settings {
    public static final String BLOCK_CALLS_FROM_BLACK_LIST = "BLOCK_CALLS_FROM_BLACK_LIST";
    public static final String BLOCK_ALL_CALLS = "BLOCK_ALL_CALLS";
    public static final String BLOCK_CALLS_NOT_FROM_CONTACTS = "BLOCK_CALLS_NOT_FROM_CONTACTS";
    public static final String BLOCK_CALLS_NOT_FROM_SMS_CONTENT = "BLOCK_CALLS_NOT_FROM_SMS_CONTENT";
    public static final String BLOCK_PRIVATE_CALLS = "BLOCK_PRIVATE_CALLS";
    public static final String BLOCKED_CALL_STATUS_NOTIFICATION = "BLOCKED_CALL_STATUS_NOTIFICATION";
    public static final String WRITE_CALLS_JOURNAL = "WRITE_CALLS_JOURNAL";
    public static final String BLOCK_SMS_FROM_BLACK_LIST = "BLOCK_SMS_FROM_BLACK_LIST";
    public static final String BLOCK_ALL_SMS = "BLOCK_ALL_SMS";
    public static final String BLOCK_SMS_NOT_FROM_CONTACTS = "BLOCK_SMS_NOT_FROM_CONTACTS";
    public static final String BLOCK_SMS_NOT_FROM_SMS_CONTENT = "BLOCK_SMS_NOT_FROM_SMS_CONTENT";
    public static final String BLOCK_PRIVATE_SMS = "BLOCK_PRIVATE_SMS";
    public static final String BLOCKED_SMS_STATUS_NOTIFICATION = "BLOCKED_SMS_STATUS_NOTIFICATION";
    public static final String WRITE_SMS_JOURNAL = "WRITE_SMS_JOURNAL";
    public static final String BLOCKED_SMS_SOUND_NOTIFICATION = "BLOCKED_SMS_SOUND_NOTIFICATION";
    public static final String RECEIVED_SMS_SOUND_NOTIFICATION = "RECEIVED_SMS_SOUND_NOTIFICATION";
    public static final String BLOCKED_SMS_VIBRATION_NOTIFICATION = "BLOCKED_SMS_VIBRATION_NOTIFICATION";
    public static final String RECEIVED_SMS_VIBRATION_NOTIFICATION = "RECEIVED_SMS_VIBRATION_NOTIFICATION";
    public static final String BLOCKED_SMS_RINGTONE = "BLOCKED_SMS_RINGTONE";
    public static final String RECEIVED_SMS_RINGTONE = "RECEIVED_SMS_RINGTONE";
    public static final String BLOCKED_CALL_SOUND_NOTIFICATION = "BLOCKED_CALL_SOUND_NOTIFICATION";
    public static final String BLOCKED_CALL_VIBRATION_NOTIFICATION = "BLOCKED_CALL_VIBRATION_NOTIFICATION";
    public static final String BLOCKED_CALL_RINGTONE = "BLOCKED_CALL_RINGTONE";
    public static final String DELIVERY_SMS_NOTIFICATION = "DELIVERY_SMS_NOTIFICATION";
    public static final String FOLD_SMS_TEXT_IN_JOURNAL = "FOLD_SMS_TEXT_IN_JOURNAL";
    public static final String UI_THEME_DARK = "UI_THEME_DARK";
    public static final String GO_TO_JOURNAL_AT_START = "GO_TO_JOURNAL_AT_START";
    public static final String DEFAULT_SMS_APP_NATIVE_PACKAGE = "DEFAULT_SMS_APP_NATIVE_PACKAGE";
    public static final String DONT_EXIT_ON_BACK_PRESSED = "DONT_EXIT_ON_BACK_PRESSED";
    public static final String REMOVE_FROM_CALL_LOG = "REMOVE_FROM_CALL_LOG";
    public static final String SIM_SUBSCRIPTION_ID = "SIM_SUBSCRIPTION";

    private static final String TRUE = "TRUE";
    private static final String FALSE = "FALSE";

    private static Map<String, String> settingsMap = new ConcurrentHashMap<>();

    public static void invalidateCache() {
        settingsMap.clear();
    }

    public static boolean setStringValue(Context context, @NonNull String name, @NonNull String value) {
        DatabaseAccessHelper db = DatabaseAccessHelper.getInstance(context);
        if (db != null && db.setSettingsValue(name, value)) {
            settingsMap.put(name, value);
            return true;
        }
        return false;
    }

    @Nullable
    public static String getStringValue(Context context, @NonNull String name) {
        String value = settingsMap.get(name);
        if (value == null) {
            DatabaseAccessHelper db = DatabaseAccessHelper.getInstance(context);
            if (db != null) {
                value = db.getSettingsValue(name);
                if (value != null) {
                    settingsMap.put(name, value);
                }
            }
        }
        return value;
    }

    public static boolean setBooleanValue(Context context, @NonNull String name, boolean value) {
        String v = (value ? TRUE : FALSE);
        return setStringValue(context, name, v);
    }

    public static boolean getBooleanValue(Context context, @NonNull String name) {
        String value = getStringValue(context, name);
        return (value != null && value.equals(TRUE));
    }

    public static boolean setIntegerValue(Context context, @NonNull String name, int value) {
        String v = String.valueOf(value);
        return setStringValue(context, name, v);
    }

    @Nullable
    public static Integer getIntegerValue(Context context, @NonNull String name) {
        String value = getStringValue(context, name);
        try {
            return (value != null ? Integer.valueOf(value) : null);
        } catch (NumberFormatException ignored) {
        }
        return null;
    }

    public static void initDefaults(Context context) {
        Map<String, String> map = new HashMap<>();
        map.put(BLOCK_CALLS_FROM_BLACK_LIST, TRUE);
        map.put(BLOCK_ALL_CALLS, FALSE);
        map.put(BLOCK_CALLS_NOT_FROM_CONTACTS, FALSE);
        map.put(BLOCK_CALLS_NOT_FROM_SMS_CONTENT, FALSE);
        map.put(BLOCK_PRIVATE_CALLS, FALSE);
        map.put(WRITE_CALLS_JOURNAL, TRUE);
        map.put(BLOCKED_CALL_STATUS_NOTIFICATION, TRUE);
        map.put(BLOCKED_CALL_SOUND_NOTIFICATION, FALSE);
        map.put(BLOCKED_CALL_VIBRATION_NOTIFICATION, FALSE);
        map.put(BLOCK_SMS_FROM_BLACK_LIST, TRUE);
        map.put(BLOCK_ALL_SMS, FALSE);
        map.put(BLOCK_SMS_NOT_FROM_CONTACTS, FALSE);
        map.put(BLOCK_SMS_NOT_FROM_SMS_CONTENT, FALSE);
        map.put(BLOCK_PRIVATE_SMS, FALSE);
        map.put(BLOCKED_SMS_STATUS_NOTIFICATION, TRUE);
        map.put(WRITE_SMS_JOURNAL, TRUE);
        map.put(BLOCKED_SMS_SOUND_NOTIFICATION, FALSE);
        map.put(RECEIVED_SMS_SOUND_NOTIFICATION, TRUE);
        map.put(BLOCKED_SMS_SOUND_NOTIFICATION, FALSE);
        map.put(RECEIVED_SMS_VIBRATION_NOTIFICATION, TRUE);
        map.put(BLOCKED_SMS_VIBRATION_NOTIFICATION, FALSE);
        map.put(DELIVERY_SMS_NOTIFICATION, TRUE);
        map.put(FOLD_SMS_TEXT_IN_JOURNAL, TRUE);
        map.put(UI_THEME_DARK, FALSE);
        map.put(GO_TO_JOURNAL_AT_START, FALSE);
        map.put(DONT_EXIT_ON_BACK_PRESSED, FALSE);
        map.put(REMOVE_FROM_CALL_LOG, FALSE);
        map.put(SIM_SUBSCRIPTION_ID, "-1");

        if (!Permissions.isGranted(context, Permissions.WRITE_EXTERNAL_STORAGE)) {
            settingsMap = new ConcurrentHashMap<>(map);
        } else {
            for (Map.Entry<String, String> entry : map.entrySet()) {
                String name = entry.getKey();
                if (getStringValue(context, name) == null) {
                    String value = entry.getValue();
                    setStringValue(context, name, value);
                }
            }
        }
    }

    // Applies the current UI theme depending on settings
    public static void applyCurrentTheme(Activity activity) {
        if (getBooleanValue(activity, Settings.UI_THEME_DARK)) {
            activity.setTheme(R.style.AppTheme_Dark);
        } else {
            activity.setTheme(R.style.AppTheme_Light);
        }
    }
}

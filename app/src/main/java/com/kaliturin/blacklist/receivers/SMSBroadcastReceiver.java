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

package com.kaliturin.blacklist.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import androidx.annotation.Nullable;
import android.telephony.SmsMessage;
import android.util.Log;

import com.kaliturin.blacklist.R;
import com.kaliturin.blacklist.services.BlockEventProcessService;
import com.kaliturin.blacklist.services.SMSProcessService;
import com.kaliturin.blacklist.utils.ContactsAccessHelper;
import com.kaliturin.blacklist.utils.DatabaseAccessHelper;
import com.kaliturin.blacklist.utils.DatabaseAccessHelper.Contact;
import com.kaliturin.blacklist.utils.DefaultSMSAppHelper;
import com.kaliturin.blacklist.utils.Permissions;
import com.kaliturin.blacklist.utils.Settings;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * BroadcastReceiver for SMS catching
 */
public class SMSBroadcastReceiver extends BroadcastReceiver {
    private static final String TAG = SMSBroadcastReceiver.class.getName();
    private static final String SMS_RECEIVED = "android.provider.Telephony.SMS_RECEIVED";
    private static final String SMS_DELIVER = "android.provider.Telephony.SMS_DELIVER";

    @Override
    public void onReceive(Context context, Intent intent) {
        long timeReceive = System.currentTimeMillis();

        // check action
        String action = intent.getAction();
        if (action == null ||
                !action.equals(SMS_DELIVER) &&
                        !action.equals(SMS_RECEIVED)) {
            return;
        }

        // is app the "default SMS app" ?
        boolean isDefaultSmsApp = DefaultSMSAppHelper.isDefault(context);

        // if "default SMS app" feature is available and app is default
        if (DefaultSMSAppHelper.isAvailable() &&
                isDefaultSmsApp && action.equals(SMS_RECEIVED)) {
            // ignore SMS_RECEIVED action - wait only for SMS_DELIVER
            return;
        }

        // get message data
        Map<String, String> data = extractMessageData(context, intent, timeReceive);
        if (data == null) {
            return;
        }

        // if isn't "default SMS app" or if message isn't blocked
        if (!isDefaultSmsApp || !processMessageData(context, data)) {
            // process message in service
            SMSProcessService.start(context, data);
        }
    }

    // Extracts message data
    @Nullable
    private Map<String, String> extractMessageData(Context context,
                                                   Intent intent, long timeReceive) {
        // get messages
        SmsMessage[] messages = getSMSMessages(intent);
        if (messages == null || messages.length == 0) {
            Log.w(TAG, "Received message is null or empty");
            return null;
        }

        // Assume that all messages in array received at ones have the same data except of bodies.
        // So get just the first message to get the rest data.
        SmsMessage message = messages[0];
        if (message == null) {
            Log.w(TAG, "Received message is null");
            return null;
        }
        String number = message.getOriginatingAddress();

        Map<String, String> data = new HashMap<>();
        data.put(ContactsAccessHelper.BODY, getSMSMessageBody(context, messages));
        data.put(ContactsAccessHelper.ADDRESS, number);
        data.put(ContactsAccessHelper.DATE, String.valueOf(timeReceive));
        data.put(ContactsAccessHelper.DATE_SENT, String.valueOf(message.getTimestampMillis()));
        data.put(ContactsAccessHelper.PROTOCOL, String.valueOf(message.getProtocolIdentifier()));
        data.put(ContactsAccessHelper.REPLY_PATH_PRESENT, String.valueOf(message.isReplyPathPresent()));
        data.put(ContactsAccessHelper.SERVICE_CENTER, message.getServiceCenterAddress());
        String subject = message.getPseudoSubject();
        subject = (subject != null && !subject.isEmpty() ? subject : null);
        data.put(ContactsAccessHelper.SUBJECT, subject);

        return data;
    }

    // Processes message; returns true if message was blocked, false else
    private boolean processMessageData(Context context, Map<String, String> data) {
        String number = data.get(ContactsAccessHelper.ADDRESS);
        String body = data.get(ContactsAccessHelper.BODY);

        // private number detected
        if (ContactsAccessHelper.isPrivatePhoneNumber(number)) {
            String name = context.getString(R.string.Private_number);
            data.put(ContactsAccessHelper.NAME, name);
            // if block private numbers
            if (Settings.getBooleanValue(context, Settings.BLOCK_PRIVATE_SMS) ||
                    // or if block all SMS
                    Settings.getBooleanValue(context, Settings.BLOCK_ALL_SMS)) {
                // abort broadcast and notify user
                abortSMSAndNotify(context, number, name, body);
                return true;
            }
            return false;
        }

        // normalize number
        number = ContactsAccessHelper.normalizePhoneNumber(number);
        if (number.isEmpty()) {
            Log.w(TAG, "Received message address is empty");
            return false;
        }

        // save normalized number
        data.put(ContactsAccessHelper.ADDRESS, number);

        // get contacts linked to the number
        List<Contact> contacts = getContacts(context, number);
        if (contacts == null) {
            return false;
        }

        // if contact is from the white list
        Contact contact = findContactByType(contacts, Contact.TYPE_WHITE_LIST);
        if (contact != null) {
            return false;
        }

        // get name of contact
        String name = (contacts.size() > 0 ? contacts.get(0).name : null);
        //data.put(ContactsAccessHelper.NAME, name);

        // if block all SMS (excluding the white list)
        if (Settings.getBooleanValue(context, Settings.BLOCK_ALL_SMS)) {
            // abort SMS and notify user
            abortSMSAndNotify(context, number, name, body);
            return true;
        }

        // if contact is from the black list
        if (Settings.getBooleanValue(context, Settings.BLOCK_SMS_FROM_BLACK_LIST)) {
            contact = findContactByType(contacts, Contact.TYPE_BLACK_LIST);
            if (contact != null) {
                // abort SMS and notify user
                abortSMSAndNotify(context, number, contact.name, body);
                return true;
            }
        }

        boolean abort = false;

        // if block numbers that are not in the contact list
        if (Settings.getBooleanValue(context, Settings.BLOCK_SMS_NOT_FROM_CONTACTS) &&
                Permissions.isGranted(context, Permissions.READ_CONTACTS)) {
            ContactsAccessHelper db = ContactsAccessHelper.getInstance(context);
            if (db.getContact(context, number) != null) {
                return false;
            }
            // there is no contact - get number as name
            name = number;
            abort = true;
        }

        // if block numbers that are not in the SMS content list
        if (Settings.getBooleanValue(context, Settings.BLOCK_SMS_NOT_FROM_SMS_CONTENT) &&
                Permissions.isGranted(context, Permissions.READ_SMS)) {
            ContactsAccessHelper db = ContactsAccessHelper.getInstance(context);
            if (db.containsNumberInSMSContent(context, number)) {
                return false;
            }
            abort = true;
        }

        if (abort) {
            // abort SMS and notify user
            abortSMSAndNotify(context, number, name, body);
        }

        return abort;
    }

    // Finds contact by type
    private Contact findContactByType(List<Contact> contacts, int contactType) {
        for (Contact contact : contacts) {
            if (contact.type == contactType) {
                return contact;
            }
        }
        return null;
    }

    // Finds contacts by number
    @Nullable
    private List<Contact> getContacts(Context context, String number) {
        DatabaseAccessHelper db = DatabaseAccessHelper.getInstance(context);
        return (db == null ? null : db.getContacts(number, false));
    }

    // Extracts received SMS message from intent
    @SuppressWarnings("deprecation")
    private SmsMessage[] getSMSMessages(Intent intent) {
        SmsMessage[] messages = null;
        Bundle bundle = intent.getExtras();
        if (bundle != null) {
            Object[] pdus = (Object[]) bundle.get("pdus");
            if (pdus != null) {
                messages = new SmsMessage[pdus.length];
                for (int i = 0; i < pdus.length; i++) {
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
                        messages[i] = SmsMessage.createFromPdu((byte[]) pdus[i]);
                    } else {
                        String format = bundle.getString("format");
                        messages[i] = SmsMessage.createFromPdu((byte[]) pdus[i], format);
                    }
                }
            }
        }
        return messages;
    }

    // Extracts message body
    private String getSMSMessageBody(Context context, SmsMessage[] messages) {
        StringBuilder smsBody = new StringBuilder();
        for (SmsMessage message : messages) {
            String text = message.getMessageBody();
            if (text != null) {
                smsBody.append(text);
            }
        }
        String body = smsBody.toString();
        if (body.isEmpty()) {
            body = context.getString(R.string.No_text);
        }
        return body;
    }

    // Aborts broadcast (if available) and notifies the user
    private void abortSMSAndNotify(Context context, String number, String name, String body) {
        // prevent placing this SMS to the inbox
        abortBroadcast();
        // process the event of blocking in the service
        BlockEventProcessService.start(context, number, name, body);
    }
}

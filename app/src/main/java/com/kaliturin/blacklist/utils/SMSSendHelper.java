package com.kaliturin.blacklist.utils;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.support.annotation.NonNull;
import android.telephony.SmsManager;
import android.widget.Toast;

import com.kaliturin.blacklist.InternalEventBroadcast;
import com.kaliturin.blacklist.R;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

/**
 * Sends SMS and processes the results of sending
 */

public class SMSSendHelper {
    private static final String PHONE_NUMBER = "PHONE_NUMBER";
    private static final String MESSAGE_PART = "MESSAGE_PART";
    private static final String MESSAGE_PART_ID = "MESSAGE_PART_ID";
    private static final String MESSAGE_PARTS = "MESSAGE_PARTS";
    private Set<BroadcastReceiver> receivers = new HashSet<>();

    // Sends SMS
    public boolean sendSMS(Context context, @NonNull String phoneNumber, @NonNull String message) {
        if (phoneNumber.isEmpty() || message.isEmpty()) {
            return false;
        }

        // get application context
        context = context.getApplicationContext();

        // divide message into parts
        SmsManager smsManager = SmsManager.getDefault();
        ArrayList<String> messageParts = smsManager.divideMessage(message);
        ArrayList<PendingIntent> sentIntents = new ArrayList<>(messageParts.size());
        ArrayList<PendingIntent> deliveryIntents = new ArrayList<>(messageParts.size());

        // create intents and result receivers for each part of message
        for (int i = 0; i < messageParts.size(); i++) {
            String messagePart = messageParts.get(i);
            int messagePartId = i + 1;

            // create on sent SMS receiver
            ResultReceiver receiver = new ResultReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    onSMSPartSent(context, intent, getResultCode());
                    receivers.remove(this);
                    context.unregisterReceiver(this);
                }
            };
            receivers.add(receiver);

            // create unique intent name and register receiver
            String intentName = "SMS_SENT" + "_" + Integer.toHexString(receiver.hashCode());
            PendingIntent pendingIntent = receiver.register(context, intentName,
                    phoneNumber, messagePart, messagePartId, messageParts.size());
            sentIntents.add(pendingIntent);

            if (Settings.getBooleanValue(context, Settings.DELIVERY_SMS_NOTIFICATION)) {
                // create on delivery SMS receiver
                receiver = new ResultReceiver() {
                    @Override
                    public void onReceive(Context context, Intent intent) {
                        onSMSPartDelivery(context, intent, getResultCode());
                        receivers.remove(this);
                        context.unregisterReceiver(this);
                    }
                };
                receivers.add(receiver);

                // create unique intent name and register receiver
                intentName = "SMS_DELIVERED" + "_" + Integer.toHexString(receiver.hashCode());
                pendingIntent = receiver.register(context, intentName,
                        phoneNumber, messagePart, messagePartId, messageParts.size());
                deliveryIntents.add(pendingIntent);
            }
        }

        // send multipart message
        smsManager.sendMultipartTextMessage(phoneNumber, null, messageParts, sentIntents, deliveryIntents);

        // write the sent SMS
        writeSMSMessage(context, phoneNumber, message);

        return true;
    }

    // Is calling on SMS part sending results received
    private void onSMSPartSent(Context context, Intent intent, int result) {
        int stringId = R.string.Unknown_error;
        switch (result) {
            case Activity.RESULT_OK:
                stringId = R.string.SMS_is_sent;
                break;
            case SmsManager.RESULT_ERROR_GENERIC_FAILURE:
                stringId = R.string.Generic_failure;
                break;
            case SmsManager.RESULT_ERROR_NO_SERVICE:
                stringId = R.string.No_service;
                break;
            case SmsManager.RESULT_ERROR_NULL_PDU:
                stringId = R.string.Null_PDU;
                break;
            case SmsManager.RESULT_ERROR_RADIO_OFF:
                stringId = R.string.Radio_off;
                break;
        }

        // notify user about sending the current part of SMS
        int messageParts = intent.getIntExtra(MESSAGE_PARTS, 0);
        int messagePartId = intent.getIntExtra(MESSAGE_PART_ID, 0);
        String message = context.getString(stringId);
        if (messageParts > 1) {
            message += " [" + messagePartId + "/" + messageParts + "]";
        }
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
    }

    /**
     * Is calling on SMS part delivery result received
     **/
    private void onSMSPartDelivery(Context context, Intent intent, int result) {
        int stringId = R.string.Unknown_error;
        switch (result) {
            case Activity.RESULT_OK:
                stringId = R.string.SMS_is_delivered;
                break;
            case Activity.RESULT_CANCELED:
                stringId = R.string.SMS_is_not_delivered;
                break;
        }

        // get SMS parameters
        String phoneNumber = intent.getStringExtra(PHONE_NUMBER);
        int messageParts = intent.getIntExtra(MESSAGE_PARTS, 0);
        int messagePartId = intent.getIntExtra(MESSAGE_PART_ID, 0);

        // notify user about delivery only the last part of the SMS
        if (phoneNumber != null && messageParts > 0 &&
                messagePartId == messageParts) {
            String message = context.getString(stringId);
            Notifications.onSmsDelivery(context, phoneNumber, message);
        }
    }

    /**
     * Cleans pending results
     **/
    public void clean(Context context) {
        context = context.getApplicationContext();
        for (BroadcastReceiver receiver : receivers) {
            context.unregisterReceiver(receiver);
        }
        receivers.clear();
    }

    // Writes the sent SMS
    private void writeSMSMessage(Context context, String phoneNumber, String message) {
        // if newer KITKAT and if app isn't default - the SMS will be written by the system
        if (!DefaultSMSAppHelper.isAvailable() ||
                DefaultSMSAppHelper.isDefault(context)) {
            ContactsAccessHelper db = ContactsAccessHelper.getInstance(context);
            db.writeSMSMessageToSentBox(context, phoneNumber, message);
        }

        // send internal event message
        InternalEventBroadcast.sendSMSWasWritten(context, phoneNumber);
    }

    // Sending SMS action results receiver
    private abstract class ResultReceiver extends BroadcastReceiver {
        PendingIntent register(Context context, String intentName, String phoneNumber,
                               String messagePart, int messagePartId, int messageParts) {
            // create pending intent
            Intent intent = new Intent(intentName);
            intent.putExtra(PHONE_NUMBER, phoneNumber);
            intent.putExtra(MESSAGE_PART, messagePart);
            intent.putExtra(MESSAGE_PART_ID, messagePartId);
            intent.putExtra(MESSAGE_PARTS, messageParts);
            PendingIntent pendingIntent = PendingIntent.getBroadcast(context, 0, intent, 0);
            // register receiver
            context.registerReceiver(this, new IntentFilter(intentName));
            return pendingIntent;
        }
    }
}

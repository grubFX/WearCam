package net.felixgruber.wearcam;

import android.app.PendingIntent;
import android.content.Intent;
import android.util.Log;

import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.WearableListenerService;

/**
 * Created by Felix on 30.05.2015.
 */
public class MobileListener extends WearableListenerService {
    private static final String TAG = "MobileListener";

    @Override
    public void onMessageReceived(MessageEvent messageEvent) {
        super.onMessageReceived(messageEvent);
        Log.d(TAG, "onMessageReceived: " + messageEvent.getPath());

        if (messageEvent.getPath().equals(MyConstants.PATH_START)) {
            Log.d(TAG, "path equals pathstart,");
            Intent intent = new Intent(this, MainActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_NEW_TASK);
            PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
            startActivity(intent);
        }
    }
}
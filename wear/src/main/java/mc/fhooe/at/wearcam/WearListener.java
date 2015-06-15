package mc.fhooe.at.wearcam;

import android.content.Intent;
import android.util.Log;

import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.WearableListenerService;

/**
 * Created by Felix on 30.05.2015.
 */
public class WearListener extends WearableListenerService {
    private static final String TAG = "WearListener";
    Intent intent = null;

    @Override
    public void onMessageReceived(MessageEvent messageEvent) {
        super.onMessageReceived(messageEvent);
        Log.d(TAG, "onMessageReceived: " + messageEvent.getPath());

        /*
        if (messageEvent.getPath().equals("start")) {
            if (intent == null) {
                intent = new Intent(this, MainActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
            } else {
                Log.d(TAG, "intent already running");
            }
        } else if (messageEvent.getPath().equals("stop")) {
            intent = null;
        }
        */
    }
}
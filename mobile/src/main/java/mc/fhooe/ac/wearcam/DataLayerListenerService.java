package mc.fhooe.ac.wearcam;

import android.util.Log;
import android.widget.Toast;

import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.WearableListenerService;

/**
 * Created by Musti on 20.05.2015.
 */
public class DataLayerListenerService extends WearableListenerService {

    @Override
    public void onMessageReceived(MessageEvent messageEvent) {
        super.onMessageReceived(messageEvent);
        if("/MESSAGE".equals(messageEvent.getPath())) {
            Toast.makeText(getApplication(),"ButtonClick received",Toast.LENGTH_LONG).show();
            Log.i("messageReceived","toaaaast");
        }
    }
}

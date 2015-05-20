package mc.fhooe.ac.wearcam;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Wearable;
import com.google.android.gms.wearable.WearableListenerService;

/**
 * Created by Felix on 20.05.2015.
 */
public class ReceiverService extends WearableListenerService implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {
    GoogleApiClient mGoogleApiClient;
    public final String LOGTAG = "Wear - MainActivity";
    Context mContext;

    @Override
    public void onCreate() {
        super.onCreate();
        mContext = this;

        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addApi(Wearable.API)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .build();
    }

    @Override
    public void onMessageReceived(MessageEvent messageEvent) {
        super.onMessageReceived(messageEvent);

        if (messageEvent.getPath().equals("/startactivity")) {
            Intent startIntent = new Intent(this, MainActivity.class);
            startIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(startIntent);
        }
    }

    @Override
    public void onConnected(Bundle bundle) {
        String temp = "connected to phone";
        Log.d(LOGTAG, temp);
        Toast.makeText(mContext, temp, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onConnectionSuspended(int i) {
        String temp = "connection to phone suspended";
        Log.d(LOGTAG, temp);
        Toast.makeText(mContext, temp, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {
        String temp = "connection to phone failed";
        Log.w(LOGTAG, temp);
        Toast.makeText(mContext, temp, Toast.LENGTH_SHORT).show();
    }
}
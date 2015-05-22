package mc.fhooe.ac.wearcam;

import android.app.Activity;
import android.content.Intent;
import android.content.IntentSender;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.support.wearable.view.WatchViewStub;
import android.util.Log;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.data.FreezableUtils;
import com.google.android.gms.wearable.Asset;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.Wearable;

import java.io.InputStream;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class MainActivity extends Activity implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener, DataApi.DataListener {
    private TextView mTextView;
    private ImageView mImageView;
    private GoogleApiClient mGoogleApiClient;
    private String TAG = "WearTAG";
    private static final int REQUEST_RESOLVE_ERROR = 1001;
    private boolean mResolvingError = false;
    private Uri uri;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        final WatchViewStub stub = (WatchViewStub) findViewById(R.id.watch_view_stub);
        stub.setOnLayoutInflatedListener(new WatchViewStub.OnLayoutInflatedListener() {
            @Override
            public void onLayoutInflated(WatchViewStub stub) {
                mTextView = (TextView) stub.findViewById(R.id.text);
                mImageView = (ImageView) stub.findViewById(R.id.imageView);
            }
        });

        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(Wearable.API)
                .build();

        uri = null;
    }

    @Override
    protected void onResume() {
        super.onStart();
        Wearable.DataApi.addListener(mGoogleApiClient, this);
        mGoogleApiClient.connect();
        Log.d(TAG, "connect called in onResume Method");
    }

    @Override
    protected void onPause() {
        super.onPause();
        mGoogleApiClient.disconnect();
        Wearable.DataApi.removeListener(mGoogleApiClient, this);
        Log.d(TAG, "disconnected in onPause Method");
    }

    @Override
    public void onConnected(Bundle bundle) {
        Log.d(TAG, "onConnected: " + bundle);
        /*
        if (uri != null) {
            Wearable.DataApi.deleteDataItems(mGoogleApiClient, uri).setResultCallback(new ResultCallback<DataApi.DeleteDataItemsResult>() {
                @Override
                public void onResult(DataApi.DeleteDataItemsResult deleteDataItemsResult) {
                    Log.d(TAG, "onResult of deleting dataItem: " + deleteDataItemsResult.getStatus());
                    uri = null;
                }
            });
        } else {
            Log.w(TAG, "uri was null - could not delete");
        }
        */
    }

    @Override
    public void onConnectionSuspended(int i) {
        Log.w(TAG, "onConnectionSuspended: " + i);
    }

    @Override
    public void onConnectionFailed(ConnectionResult result) {
        Log.e(TAG, "onConnectionFailed: " + result);

        if (mResolvingError) {
            return;
        } else if (result.hasResolution()) {
            try {
                mResolvingError = true;
                result.startResolutionForResult(this, REQUEST_RESOLVE_ERROR);
            } catch (IntentSender.SendIntentException e) {
                mGoogleApiClient.connect();
            }
        } else {
            Log.e(TAG, "connection failed: " + result.getErrorCode());
            mResolvingError = true;
        }
    }


    @Override
    protected void onStart() {
        super.onStart();
        if (!mResolvingError) {
            mGoogleApiClient.connect();
            Log.d(TAG, "connected to play services");
        }
    }

    @Override
    protected void onStop() {
        mGoogleApiClient.disconnect();
        Log.w(TAG, "disconnected from play services");
        super.onStop();
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_RESOLVE_ERROR) {
            mResolvingError = false;
            if (resultCode == RESULT_OK) {
                if (!mGoogleApiClient.isConnecting() &&
                        !mGoogleApiClient.isConnected()) {
                    mGoogleApiClient.connect();
                }
            }
        }
    }

    @Override
    public void onDataChanged(DataEventBuffer dataEvents) {
        final List<DataEvent> events = FreezableUtils.freezeIterable(dataEvents);
        dataEvents.close();
        for (DataEvent event : events) {
            if (event.getType() == DataEvent.TYPE_CHANGED) {
                Log.d(TAG, "onDataChanged - TYPE_CHANGED");
                uri = event.getDataItem().getUri();
                String path = uri.getPath();
                if ("/image".equals(path)) {
                    DataMapItem item = DataMapItem.fromDataItem(event.getDataItem());
                    Asset asset = item.getDataMap().getAsset("img");
                    Bitmap bitmap = loadBitmapFromAsset(asset);
                    if (bitmap != null) {
                        reloadImageView(bitmap);

                    }
                }

                mGoogleApiClient.reconnect();
                Wearable.DataApi.addListener(mGoogleApiClient, this);
            } else if (event.getType() == DataEvent.TYPE_DELETED) {
                Log.d(TAG, "onDataChanged - TYPE_DELETED");
            }
        }
    }

    private void reloadImageView(Bitmap _bitmap) {
        final Bitmap bit = _bitmap;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mImageView.setImageBitmap(bit);
                Log.d(TAG, "image was changed");
            }
        });

    }

    public Bitmap loadBitmapFromAsset(Asset asset) {
        if (asset == null) {
            throw new IllegalArgumentException("Asset must be non-null");
        }
        ConnectionResult result =
                mGoogleApiClient.blockingConnect(100, TimeUnit.MILLISECONDS);
        if (!result.isSuccess()) {
            return null;
        }
        InputStream assetInputStream = Wearable.DataApi.getFdForAsset(
                mGoogleApiClient, asset).await().getInputStream();
        mGoogleApiClient.disconnect();

        if (assetInputStream == null) {
            Log.w(TAG, "Requested an unknown Asset.");
            return null;
        }
        return BitmapFactory.decodeStream(assetInputStream);
    }
}
package mc.fhooe.at.wearcam;

import android.app.Activity;
import android.content.Intent;
import android.content.IntentSender;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.support.wearable.view.WatchViewStub;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.data.FreezableUtils;
import com.google.android.gms.wearable.Asset;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.MessageApi;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.NodeApi;
import com.google.android.gms.wearable.Wearable;

import java.io.InputStream;
import java.util.List;
import java.util.Vector;
import java.util.concurrent.TimeUnit;

public class MainActivity extends Activity implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener, DataApi.DataListener, MessageApi.MessageListener, NodeApi.NodeListener, View.OnClickListener {
    private ImageView mImageView;
    private GoogleApiClient mGoogleApiClient;
    private String TAG = "WearTAG";
    private static final int REQUEST_RESOLVE_ERROR = 1001;
    private boolean mResolvingError = false;
    private Uri uri;
    private Vector<Node> mNodeList;
    private ImageButton imgBtn1 = null, imgBtn2 = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        final WatchViewStub stub = (WatchViewStub) findViewById(R.id.watch_view_stub);
        stub.setOnLayoutInflatedListener(new WatchViewStub.OnLayoutInflatedListener() {
            @Override
            public void onLayoutInflated(WatchViewStub stub) {
                mImageView = (ImageView) stub.findViewById(R.id.imageView);
                imgBtn1 = (ImageButton) findViewById(R.id.imageButton1);
                imgBtn2 = (ImageButton) findViewById(R.id.imageButton2);
                updateUI();
            }
        });

        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(Wearable.API)
                .build();
        mNodeList = new Vector();
        uri = null;
    }

    void updateUI() {
        if (imgBtn1 != null) {
            imgBtn1.setOnClickListener(this);
        }
        if (imgBtn2 != null) {
            imgBtn2.setOnClickListener(this);
        }
    }

    @Override
    protected void onStart() {
        mGoogleApiClient.connect();
        Wearable.MessageApi.addListener(mGoogleApiClient, this);
        Wearable.DataApi.addListener(mGoogleApiClient, this);
        super.onStart();
    }

    @Override
    protected void onDestroy() {
        sendToPhones("stop");
        Wearable.DataApi.removeListener(mGoogleApiClient, this);
        Wearable.MessageApi.removeListener(mGoogleApiClient, this);
        mGoogleApiClient.disconnect();
        super.onDestroy();
    }

    @Override
    public void onConnected(Bundle bundle) {
        Log.d(TAG, "onConnected: " + bundle);
        Wearable.NodeApi.getConnectedNodes(mGoogleApiClient).setResultCallback(new ResultCallback<NodeApi.GetConnectedNodesResult>() {
            @Override
            public void onResult(NodeApi.GetConnectedNodesResult getConnectedNodesResult) {
                mNodeList = new Vector<>();
                if (getConnectedNodesResult.getNodes().size() > 0) {
                    for (Node n : getConnectedNodesResult.getNodes()) {
                        mNodeList.add(n);
                        Log.d(TAG, "found node: name=" + n.getDisplayName() + ", id=" + n.getId());
                    }
                    sendToPhones("start");
                }
            }
        });
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
                //Log.d(TAG, "onDataChanged - TYPE_CHANGED");
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
                //Log.d(TAG, "image was changed");
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

        if (assetInputStream == null) {
            Log.w(TAG, "Requested an unknown Asset.");
            return null;
        }
        return BitmapFactory.decodeStream(assetInputStream);
    }

    @Override
    public void onMessageReceived(MessageEvent _messageEvent) {
        final String temp = _messageEvent.getPath();
        if (temp.equals("stop")) {
            finish();
        }
    }

    @Override
    public void onPeerConnected(Node _node) {
        Log.d(TAG, "node connected: name=" + _node.getDisplayName() + ", id=" + _node.getId());
        mNodeList.add(_node);
    }

    @Override
    public void onPeerDisconnected(Node _node) {
        Log.d(TAG, "node DISCONNECTED: name=" + _node.getDisplayName() + ", id=" + _node.getId());
        mNodeList.remove(_node);
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.imageButton1:
                sendToPhones("pic");
                break;

            case R.id.imageButton2:
                sendToPhones("vid");
                break;
        }
    }

    public void sendToPhones(final String _path) {
        for (Node n : mNodeList) {
            if (n != null) {
                Wearable.MessageApi.sendMessage(mGoogleApiClient, n.getId(), _path, null).setResultCallback(new ResultCallback<MessageApi.SendMessageResult>() {
                    @Override
                    public void onResult(MessageApi.SendMessageResult sendMessageResult) {
                        Log.d(TAG, "onResult of sending \"" + _path + "\" to mobile: " + sendMessageResult.getStatus());
                    }
                });
            } else {
                Log.w(TAG, "node was null");
            }
        }
    }
}
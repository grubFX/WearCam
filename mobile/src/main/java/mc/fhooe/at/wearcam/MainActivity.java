package mc.fhooe.at.wearcam;

import android.app.Activity;
import android.content.IntentSender;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.hardware.Camera;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.OrientationEventListener;
import android.view.SurfaceView;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.Asset;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.MessageApi;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.NodeApi;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.PutDataRequest;
import com.google.android.gms.wearable.Wearable;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Date;
import java.util.Vector;

public class MainActivity extends Activity implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener, Camera.PreviewCallback, MessageApi.MessageListener, NodeApi.NodeListener {
    private GoogleApiClient mGoogleApiClient;
    private String TAG = "PhoneTag";
    private Camera mCamera;
    private static final int REQUEST_RESOLVE_ERROR = 1001, IMG_SIZE = 200, QUALITY_IN_PERCENT = 30;
    private boolean mResolvingError = false;
    private Vector<Node> mNodeList;
    private Preview mPreview;
    private int ANGLE_ROTATE_MATRIX, ANGLE_ROTATE_PREVIEW;
    private OrientationEventListener mOrientationEventListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);

        setContentView(R.layout.activity_main);

        ANGLE_ROTATE_PREVIEW = 90;
        ANGLE_ROTATE_MATRIX = 0;

        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addApi(Wearable.API)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .build();
        mNodeList = new Vector<>();
        mPreview = new Preview(this, (SurfaceView) findViewById(R.id.surfaceView));
        mPreview.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        ((FrameLayout) findViewById(R.id.layout)).addView(mPreview);
        mPreview.setKeepScreenOn(true);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
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
                    //sendToWear("start");
                }
            }
        });
    }

    @Override
    public void onConnectionSuspended(int i) {
        Log.d(TAG, "onConnectionSuspended: " + i);
    }

    @Override
    public void onConnectionFailed(ConnectionResult result) {
        Log.d(TAG, "onConnectionFailed: " + result);

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
            Log.e(TAG, "onCeonnectionFailed - error code: " + result.getErrorCode());
            mResolvingError = true;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        mGoogleApiClient.connect();
        Wearable.MessageApi.addListener(mGoogleApiClient, this);

        int numCams = Camera.getNumberOfCameras();
        if (numCams > 0) {
            try {
                mCamera = Camera.open(0);
                mCamera.startPreview();
                mPreview.setCamera(mCamera);
            } catch (RuntimeException ex) {
                Log.e(TAG, "camera not found");
            }
        }
        if (mCamera != null) {
            mCamera.setPreviewCallback(this);
            mCamera.setDisplayOrientation(ANGLE_ROTATE_PREVIEW);
        }

        if (mOrientationEventListener == null) {
            mOrientationEventListener = new OrientationEventListener(this, SensorManager.SENSOR_DELAY_NORMAL) {
                @Override
                public void onOrientationChanged(int _rot) {
                    int temp = _rot % 360;
                    if (temp > 315 || temp <= 45) {
                        ANGLE_ROTATE_MATRIX = 0;
                    } else if (temp > 45 && temp <= 135) {
                        ANGLE_ROTATE_MATRIX = 90;
                    } else if (temp > 135 && temp <= 225) {
                        ANGLE_ROTATE_MATRIX = 180;
                    } else if (temp > 225 && temp <= 315) {
                        ANGLE_ROTATE_MATRIX = 270;
                    }
                }
            };
        }
        if (mOrientationEventListener.canDetectOrientation()) {
            mOrientationEventListener.enable();
        }
    }

    @Override
    protected void onPause() {
        Wearable.MessageApi.removeListener(mGoogleApiClient, this);
        mGoogleApiClient.disconnect();

        if (mCamera != null) {
            mCamera.stopPreview();
            mCamera.setPreviewCallback(null);
            mPreview.setCamera(null);
            mCamera.release();
            mCamera = null;
        }
        mOrientationEventListener.disable();
        super.onPause();
    }

    @Override
    public void onPreviewFrame(byte[] data, Camera camera) {
        final byte[] arr = data.clone();
        YuvImage temp = new YuvImage(arr, camera.getParameters().getPreviewFormat(), camera.getParameters().getPictureSize().width, camera.getParameters().getPictureSize().height, null);
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        temp.compressToJpeg(new Rect(0, 0, temp.getWidth(), temp.getHeight()), QUALITY_IN_PERCENT, os);
        Bitmap preview = Bitmap.createScaledBitmap(BitmapFactory.decodeByteArray(os.toByteArray(), 0, os.toByteArray().length), IMG_SIZE, IMG_SIZE, false);
        Matrix m = new Matrix();
        m.postRotate(ANGLE_ROTATE_MATRIX + ANGLE_ROTATE_PREVIEW);
        Bitmap rotatedBitmap = Bitmap.createBitmap(preview, 0, 0, preview.getWidth(), preview.getHeight(), m, true);
        Asset asset = null;
        ByteArrayOutputStream byteStream = null;
        try {
            byteStream = new ByteArrayOutputStream();
            rotatedBitmap.compress(Bitmap.CompressFormat.WEBP, QUALITY_IN_PERCENT, byteStream);
            asset = Asset.createFromBytes(byteStream.toByteArray());
        } finally {
            if (byteStream != null) {
                try {
                    byteStream.close();
                } catch (IOException e) {
                    // ignore
                }
            }
        }
        if (asset != null) {
            PutDataMapRequest dataMap = PutDataMapRequest.create("/image");
            dataMap.getDataMap().putLong("" + new Date().getTime(), new Date().getTime());
            dataMap.getDataMap().putAsset("img", asset);
            PutDataRequest request = dataMap.asPutDataRequest();

            PendingResult<DataApi.DataItemResult> pendingResult = Wearable.DataApi
                    .putDataItem(mGoogleApiClient, request);
            pendingResult.setResultCallback(new ResultCallback<DataApi.DataItemResult>() {
                @Override
                public void onResult(DataApi.DataItemResult dataItemResult) {
                    //Log.d(TAG, "onResult of sending data: " + dataItemResult.getStatus());
                }
            });
        }
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

    public void sendToWear(final String _path) {
        for (Node n : mNodeList) {
            if (n != null) {
                Wearable.MessageApi.sendMessage(mGoogleApiClient, n.getId(), _path, null).setResultCallback(new ResultCallback<MessageApi.SendMessageResult>() {
                    @Override
                    public void onResult(MessageApi.SendMessageResult sendMessageResult) {
                        Log.d(TAG, "onResult of sending \"" + _path + "\" to wear: " + sendMessageResult.getStatus());
                    }
                });
            } else {
                Log.w(TAG, "node was null");
            }
        }
    }

    private void resetCam() { // needs to be called after taking pic
        mCamera.startPreview();
        mPreview.setCamera(mCamera);
    }
}
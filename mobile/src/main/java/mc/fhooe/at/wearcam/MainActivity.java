package mc.fhooe.at.wearcam;

import android.app.Activity;
import android.content.Intent;
import android.content.IntentSender;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.hardware.Camera;
import android.os.Bundle;
import android.util.Log;
import android.view.Display;
import android.view.Menu;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.Asset;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.MessageApi;
import com.google.android.gms.wearable.MessageApi.MessageListener;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.NodeApi;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.PutDataRequest;
import com.google.android.gms.wearable.Wearable;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Date;

public class MainActivity extends Activity implements DataApi.DataListener, GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener, Camera.PreviewCallback ,SurfaceHolder.Callback2{
    private GoogleApiClient mGoogleApiClient;
    private Button button;
    private String TAG = "PhoneTag";
    private int i=0;
    private Camera cam;
    private static int REQUEST_RESOLVE_ERROR = 1001, SCREEN_ROTATION = 90, SCREEN = 90;
    private boolean mResolvingError = false;
    private SurfaceView surf;
    private SurfaceHolder mHolder;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addApi(Wearable.API)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .build();


        cam = Camera.open();
        //cam.setDisplayOrientation(SCREEN);
        cam.setPreviewCallback(this);


        surf = (SurfaceView) findViewById(R.id.surfaceView);
        mHolder = surf.getHolder();
        mHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
        mHolder.addCallback(this);
    }



    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public void onConnected(Bundle bundle) {
        Log.d(TAG, "onConnected: " + bundle);
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
            showErrorDialog(result.getErrorCode());
            mResolvingError = true;
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (!mResolvingError) {  // more about this later
            mGoogleApiClient.connect();
            Log.d(TAG, "connedted, jippieeeeeeee!!!!! ");
        }
    }

    @Override
    protected void onStop() {
        mGoogleApiClient.disconnect();
        Log.d(TAG, "disconnedted,  :( :( :( :( :(!!!!! ");
        super.onStop();
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
        Log.d(TAG, "disconnected in onPause Method");
        Wearable.DataApi.removeListener(mGoogleApiClient, this);
    }

    private void showErrorDialog(int errorCode) {
        Toast.makeText(getApplication(), errorCode, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onDataChanged(DataEventBuffer dataEventBuffer) {
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cam.setPreviewCallback(null);
        surf.getHolder().removeCallback(this);
        cam.stopPreview();
        cam.release();
    }


    private static Asset createAssetFromBitmap(Bitmap bitmap) {
        ByteArrayOutputStream byteStream = null;
        try {
            byteStream = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.WEBP, 5, byteStream);
            return Asset.createFromBytes(byteStream.toByteArray());
        } finally {
            if (byteStream != null) {
                try {
                    byteStream.close();
                } catch (IOException e) {
                    // ignore
                }
            }
        }
    }

    @Override
    public void onPreviewFrame(byte[] data, Camera camera) {
        Log.d(TAG, "previewCamera called");
        YuvImage temp = new YuvImage(data, camera.getParameters().getPreviewFormat(), camera.getParameters().getPictureSize().width, camera.getParameters().getPictureSize().height, null);
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        temp.compressToJpeg(new Rect(0, 0, temp.getWidth(), temp.getHeight()), 20, os);
        Bitmap preview = Bitmap.createScaledBitmap(BitmapFactory.decodeByteArray(os.toByteArray(), 0, os.toByteArray().length), 280, 280, true);
        Matrix m = new Matrix();
        m.postRotate(SCREEN);
        Bitmap rotatedBitmap = Bitmap.createBitmap(preview, 0, 0, preview.getWidth(), preview.getHeight(), m, true);
        Asset asset = createAssetFromBitmap(rotatedBitmap);

            PutDataMapRequest dataMap = PutDataMapRequest.create("/image");

            dataMap.getDataMap().putLong("time", new Date().getTime());
            dataMap.getDataMap().putAsset("img", asset);
            dataMap.getDataMap().putLong(String.valueOf(System.currentTimeMillis()), System.currentTimeMillis());
            PutDataRequest request = dataMap.asPutDataRequest();

            PendingResult<DataApi.DataItemResult> pendingResult = Wearable.DataApi
                    .putDataItem(mGoogleApiClient, request);
            pendingResult.setResultCallback(new ResultCallback<DataApi.DataItemResult>() {
                @Override
                public void onResult(DataApi.DataItemResult dataItemResult) {
                    Log.i(TAG, "onResult of sending data: " + dataItemResult.getStatus());
                }
            });

    }


    @Override
    public void surfaceCreated(SurfaceHolder holder) {

        try {
            cam.setPreviewDisplay(holder);
            cam.startPreview();
            cam.autoFocus(new Camera.AutoFocusCallback() {
                @Override
                public void onAutoFocus(boolean success, Camera camera) {
                    if (camera.getParameters().getFocusMode() != Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE) {
                        Camera.Parameters parameters = camera.getParameters();
                        parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
                        parameters.setFocusAreas(null);
                        camera.setParameters(parameters);
                        camera.startPreview();
                    }
                }
            });
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {

        Camera.Parameters parameters = cam.getParameters();
        Display display = ((WindowManager)getSystemService(WINDOW_SERVICE)).getDefaultDisplay();

        if(display.getRotation() == Surface.ROTATION_0)
        {
            parameters.setPreviewSize(height, width);
            cam.setDisplayOrientation(90);
            SCREEN = 90;
        }

        if(display.getRotation() == Surface.ROTATION_90)
        {
            parameters.setPreviewSize(width, height);
            SCREEN = 0;
        }

        if(display.getRotation() == Surface.ROTATION_180)
        {
            parameters.setPreviewSize(height, width);
            //SCREEN = 0;
        }

        if(display.getRotation() == Surface.ROTATION_270)//rotate right
        {
            parameters.setPreviewSize(width, height);
            cam.setDisplayOrientation(180);
            SCREEN = 180;
        }

    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
    }

    @Override
    public void surfaceRedrawNeeded(SurfaceHolder holder) {
    }
}
package mc.fhooe.at.wearcam;

import android.app.Activity;
import android.content.Context;
import android.content.IntentSender;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.hardware.Camera;
import android.hardware.SensorManager;
import android.media.AudioManager;
import android.media.CamcorderProfile;
import android.media.MediaRecorder;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.Menu;
import android.view.MotionEvent;
import android.view.OrientationEventListener;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.LinearInterpolator;
import android.view.animation.ScaleAnimation;
import android.widget.ImageButton;
import android.widget.ImageView;
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
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Vector;

public class MainActivity extends Activity implements DataApi.DataListener, GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener, Camera.PreviewCallback, MessageListener, NodeApi.NodeListener, SurfaceHolder.Callback, Camera.PictureCallback, Camera.ShutterCallback, View.OnTouchListener {
    private GoogleApiClient mGoogleApiClient;
    private String TAG = "PhoneTag", path = null;
    private int currentCameraId = Camera.CameraInfo.CAMERA_FACING_BACK, index, angleRotateMatrix, anglePreview;
    private static final int REQUEST_RESOLVE_ERROR = 1001, IMG_SIZE = 200, QUALITY_IN_PERCENT = 20;
    private Camera cam;
    private boolean mResolvingError = false, isRecording = false, isFlashModeOn = false, isGalleryModeOn = false;
    private SurfaceView surf;
    private SurfaceHolder mHolder;
    private MediaRecorder recorder;
    private ImageView redDotView;
    private ImageButton flashButton, changeCamButton, pictureButton, videoButton;
    private float mDist;
    private Vector<Node> mNodeList;
    private OrientationEventListener mOrientationEventListener;
    private Utils utils;
    private ArrayList<String> imagePaths = new ArrayList<String>();

    // SD card image directory
    public static final String PHOTO_ALBUM = "wearcam";
    // supported file formats
    public static final List<String> FILE_EXTN = Arrays.asList("jpg", "jpeg", "png");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);

        setContentView(R.layout.activity_main);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        anglePreview = 90;
        angleRotateMatrix = 0;

        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addApi(Wearable.API)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .build();

        Wearable.MessageApi.addListener(mGoogleApiClient, this);

        pictureButton = (ImageButton) findViewById(R.id.pictureButton);
        pictureButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!isGalleryModeOn) {
                    takePicture();
                }
            }
        });

        videoButton = (ImageButton) findViewById(R.id.videoButton);
        videoButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!isGalleryModeOn) {
                    takeVideo();
                }
            }
        });

        flashButton = (ImageButton) findViewById(R.id.flashlightButton);
        flashButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                flashLightAction();
            }
        });

        changeCamButton = (ImageButton) findViewById(R.id.changeCameraButton);
        changeCamButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                changeCamera();
            }
        });

        redDotView = (ImageView) findViewById(R.id.redDotPhoneImage);

        setupCam();
        flashButton.setAlpha(0.5f);
        utils = new Utils(this);
        // loading all image paths from SD card
        imagePaths = utils.getFilePaths();
        mNodeList = new Vector<>();
        index = 0;
    }

    void flashLightAction() {
        if (isFlashModeOn) {
            isFlashModeOn = false;
            flashButton.setAlpha(0.5f);
        } else {
            isFlashModeOn = true;
            flashButton.setAlpha(1.0f);
        }
    }

    void changeCamera() {
        destroyCam();

        if (currentCameraId == Camera.CameraInfo.CAMERA_FACING_BACK)
            currentCameraId = Camera.CameraInfo.CAMERA_FACING_FRONT;
        else {
            currentCameraId = Camera.CameraInfo.CAMERA_FACING_BACK;
        }
        setupCam();
    }

    void startRedDotAnimation() {
        redDotView.setAlpha(1.0f);
        ScaleAnimation anim = new ScaleAnimation(1.0f, 1.3f, 1.0f, 1.3f, 100, 100);
        anim.setInterpolator(new LinearInterpolator());
        anim.setRepeatCount(Animation.INFINITE);
        anim.setRepeatMode(Animation.REVERSE);
        anim.setDuration(1000);
        redDotView.startAnimation(anim);
    }

    void stopRedDotAnimation() {
        redDotView.setAnimation(null);
        redDotView.animate();
        redDotView.setAlpha(0.0f);
    }

    void takePicture() {
        Camera.Parameters p = cam.getParameters();
        if (isFlashModeOn) {
            p.setFlashMode(Camera.Parameters.FLASH_MODE_ON);
        } else {
            p.setFlashMode(Camera.Parameters.FLASH_MODE_OFF);
        }
        cam.setParameters(p);
        cam.takePicture(this, this, this);
        Toast.makeText(getApplication(), "Picture saved in \\wearcam", Toast.LENGTH_SHORT).show();
    }

    void takeVideo() {
        if (isRecording) {
            recorder.stop();
            releaseMediaRecorder();
            cam.lock();
            isRecording = false;
            stopRedDotAnimation();

            Toast.makeText(getApplication(), "Recording stopped!!", Toast.LENGTH_SHORT).show();
            if (isFlashModeOn) {
                Camera.Parameters p = cam.getParameters();
                p.setFlashMode(Camera.Parameters.FLASH_MODE_OFF);
                cam.setParameters(p);
            }
            MediaScannerConnection.scanFile(this, new String[]{path}, new String[]{"video/mp4"}, null);
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR);
        } else { //is not recording
            Camera.Parameters p = cam.getParameters();

            if (isFlashModeOn) {
                p.setFlashMode(Camera.Parameters.FLASH_MODE_TORCH);
            } else {
                p.setFlashMode(Camera.Parameters.FLASH_MODE_OFF);
            }
            cam.setParameters(p);
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LOCKED);

            if (prepareVideoRecorder()) {
                recorder.start();
                isRecording = true;
                startRedDotAnimation();
                Toast.makeText(getApplication(), "Recording started!!", Toast.LENGTH_SHORT).show();
            } else {
                releaseMediaRecorder();
            }
        }
    }

    private void releaseMediaRecorder() {
        if (recorder != null) {
            recorder.reset();   // clear recorder configuration
            recorder.release(); // release the recorder object
            recorder = null;
            cam.lock();           // lock camera for later use
        }
    }

    private boolean prepareVideoRecorder() {
        recorder = new MediaRecorder();

        // Step 1: Unlock and set camera to MediaRecorder
        cam.unlock();
        recorder.setCamera(cam);

        //setorientation
        recorder.setOrientationHint(getRoatationAngle(this, currentCameraId));

        // Step 2: Set sources
        recorder.setAudioSource(MediaRecorder.AudioSource.CAMCORDER);
        recorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);

        // Step 3: Set a CamcorderProfile (requires API Level 8 or higher)
        recorder.setProfile(CamcorderProfile.get(CamcorderProfile.QUALITY_HIGH));

        path = Environment.getExternalStorageDirectory().getAbsolutePath()
                + "/wearcam/" + System.currentTimeMillis() + ".mp4";
        // Step 4: Set output file
        recorder.setOutputFile(path);

        // Step 5: Set the preview output
        recorder.setPreviewDisplay(surf.getHolder().getSurface());

        // Step 6: Prepare configured MediaRecorder
        try {
            recorder.prepare();
        } catch (IllegalStateException e) {
            Log.d(TAG, "IllegalStateException preparing MediaRecorder: " + e.getMessage());
            releaseMediaRecorder();
            return false;
        } catch (IOException e) {
            Log.d(TAG, "IOException preparing MediaRecorder: " + e.getMessage());
            releaseMediaRecorder();
            return false;
        }
        return true;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
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
            showErrorDialog(result.getErrorCode());
            mResolvingError = true;
        }

    }

    @Override
    protected void onStart() {
        super.onStart();
        if (!mResolvingError) {
            mGoogleApiClient.connect();
            Log.d(TAG, "connedted, jippieeeeeeee!!!!! ");
        }
        Uri uri = new Uri.Builder().scheme(PutDataRequest.WEAR_URI_SCHEME).path(MyConstants.PATH_IMAGE).build();
        Wearable.DataApi.deleteDataItems(mGoogleApiClient, uri).setResultCallback(new ResultCallback<DataApi.DeleteDataItemsResult>() {
            @Override
            public void onResult(DataApi.DeleteDataItemsResult deleteDataItemsResult) {
                Log.d(TAG, "onResult of deleting /image: " + deleteDataItemsResult.getStatus().toString());
            }
        });
    }

    @Override
    protected void onStop() {
        super.onStop();
        mGoogleApiClient.disconnect();
        Log.d(TAG, "disconnedted,  :( :( :( :( :(!!!!! ");
        Wearable.MessageApi.removeListener(mGoogleApiClient, this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        Wearable.DataApi.addListener(mGoogleApiClient, this);
        mGoogleApiClient.connect();
        Log.d(TAG, "connect called in onResume Method");

        if (mOrientationEventListener == null) {
            mOrientationEventListener = new OrientationEventListener(this, SensorManager.SENSOR_DELAY_NORMAL) {
                @Override
                public void onOrientationChanged(int _rot) {
                    int temp = _rot % 360;
                    if (temp > 315 || temp <= 45) {
                        if (currentCameraId == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                            angleRotateMatrix = 180;
                        } else {
                            angleRotateMatrix = 0;
                        }
                    } else if (temp > 45 && temp <= 135) {
                        angleRotateMatrix = 90;
                    } else if (temp > 135 && temp <= 225) {
                        angleRotateMatrix = 180;
                    } else if (temp > 225 && temp <= 315) {
                        angleRotateMatrix = 270;
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
        super.onPause();
        Wearable.MessageApi.removeListener(mGoogleApiClient, this);
        mGoogleApiClient.disconnect();
        mOrientationEventListener.disable();
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
        destroyCam();

        mGoogleApiClient.disconnect();
        Log.d(TAG, "disconnedted,  :( :( :( :( :(!!!!! ");
        Wearable.MessageApi.removeListener(mGoogleApiClient, this);
    }


    private static Asset createAssetFromBitmap(Bitmap bitmap) {
        ByteArrayOutputStream byteStream = null;
        try {
            byteStream = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.WEBP, 30, byteStream);
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
        if (!isGalleryModeOn && camera != null) {
            final byte[] arr = data.clone();
            YuvImage temp = new YuvImage(arr, camera.getParameters().getPreviewFormat(), camera.getParameters().getPictureSize().width, camera.getParameters().getPictureSize().height, null);
            ByteArrayOutputStream os = new ByteArrayOutputStream();
            temp.compressToJpeg(new Rect(0, 0, temp.getWidth(), temp.getHeight()), QUALITY_IN_PERCENT, os);
            Bitmap preview = Bitmap.createScaledBitmap(BitmapFactory.decodeByteArray(os.toByteArray(), 0, os.toByteArray().length), IMG_SIZE, IMG_SIZE, false);
            Matrix m = new Matrix();
            m.postRotate(angleRotateMatrix + anglePreview);
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
                PutDataMapRequest dataMap = PutDataMapRequest.create(MyConstants.PATH_IMAGE);
                dataMap.getDataMap().putLong(MyConstants.DATA_ITEM_TIMESTAMP, System.currentTimeMillis());
                dataMap.getDataMap().putAsset(MyConstants.DATA_ITEM_IMAGE, asset);
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
    }

    @Override
    public void onMessageReceived(MessageEvent messageEvent) {
        final String temp = messageEvent.getPath();

        Log.d(TAG, "received path:  " + temp);

        if (temp.equals(MyConstants.PATH_CAM)) {
            isGalleryModeOn = false;
            changeView();

        } else if (temp.equals(MyConstants.PATH_GALLERY)) {
            isGalleryModeOn = true;
            changeView();
            sendStoredImageToPhone(index);
        } else if (temp.equals(MyConstants.PATH_STOP)) {
            finish();
        } else if (temp.equals(MyConstants.PATH_TAKE_PIC)) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    takePicture();
                }
            });
        } else if (temp.equals(MyConstants.PATH_FLASH)) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    flashLightAction();
                }
            });
        } else if (temp.equals(MyConstants.PATH_CHANGE_CAM)) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    changeCamera();
                }
            });
        } else if (temp.equals(MyConstants.PATH_TAKE_VID)) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    takeVideo();
                }
            });
        } else if (temp.equals(MyConstants.PATH_NEXT_PIC)) {
            sendStoredImageToPhone((++index) % imagePaths.size());
        } else if (temp.equals(MyConstants.PATH_PREV_PIC)) {
            sendStoredImageToPhone((--index) % imagePaths.size());
        }
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(getApplicationContext(), temp, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void sendStoredImageToPhone(int _index) {
        //for (int i = 0; i < imagePaths.size(); i++) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        Bitmap bitmap = BitmapFactory.decodeFile(imagePaths.get(_index), options);
        Bitmap resizedBitmap = Bitmap.createScaledBitmap(bitmap, bitmap.getWidth() / 2, bitmap.getHeight() / 2, true);
        Asset asset = createAssetFromBitmap(resizedBitmap);
        PutDataMapRequest dataMap = PutDataMapRequest.create(MyConstants.PATH_GALLERY_IMAGE);
        dataMap.getDataMap().putAsset(MyConstants.DATA_ITEM_IMAGE, asset);
        dataMap.getDataMap().putLong(MyConstants.DATA_ITEM_TIMESTAMP, System.currentTimeMillis());
        PutDataRequest request = dataMap.asPutDataRequest();

        PendingResult<DataApi.DataItemResult> pendingResult = Wearable.DataApi
                .putDataItem(mGoogleApiClient, request);
        pendingResult.setResultCallback(new ResultCallback<DataApi.DataItemResult>() {
            @Override
            public void onResult(DataApi.DataItemResult dataItemResult) {
                Log.i(TAG, "onResult of sending data: " + dataItemResult.getStatus());
            }
        });
        //}
    }

    public void changeView() {
        if (isGalleryModeOn) {
            if (isRecording) {
                recorder.stop();
                isRecording = false;
            }
            destroyCam();
        } else {
            setupCam();
        }
    }

    private boolean setupCam() {
        surf = (SurfaceView) findViewById(R.id.surfaceView);
        if (surf != null) {
            surf.setOnTouchListener(this);
            mHolder = surf.getHolder();
            if (mHolder != null) {
                mHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
                mHolder.addCallback(this);
            } else {
                return false;
            }
        } else {
            return false;
        }

        try {
            cam = Camera.open(currentCameraId);
            if (cam != null) {
                int tempAngle = getRoatationAngle(this, currentCameraId);
                cam.setDisplayOrientation(tempAngle);
                cam.setPreviewCallback(this);
                cam.setPreviewDisplay(mHolder);
                cam.startPreview();
                return true;
            } else {
                return false;
            }
        } catch (Exception _e) {
            return false;
        }
    }

    private void destroyCam() {
        if (surf != null) {
            surf.getHolder().removeCallback(this);
            surf.setOnTouchListener(null);
            surf = null;
        }
        if (cam != null) {
            cam.stopPreview();
            cam.setPreviewCallback(null);
            cam.release();
            cam = null;
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

    public boolean onTouchEvent(MotionEvent event) {
        // Get the pointer ID
        Camera.Parameters params = cam.getParameters();
        int action = event.getAction();

        if (event.getPointerCount() > 1) {
            // handle multi-touch events
            if (action == MotionEvent.ACTION_POINTER_DOWN) {
                mDist = getFingerSpacing(event);
            } else if (action == MotionEvent.ACTION_MOVE && params.isZoomSupported()) {
                cam.cancelAutoFocus();
                handleZoom(event, params);
            }
        } else {
            // handle single touch events
            if (action == MotionEvent.ACTION_UP) {
                handleFocus(event, params);
            }
        }
        return true;
    }

    private void handleZoom(MotionEvent event, Camera.Parameters params) {
        int maxZoom = params.getMaxZoom();
        int zoom = params.getZoom();
        float newDist = getFingerSpacing(event);
        if (newDist > mDist) {
            //zoom in
            if (zoom < maxZoom)
                zoom += 2;
        } else if (newDist < mDist) {
            //zoom out
            if (zoom > 0)
                zoom -= 2;
        }
        mDist = newDist;
        params.setZoom(zoom);
        if (cam != null) {
            cam.setParameters(params);
        }
    }

    public void handleFocus(MotionEvent event, Camera.Parameters params) {
        int pointerId = event.getPointerId(0);
        int pointerIndex = event.findPointerIndex(pointerId);
        // Get the pointer's current position
        float x = event.getX(pointerIndex);
        float y = event.getY(pointerIndex);

        List<String> supportedFocusModes = params.getSupportedFocusModes();
        if (supportedFocusModes != null && supportedFocusModes.contains(Camera.Parameters.FOCUS_MODE_AUTO)) {
            cam.autoFocus(new Camera.AutoFocusCallback() {
                @Override
                public void onAutoFocus(boolean b, Camera camera) {
                    // currently set to auto-focus on single touch
                }
            });
        }
    }

    /**
     * Determine the space between the first two fingers
     */
    private float getFingerSpacing(MotionEvent event) {
        // ...
        float x = event.getX(0) - event.getX(1);
        float y = event.getY(0) - event.getY(1);
        return (float) Math.sqrt(x * x + y * y);
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        onTouchEvent(event);
        return true;
    }

    /**
     * Get Rotation Angle
     *
     * @param mContext
     * @param cameraId probably front cam
     * @return angel to rotate
     */
    public static int getRoatationAngle(Activity mContext, int cameraId) {
        android.hardware.Camera.CameraInfo info = new android.hardware.Camera.CameraInfo();
        android.hardware.Camera.getCameraInfo(cameraId, info);
        int rotation = mContext.getWindowManager().getDefaultDisplay().getRotation();
        int degrees = 0;
        switch (rotation) {
            case Surface.ROTATION_0:
                degrees = 0;
                break;
            case Surface.ROTATION_90:
                degrees = 90;
                break;
            case Surface.ROTATION_180:
                degrees = 180;
                break;
            case Surface.ROTATION_270:
                degrees = 270;
                break;
        }
        int result;
        if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            result = (info.orientation + degrees) % 360;
            result = (360 - result) % 360; // compensate the mirror
        } else { // back-facing
            result = (info.orientation - degrees + 360) % 360;
        }
        return result;
    }

    public static Bitmap rotate(Bitmap bitmap, int degree) {
        int w = bitmap.getWidth();
        int h = bitmap.getHeight();

        Matrix mtx = new Matrix();
        mtx.postRotate(degree);

        return Bitmap.createBitmap(bitmap, 0, 0, w, h, mtx, true);
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        try {
            if (cam != null) {
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
                            //camera.startPreview();
                        }
                    }
                });
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        int angleToRotate = getRoatationAngle(this, currentCameraId);
        if (cam != null) {
            cam.setDisplayOrientation(angleToRotate);
        }
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        if (isRecording) {
            recorder.stop();
            isRecording = false;
        }
        //recorder.release();
    }

    @Override
    public void onPictureTaken(byte[] data, Camera camera) {
        if (data != null) {
            int angleToRotate = getRoatationAngle(MainActivity.this, currentCameraId);
            Bitmap orignalImage = BitmapFactory.decodeByteArray(data, 0, data.length);
            // TODO: front camera portrait picture is reverted
            Bitmap bitmap = rotate(orignalImage, angleToRotate);

            if (bitmap != null) {

                File file = new File(Environment.getExternalStorageDirectory() + "/Wearcam");
                if (!file.isDirectory()) {
                    file.mkdir();
                }

                file = new File(Environment.getExternalStorageDirectory() + "/Wearcam", System.currentTimeMillis() + ".jpg");

                try {
                    FileOutputStream fileOutputStream = new FileOutputStream(file);
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 100, fileOutputStream);
                    fileOutputStream.flush();
                    fileOutputStream.close();

                    MediaScannerConnection.scanFile(this, new String[]{file.getPath()}, new String[]{"image/jpeg"}, null);
                } catch (IOException e) {
                    e.printStackTrace();
                } catch (Exception exception) {
                    exception.printStackTrace();
                }
            }
            cam.startPreview();
        }
    }

    @Override
    public void onShutter() {
        AudioManager mgr = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        mgr.playSoundEffect(AudioManager.FLAG_PLAY_SOUND);
    }
}
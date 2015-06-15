package mc.fhooe.at.wearcam;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.hardware.Camera;
import android.media.AudioManager;
import android.media.CamcorderProfile;
import android.media.Image;
import android.media.MediaRecorder;
import android.media.MediaScannerConnection;
import android.os.Bundle;
import android.os.Environment;
import android.support.v4.view.ViewPager;
import android.util.Log;
import android.view.Display;
import android.view.Menu;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.LinearInterpolator;
import android.view.animation.ScaleAnimation;
import android.widget.Button;
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
import java.util.Date;
import java.util.List;

public class MainActivity extends Activity implements DataApi.DataListener, GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener, Camera.PreviewCallback, MessageListener, NodeApi.NodeListener, SurfaceHolder.Callback, Camera.PictureCallback, Camera.ShutterCallback, View.OnTouchListener {
    private GoogleApiClient mGoogleApiClient;
    private Button pictureButton, videoButton, galleryButton;
    private String TAG = "PhoneTag", path = null;
    private int i, SCREEN = 90, currentCameraId = Camera.CameraInfo.CAMERA_FACING_BACK;
    private Camera cam;
    private static final int REQUEST_RESOLVE_ERROR = 1001;
    private boolean mResolvingError = false, isRecording = false, isFlashModeOn = false, isGalleryModeOn = false;
    private SurfaceView surf;
    private SurfaceHolder mHolder;
    private Node mNode;
    private MediaRecorder recorder;
    private ImageView redDotView;
    private ImageButton flashButton, changeCamButton;
    private float mDist;

    //----------------------------------------------------
    // SD card image directory
    public static final String PHOTO_ALBUM = "wearcam";

    // supported file formats
    public static final List<String> FILE_EXTN = Arrays.asList("jpg", "jpeg",
            "png");

    private Utils utils;
    private ArrayList<String> imagePaths = new ArrayList<String>();


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addApi(Wearable.API)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .build();

        Wearable.MessageApi.addListener(mGoogleApiClient, this);

        pictureButton = (Button) findViewById(R.id.pictureButton);
        videoButton = (Button) findViewById(R.id.videoButton);
        flashButton = (ImageButton) findViewById(R.id.flashlightButton);
        changeCamButton = (ImageButton) findViewById(R.id.changeCameraButton);
        galleryButton = (Button) findViewById(R.id.galleryButton);

        pictureButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                takePicture();
            }
        });
        videoButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                takeVideo();
            }
        });

        redDotView = (ImageView) findViewById(R.id.redDotPhoneImage);
        flashButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                flashLightAction();
            }
        });
        changeCamButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                changeCamera();
            }
        });

        i = 0;
        cam = Camera.open();
        cam.setPreviewCallback(this);


        surf = (SurfaceView) findViewById(R.id.surfaceView);
        surf.setOnTouchListener(this);
        mHolder = surf.getHolder();
        mHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
        mHolder.addCallback(this);
        flashButton.setAlpha(0.5f);

        utils = new Utils(this);
        // loading all image paths from SD card
        imagePaths = utils.getFilePaths();
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

        if (cam != null) {
            cam.stopPreview();
            cam.setPreviewCallback(null);
            cam.release();
            cam = null;
        }

        if (currentCameraId == Camera.CameraInfo.CAMERA_FACING_BACK)
            currentCameraId = Camera.CameraInfo.CAMERA_FACING_FRONT;
        else {
            currentCameraId = Camera.CameraInfo.CAMERA_FACING_BACK;
        }

        try {
            cam = Camera.open(currentCameraId);
            cam.setPreviewCallback(this);
            int angleToRotate = getRoatationAngle(this, currentCameraId);
            cam.setDisplayOrientation(angleToRotate);
            cam.setPreviewDisplay(mHolder);
            cam.startPreview();
        } catch (Exception e) {
            e.printStackTrace();
        }

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
        recorder.setOrientationHint(getRoatationAngle(this,currentCameraId));

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
        if (!mResolvingError) {
            mGoogleApiClient.connect();
            Log.d(TAG, "connedted, jippieeeeeeee!!!!! ");
        }
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

        mGoogleApiClient.disconnect();
        Log.d(TAG, "disconnedted,  :( :( :( :( :(!!!!! ");
        Wearable.MessageApi.removeListener(mGoogleApiClient, this);
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
    public void onMessageReceived(MessageEvent messageEvent) {

        final String temp = messageEvent.getPath();

        Log.d("LOG", "received path:  " + temp);

        if (temp.equals("/cam")) {
            isGalleryModeOn = false;
            changeView();
        } else if (temp.equals("/gallery")) {
            isGalleryModeOn = true;
            changeView();
            sendStoredImagesToPhone();
        }

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(getApplicationContext(), temp, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void sendStoredImagesToPhone() {

        for (int i = 0; i < imagePaths.size(); i++) {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inPreferredConfig = Bitmap.Config.ARGB_8888;
            Bitmap bitmap = BitmapFactory.decodeFile(imagePaths.get(i), options);

            Asset asset = createAssetFromBitmap(bitmap);

            PutDataMapRequest dataMap = PutDataMapRequest.create("/galleryimage");

            dataMap.getDataMap().putLong("time2", new Date().getTime());
            dataMap.getDataMap().putAsset("image", asset);
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
    }

    public void changeView() {
        if (isGalleryModeOn) {

            if (isRecording) {
                recorder.stop();
                isRecording = false;
            }
            cam.setPreviewCallback(null);
            surf.getHolder().removeCallback(this);
            cam.stopPreview();
            cam.release();
            surf.setOnTouchListener(null);

        } else {

            cam = Camera.open();
            cam.setPreviewCallback(this);
            surf = (SurfaceView) findViewById(R.id.surfaceView);
            surf.setOnTouchListener(this);
            mHolder = surf.getHolder();
            mHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
            mHolder.addCallback(this);

        }
    }

    @Override
    public void onPeerConnected(Node _node) {
        mNode = _node;
    }

    @Override
    public void onPeerDisconnected(Node node) {
        // TODO
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
                        //camera.startPreview();
                    }
                }
            });
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        int angleToRotate = getRoatationAngle(this, Camera.CameraInfo.CAMERA_FACING_FRONT);
        cam.setDisplayOrientation(angleToRotate);
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
            camera.startPreview();
        }

    }

    @Override
    public void onShutter() {
        AudioManager mgr = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        mgr.playSoundEffect(AudioManager.FLAG_PLAY_SOUND);
    }

    @Override
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
        cam.setParameters(params);
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
}
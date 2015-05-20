package mc.fhooe.ac.wearcam;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.hardware.Camera;
import android.os.AsyncTask;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.Asset;
import com.google.android.gms.wearable.MessageApi;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.NodeApi;
import com.google.android.gms.wearable.PutDataRequest;
import com.google.android.gms.wearable.Wearable;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Vector;


public class MainActivity extends ActionBarActivity implements Camera.PreviewCallback {

    private GoogleApiClient mGoogleApiClient;
    Button button;
    Camera cam;
    SurfaceView surf;
    SurfaceHolder mHolder;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addApi(Wearable.API)
                .build();
        mGoogleApiClient.connect();

        cam = Camera.open();
        cam.setDisplayOrientation(90);
        cam.setPreviewCallback(this);
        surf = (SurfaceView) findViewById(R.id.surfaceView);
        mHolder = surf.getHolder();
        mHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);


        mHolder.addCallback(new SurfaceHolder.Callback2(){
            @Override
            public void surfaceCreated(SurfaceHolder holder) {

                try {
                    cam.setPreviewDisplay(holder);
                    cam.startPreview();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {

            }

            @Override
            public void surfaceDestroyed(SurfaceHolder holder) {

                if (mHolder.getSurface() == null){
                    // preview surface does not exist
                    return;
                }

                // stop preview before making changes
                try {
                    cam.stopPreview();
                } catch (Exception e){
                    // ignore: tried to stop a non-existent preview
                }
                // set preview size and make any resize, rotate or
                // reformatting changes here
                // start preview with new settings
                try {
                    cam.setPreviewDisplay(mHolder);
                    cam.startPreview();

                } catch (Exception e){
                    Log.d("TAG", "Error starting camera preview: " + e.getMessage());
                }
            }

            @Override
            public void surfaceRedrawNeeded(SurfaceHolder holder) {

            }
        });
        
        button = (Button) findViewById(R.id.mobileButton);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onButtonClicked(v);
            }
        });
    }

    private static Asset createAssetFromBitmap(Bitmap bitmap){
        final ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, byteStream);
        return Asset.createFromBytes(byteStream.toByteArray());
    }

    public void onButtonClicked(View target) {
        if (mGoogleApiClient == null)
            return;

        final PendingResult<NodeApi.GetConnectedNodesResult> nodes = Wearable.NodeApi.getConnectedNodes(mGoogleApiClient);
        nodes.setResultCallback(new ResultCallback<NodeApi.GetConnectedNodesResult>() {
            @Override
            public void onResult(NodeApi.GetConnectedNodesResult result) {
                final List<Node> nodes = result.getNodes();
                if (nodes != null) {
                    for (int i = 0; i < nodes.size(); i++) {
                        final Node node = nodes.get(i);
                        // You can just send a message
                        Wearable.MessageApi.sendMessage(mGoogleApiClient, node.getId(), "/MOBILE", null);
                    }
                }
            }
        });
    }

    @Override
    public void onPreviewFrame(byte[] data, Camera camera) {
        Log.i("LOG","onPreviewFrameCalled");
        YuvImage temp = new YuvImage(data, camera.getParameters().getPreviewFormat(), camera.getParameters().getPictureSize().width, camera.getParameters().getPictureSize().height, null);
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        temp.compressToJpeg(new Rect(0, 0, temp.getWidth(), temp.getHeight()), 80, os);
        Bitmap preview = BitmapFactory.decodeByteArray(os.toByteArray(), 0, os.toByteArray().length);

        //Bitmap bitmap = BitmapFactory.decodeResource(getResources(), R.drawable.image);
        Asset asset = createAssetFromBitmap(preview);
        PutDataRequest request = PutDataRequest.create("/image");
        request.putAsset("profileImage", asset);
        Wearable.DataApi.putDataItem(mGoogleApiClient, request);

    }
}
package mc.fhooe.at.wearcam;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.view.ViewPager;
import android.support.wearable.view.DotsPageIndicator;
import android.support.wearable.view.GridPagerAdapter;
import android.support.wearable.view.GridViewPager;
import android.support.wearable.view.WatchViewStub;
import android.util.Log;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationSet;
import android.view.animation.LinearInterpolator;
import android.view.animation.ScaleAnimation;
import android.widget.Gallery;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.Toast;

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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Vector;
import java.util.concurrent.TimeUnit;
import java.util.zip.Inflater;

public class MainActivity extends Activity implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener, DataApi.DataListener, MessageApi.MessageListener, View.OnTouchListener,
        GestureDetector.OnGestureListener, GestureDetector.OnDoubleTapListener, NodeApi.NodeListener, View.OnClickListener, GridViewPager.OnPageChangeListener{
    private ImageView mImageView;
    private GoogleApiClient mGoogleApiClient;
    private String TAG = "WearTAG";
    private static final int REQUEST_RESOLVE_ERROR = 1001;
    private boolean mResolvingError = false, animationRunning = false;
    private Uri uri;
    private Vector<Node> mNodeList;
    private ImageButton imgBtn1 = null, imgBtn2 = null;

    private GridViewPager pager;
    private DotsPageIndicator dots;
    private int column=2;
    private int row=1;
    private GestureDetector gestures;
    private ImageView redImageView;
    private ViewGroup container;
    private LayoutInflater inflater;

    // SD card image directory
    public static final String PHOTO_ALBUM = "wearcam";

    // supported file formats
    public static final List<String> FILE_EXTN = Arrays.asList("jpg", "jpeg",
            "png");

    private Utils utils;

    private ArrayList<Bitmap> galleryBitmaps = new ArrayList<Bitmap>();
    private GalleryViewAdapter adapter;
    private ViewPager viewPager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        final WatchViewStub stub = (WatchViewStub) findViewById(R.id.watch_view_stub);
        stub.setOnLayoutInflatedListener(new WatchViewStub.OnLayoutInflatedListener() {
            @Override
            public void onLayoutInflated(WatchViewStub stub) {
                pager = (GridViewPager) findViewById(R.id.pager);
                dots = (DotsPageIndicator) findViewById(R.id.page_indicator);
                dots.setPager(pager);
                redImageView = (ImageView) findViewById(R.id.redDotView);

                //mImageView = (ImageView) stub.findViewById(R.id.imageView);
                //imgBtn1 = (ImageButton) findViewById(R.id.imageButton1);
                //imgBtn2 = (ImageButton) findViewById(R.id.imageButton2);
                updateUI();
            }
        });

        gestures = new GestureDetector(MainActivity.this,
                this);

        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(Wearable.API)
                .build();
        mNodeList = new Vector();
        uri = null;

        // Gridview adapter
        adapter = new GalleryViewAdapter(MainActivity.this,galleryBitmaps);
        viewPager = new ViewPager(getApplicationContext());
        viewPager.setAdapter(adapter);

    }

    void startRedDotAnimation(){
        redImageView.setVisibility(ImageView.VISIBLE);
        ScaleAnimation anim = new ScaleAnimation(1.0f,1.4f,1.0f,1.4f,25,25);
        anim.setInterpolator(new LinearInterpolator());
        anim.setRepeatCount(Animation.INFINITE);
        anim.setRepeatMode(Animation.REVERSE);
        anim.setDuration(1000);
        redImageView.startAnimation(anim);
        animationRunning = true;
    }

    void stopRedDotAnimation(){
        animationRunning = false;
        redImageView.setAnimation(null);
        redImageView.animate();
        redImageView.setVisibility(ImageView.INVISIBLE);
    }

    void updateUI() {
        pager.setAdapter(new ImageAdapter(getApplication().getApplicationContext()));
        pager.setOnPageChangeListener(this);
    }


    @Override
    protected void onStart() {
        super.onStart();
        if (!mResolvingError) {
            mGoogleApiClient.connect();
            Log.d(TAG, "connected to play services");
        }
        Wearable.MessageApi.addListener(mGoogleApiClient, this);
        Wearable.DataApi.addListener(mGoogleApiClient, this);
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
        Wearable.DataApi.removeListener(mGoogleApiClient, this);
        Log.d(TAG, "disconnected in onPause Method");
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
                        reloadImageView(bitmap,mImageView);
                    }

                    Log.i("RECEIVEDIMAGE","TYPE STREAM STREAM STREAM");

                }else if("/galleryimage".equals(path)){

                    DataMapItem item = DataMapItem.fromDataItem(event.getDataItem());
                    Asset asset = item.getDataMap().getAsset("image");
                    Bitmap bitmap = loadBitmapFromAsset(asset);
                    if (bitmap != null) {

                        galleryBitmaps.add(bitmap);
                        adapter.notifyDataSetChanged();
                    }

                    Log.i("RECEIVEDIMAGE","TYPE GALLERY GALLERY GALLERY ");
                }
            } else if (event.getType() == DataEvent.TYPE_DELETED) {
                Log.d(TAG, "onDataChanged - TYPE_DELETED");
            }
        }
    }

    private void reloadImageView(Bitmap _bitmap, final ImageView _imageView) {
        final Bitmap bit = _bitmap;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                _imageView.setImageBitmap(bit);
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

    @Override
    public void onPageScrolled(int i, int i1, float v, float v1, int i2, int i3) {
    }

    @Override
    public void onPageSelected(int i, int i1) {

        if(i==0 && i1 ==0){
            sendToPhones("/cam");
            Toast.makeText(getApplication(),"cam view",Toast.LENGTH_SHORT).show();

        }else if(i == 0 && i1 ==1) {
            sendToPhones("/gallery");
            Toast.makeText(getApplication(), "gallery view", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onPageScrollStateChanged(int i) {
    }

    @Override
    public boolean onDown(MotionEvent e) {
        //Toast.makeText(getApplication(),"onDown",Toast.LENGTH_SHORT).show();
        return true;
    }

    @Override
    public void onShowPress(MotionEvent e) {
        //Toast.makeText(getApplication(),"onShowpress",Toast.LENGTH_SHORT).show();
    }

    @Override
    public boolean onSingleTapUp(MotionEvent e) {
        //Toast.makeText(getApplication(),"onSingleTapup",Toast.LENGTH_SHORT).show();
        //after releasing single tap gesture
        return true;
    }

    @Override
    public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
        return true;
    }

    @Override
    public void onLongPress(MotionEvent e) {
        Toast.makeText(getApplication(),"Long Press",Toast.LENGTH_SHORT).show();
        if(!animationRunning){
            startRedDotAnimation();
        }
    }


    @Override
    public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
        return true;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        return gestures.onTouchEvent(event);
    }

    @Override
     public boolean onTouch(View v, MotionEvent event) {
        return false;
    }

    @Override
    public boolean onSingleTapConfirmed(MotionEvent e) {
        Toast.makeText(getApplication(),"Single Tap",Toast.LENGTH_SHORT).show();
        if(animationRunning){
            stopRedDotAnimation();
        }else{
            // sendtakepicture notification
        }
        return true;
    }

    @Override
    public boolean onDoubleTap(MotionEvent e) {
        Toast.makeText(getApplication(),"Double Tap",Toast.LENGTH_SHORT).show();
        return false;
    }

    @Override
    public boolean onDoubleTapEvent(MotionEvent e) {
        return false;
    }

    public class ImageAdapter extends GridPagerAdapter implements View.OnTouchListener {
       final Context mContext;

        public ImageAdapter(final Context context){
            mContext = context;
        }

        @Override
        public int getRowCount() { // zeile
            return row;
        }

        @Override
        public int getColumnCount(int i) { //spalte =3
            return column;
        }

        @Override
        public int getCurrentColumnForRow(int row, int currentColumn) {
            return currentColumn;
        }

        @Override
        public Object instantiateItem(ViewGroup viewGroup, int i, int i1) {
            
            ImageView imageView;
            imageView = new ImageView(mContext);

            if(i==0&&i1==0){
                mImageView = imageView;
                mImageView.setOnTouchListener(this);
                viewGroup.addView(imageView);
                galleryBitmaps = null;
                galleryBitmaps = new ArrayList<Bitmap>();
                return imageView;

            }else {

                viewGroup.addView(viewPager);
                return viewPager;
            }
        }

        @Override
        public void destroyItem(ViewGroup viewGroup, int i, int i1, Object o) {
             viewGroup.removeView((View) o);
        }

        @Override
        public boolean isViewFromObject(View view, Object o) {
            return view.equals(o);
        }


        @Override
        public boolean onTouch(View v, MotionEvent event) {
            if(v==mImageView){
                if(gestures.onTouchEvent(event)){
                    return true;
                }
            }else{
                //touch event for 2nd imageView
            }
            return false;
        }
    }
}
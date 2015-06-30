package net.felixgruber.wearcam;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.CountDownTimer;
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
import android.view.animation.LinearInterpolator;
import android.view.animation.ScaleAnimation;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
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
import com.google.android.gms.wearable.PutDataRequest;
import com.google.android.gms.wearable.Wearable;

import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Vector;
import java.util.concurrent.TimeUnit;

import mc.fhooe.at.wearcam.R;

public class MainActivity extends Activity implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener, DataApi.DataListener, MessageApi.MessageListener, View.OnTouchListener, GestureDetector.OnGestureListener, GestureDetector.OnDoubleTapListener, NodeApi.NodeListener, View.OnClickListener, GridViewPager.OnPageChangeListener {
    private ImageView mImageViewCam, mImageViewGallery;
    private GoogleApiClient mGoogleApiClient;
    private String TAG = "WearTAG";
    private static final int REQUEST_RESOLVE_ERROR = 1001;
    private boolean mResolvingError = false, animationRunning = false, flashOn = false, isGalleryModeOn;
    private Uri uri;
    private Vector<Node> mNodeList;
    private ImageButton imgBtn1 = null, imgBtn2 = null;
    private Button counterBtn = null;
    private GridViewPager pager;
    private DotsPageIndicator dots;
    private int column = 2, row = 1, clockCounter = 0;
    private GestureDetector gestureDetector;
    private ImageView redImageView;
    private ViewGroup container;
    private LayoutInflater inflater;
    public static final List<String> FILE_EXTN = Arrays.asList("jpg", "jpeg", "png");// supported file formats
    private GalleryViewAdapter adapter;
    private ViewPager viewPager;
    private TextView upTimeTextView;

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

                imgBtn1 = (ImageButton) findViewById(R.id.imageButton1);
                imgBtn2 = (ImageButton) findViewById(R.id.imageButton2);
                counterBtn = (Button) findViewById(R.id.imageButton3);
                upTimeTextView = (TextView) findViewById(R.id.upTimeCounterWear);
                updateUI();
            }
        });

        gestureDetector = new GestureDetector(MainActivity.this, this);

        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(Wearable.API)
                .build();
        mNodeList = new Vector();
        uri = null;

        // Gridview adapter
        adapter = new GalleryViewAdapter(MainActivity.this);
        viewPager = new ViewPager(getApplicationContext());
        if (viewPager != null) {
            viewPager.setAdapter(adapter);
            //viewPager.setOnTouchListener(this);
        }

        clockCounter = 0;
        isGalleryModeOn = false;
    }

    void startRedDotAnimation() {
        redImageView.setVisibility(ImageView.VISIBLE);
        ScaleAnimation anim = new ScaleAnimation(1.0f, 1.4f, 1.0f, 1.4f, 25, 25);
        anim.setInterpolator(new LinearInterpolator());
        anim.setRepeatCount(Animation.INFINITE);
        anim.setRepeatMode(Animation.REVERSE);
        anim.setDuration(1000);
        redImageView.startAnimation(anim);
        animationRunning = true;
        startUpTimeCounter();
    }

    void stopRedDotAnimation() {
        animationRunning = false;
        redImageView.setAnimation(null);
        redImageView.animate();
        redImageView.setVisibility(ImageView.INVISIBLE);
        upTimeTextView.setVisibility(View.INVISIBLE);
    }

    void updateUI() {
        pager.setAdapter(new ImageAdapter(getApplication().getApplicationContext()));
        if (pager != null) {
            pager.setOnPageChangeListener(this);
        }
        if (imgBtn1 != null) {
            imgBtn1.setOnClickListener(this);
        }
        if (imgBtn2 != null) {
            imgBtn2.setOnClickListener(this);
        }
        if (counterBtn != null) {
            counterBtn.setOnClickListener(this);
        }

        if(upTimeTextView != null){
            upTimeTextView.setVisibility(View.INVISIBLE);
        }
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
        deleteOldDataItems();
        Wearable.DataApi.addListener(mGoogleApiClient, this);
        mGoogleApiClient.connect();
        Log.d(TAG, "connect called in onResume Method");
    }

    @Override
    protected void onPause() {
        deleteOldDataItems();
        mGoogleApiClient.disconnect();
        Wearable.DataApi.removeListener(mGoogleApiClient, this);
        Log.d(TAG, "disconnected in onPause Method");
        super.onPause();
    }


    @Override
    protected void onDestroy() {
        sendToPhones(MyConstants.PATH_STOP);
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
                    sendToPhones(MyConstants.PATH_START);
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
        sendToPhones(MyConstants.PATH_STOP);
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
                //Log.d(TAG, "onDataChanged - TYPE_CHANGED");
                uri = event.getDataItem().getUri();
                String path = uri.getPath();
                if (path.equals(MyConstants.PATH_IMAGE)) {
                    DataMapItem item = DataMapItem.fromDataItem(event.getDataItem());
                    Asset asset = item.getDataMap().getAsset(MyConstants.DATA_ITEM_IMAGE);
                    Bitmap bitmap = loadBitmapFromAsset(asset);
                    if (bitmap != null) {
                        reloadImageView(bitmap, mImageViewCam);
                    }
                    // Log.i("RECEIVEDIMAGE", "TYPE STREAM STREAM STREAM");
                } else if (path.equals(MyConstants.PATH_GALLERY_IMAGE)) {
                    DataMapItem item = DataMapItem.fromDataItem(event.getDataItem());
                    Asset asset = item.getDataMap().getAsset(MyConstants.DATA_ITEM_IMAGE);
                    Bitmap bitmap = loadBitmapFromAsset(asset);
                    if (bitmap != null && adapter != null) {
                        //adapter.setImg(bitmap);
                        reloadImageView(bitmap, mImageViewGallery);
                        //adapter.notifyDataSetChanged();
                    }
                    Log.i("RECEIVEDIMAGE", "TYPE GALLERY GALLERY GALLERY ");
                }
            } else if (event.getType() == DataEvent.TYPE_DELETED) {
                Log.d(TAG, "onDataChanged - TYPE_DELETED");
            }
        }
    }

    private void reloadImageView(final Bitmap _bitmap, final ImageView _imageView) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (_imageView != null) {
                    _imageView.setImageBitmap(_bitmap);
                }
            }
        });
    }

    public Bitmap loadBitmapFromAsset(Asset asset) {
        if (asset == null) {
            throw new IllegalArgumentException("Asset must be non-null");
        }

        ConnectionResult result = mGoogleApiClient.blockingConnect(100, TimeUnit.MILLISECONDS);
        if (!result.isSuccess()) {
            return null;
        }

        InputStream assetInputStream = Wearable.DataApi.getFdForAsset(mGoogleApiClient, asset).await().getInputStream();

        if (assetInputStream == null) {
            Log.w(TAG, "Requested an unknown Asset.");
            return null;
        }
        return BitmapFactory.decodeStream(assetInputStream);
    }

    @Override
    public void onMessageReceived(MessageEvent _messageEvent) {
        final String temp = _messageEvent.getPath();
        if (temp.equals(MyConstants.PATH_STOP)) {
            finish();
        }else if (temp.equals(MyConstants.PATH_START_RECORDING)) {
            Log.i(TAG,"wear path start recording received");
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    startRedDotAnimation();
                }
            });

            //TODO: implement counter here
        }else if(temp.equals(MyConstants.PATH_STOP_RECORDING)){
            Log.i(TAG,"wear path stop recording received");
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    stopRedDotAnimation();
                }
            });
            //TODO: implement counter here
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
                sendToPhones(MyConstants.PATH_FLASH);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(getApplicationContext(), "Flash toggled", Toast.LENGTH_SHORT).show();
                        /* wenn user am handy aufm flash button drückt is des verkehrt, darum auskommentiert
                        if (flashOn) {
                            Toast.makeText(getApplicationContext(), "Flash off", Toast.LENGTH_SHORT).show();
                            flashOn = false;
                        } else {
                            Toast.makeText(getApplicationContext(), "Flash on", Toast.LENGTH_SHORT).show();
                            flashOn = true;
                        }
                        */
                    }
                });
                break;

            case R.id.imageButton2:
                sendToPhones(MyConstants.PATH_CHANGE_CAM);
                deleteOldDataItems();
                break;

            case R.id.imageButton3:
                if (clockCounter < 5) {
                    clockCounter++;
                    counterBtn.setBackground(null);
                    counterBtn.setText(String.valueOf(clockCounter));
                } else {
                    clockCounter = 0;
                    counterBtn.setBackground(getResources().getDrawable(android.R.drawable.ic_menu_recent_history));
                    counterBtn.setText("");
                }
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
        if (i == 0 && i1 == 0) {
            sendToPhones(MyConstants.PATH_CAM);
            isGalleryModeOn = false;
            imgBtn1.setActivated(true);
            imgBtn1.setVisibility(View.VISIBLE);
            imgBtn2.setActivated(true);
            imgBtn2.setVisibility(View.VISIBLE);
            counterBtn.setActivated(true);
            counterBtn.setVisibility(View.VISIBLE);

        } else if (i == 0 && i1 == 1) {
            isGalleryModeOn = true;
            sendToPhones(MyConstants.PATH_GALLERY);
            imgBtn1.setActivated(false);
            imgBtn1.setVisibility(View.INVISIBLE);
            imgBtn2.setActivated(false);
            imgBtn2.setVisibility(View.INVISIBLE);
            counterBtn.setActivated(false);
            counterBtn.setVisibility(View.INVISIBLE);

        }
    }

    @Override
    public void onPageScrollStateChanged(int i) {
    }

    @Override
    public boolean onDown(MotionEvent e) {
        return true;
    }

    @Override
    public void onShowPress(MotionEvent e) {
    }

    @Override
    public boolean onSingleTapUp(MotionEvent e) {
        return true;
    }

    @Override
    public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
        if (distanceY < 0) {
            sendToPhones(MyConstants.PATH_NEXT_PIC);
        } else {
            sendToPhones(MyConstants.PATH_PREV_PIC);
        }
        return true;
    }

    @Override
    public void onLongPress(MotionEvent e) {
        if (isGalleryModeOn) {
            // zeige ja/nein auswahl, if ja -> sende delete
            LayoutInflater inflater = getLayoutInflater();
            View dialoglayout = inflater.inflate(R.layout.custom_dialog, null);
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setView(dialoglayout);
            final AlertDialog dialog = builder.create();
            dialog.show();

            View cancelButton = dialoglayout.findViewById(R.id.cancel_btn);
            if (cancelButton != null) {
                cancelButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (dialog != null) {
                            dialog.cancel();
                        }
                    }
                });
            }
            View okButton = dialoglayout.findViewById(R.id.ok_btn);
            if (okButton != null) {
                okButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        sendToPhones(MyConstants.PATH_DELETE_PIC);
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(getApplicationContext(), "Picture deleting..", Toast.LENGTH_SHORT).show();
                            }
                        });
                        if (dialog != null) {
                            dialog.cancel();
                        }
                    }
                });
            }
        } else {
            if (!animationRunning) {
                sendToPhones(MyConstants.PATH_TAKE_VID);
                Toast.makeText(getApplication(), "Recording video", Toast.LENGTH_SHORT).show();
                startRedDotAnimation();
            }
        }
    }

    @Override
    public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
        return false;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        return super.onTouchEvent(event);
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        return true;
    }

    @Override
    public boolean onSingleTapConfirmed(MotionEvent e) {
        if (!isGalleryModeOn) {
            if (animationRunning) {
                Toast.makeText(getApplication(), "Stopping recording", Toast.LENGTH_SHORT).show();
                sendToPhones(MyConstants.PATH_TAKE_VID);
                stopRedDotAnimation();
            } else {

                if (clockCounter != 0) {
                    int time = clockCounter * 1000;
                    counterBtn.setTextColor(Color.RED);

                    new CountDownTimer(time, 1000) {
                        public void onTick(long millisUntilFinished) {
                            counterBtn.setText(String.valueOf(millisUntilFinished / 1000));
                        }

                        public void onFinish() {
                            Toast.makeText(getApplication(), "taking a picture", Toast.LENGTH_SHORT).show();
                            counterBtn.setText("");
                            counterBtn.setBackground(getResources().getDrawable(android.R.drawable.ic_menu_recent_history));
                            counterBtn.setTextColor(Color.WHITE);
                            sendToPhones(MyConstants.PATH_TAKE_PIC);
                        }
                    }.start();
                } else {
                    Toast.makeText(getApplication(), "Picture taken", Toast.LENGTH_SHORT).show();
                    sendToPhones(MyConstants.PATH_TAKE_PIC);
                }
            }
        } else { // gallery mode
            sendToPhones(MyConstants.PATH_NEXT_PIC);
        }
        return true;
    }

    @Override
    public boolean onDoubleTap(MotionEvent e) {
        if (isGalleryModeOn) {
            sendToPhones(MyConstants.PATH_PREV_PIC);
        }
        return false;
    }

    @Override
    public boolean onDoubleTapEvent(MotionEvent e) {
        return false;
    }

    private void deleteOldDataItems() {
        Uri uri = new Uri.Builder().scheme(PutDataRequest.WEAR_URI_SCHEME).path(MyConstants.PATH_IMAGE).build();
        Wearable.DataApi.deleteDataItems(mGoogleApiClient, uri).setResultCallback(new ResultCallback<DataApi.DeleteDataItemsResult>() {
            @Override
            public void onResult(DataApi.DeleteDataItemsResult deleteDataItemsResult) {
                Log.d(TAG, "onResult of deleting " + MyConstants.PATH_IMAGE + ": " + deleteDataItemsResult.getStatus().toString());
            }
        });
    }

    private void startUpTimeCounter(){

        upTimeTextView.setVisibility(View.VISIBLE);

        new Thread(new Runnable() {
            int second = 0;
            int minute = 0;

            @Override
            public void run() {
                while (animationRunning){
                    try{
                        Thread.sleep(1000);
                    }catch (InterruptedException e){
                        e.printStackTrace();
                    }
                    upTimeTextView.post(new Runnable() {
                        @Override
                        public void run() {
                            String result = String.format("%02d:%02d", minute , second);
                            upTimeTextView.setText(result);
                        }
                    });
                    if(second == 59){
                        minute++;
                        second =0;
                    }else{
                        second++;
                    }
                }
            }
        }).start();
    }

    public class ImageAdapter extends GridPagerAdapter implements View.OnTouchListener {
        final Context mContext;

        public ImageAdapter(final Context context) {
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
        public Object instantiateItem(ViewGroup viewGroup, int _row, int _col) {
            if (_row == 0 && _col == 0) {
                mImageViewCam = new ImageView(mContext);
                mImageViewCam.setOnTouchListener(this);
                viewGroup.addView(mImageViewCam);
                return mImageViewCam;
            } else if (_row == 0 && _col == 1) {
                mImageViewGallery = new ImageView(mContext);
                mImageViewGallery.setOnTouchListener(this);
                viewGroup.addView(mImageViewGallery);
                return mImageViewGallery;
            } else {
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
            if (v == mImageViewCam || v == mImageViewGallery) {
                return gestureDetector.onTouchEvent(event);
            }
            return false;
        }

    }
}
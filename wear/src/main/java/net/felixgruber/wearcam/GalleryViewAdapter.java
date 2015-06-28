package net.felixgruber.wearcam;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.RelativeLayout;

import mc.fhooe.at.wearcam.R;

/**
 * Created by Musti on 12.06.2015.
 */
public class GalleryViewAdapter extends PagerAdapter {

    private Activity _activity;
    //private ArrayList<Bitmap> images;
    private LayoutInflater inflater;
    private Bitmap img;
    private ImageView imgDisplay;

    public GalleryViewAdapter(Activity activity) {
        //ArrayList<Bitmap> _images) {
        this._activity = activity;
        //this.images = _images;
        img = null;
        imgDisplay = null;
    }

    public int getItemPosition(Object object) {
        return POSITION_NONE;
    }

    public ImageView getImgDisplay() {
        return imgDisplay;
    }

    public void setImg(Bitmap _img) {
        img = _img;
    }

    @Override
    public int getCount() {
        //return this.images.size();
        return 1;
    }

    @Override
    public boolean isViewFromObject(View view, Object object) {
        return view == ((RelativeLayout) object);
    }

    @Override
    public Object instantiateItem(ViewGroup container, int position) {

        inflater = (LayoutInflater) _activity
                .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View viewLayout = inflater.inflate(R.layout.layout_fullscreen_image, container, false);

        imgDisplay = (ImageView) viewLayout.findViewById(R.id.imgDisplay);

        if (img != null) {
            imgDisplay.setImageBitmap(img);
        }
        ((ViewPager) container).addView(viewLayout);

        return viewLayout;
    }

    @Override
    public void destroyItem(ViewGroup container, int position, Object object) {
        ((ViewPager) container).removeView((RelativeLayout) object);
    }
}
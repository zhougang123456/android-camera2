package com.zhougang.camera2;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.hardware.camera2.params.Face;
import android.os.Build;
import android.support.annotation.RequiresApi;
import android.util.AttributeSet;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;


public class FindFaceView extends SurfaceView implements SurfaceHolder.Callback{
    private SurfaceHolder mHolder;
    private int mWidth;
    private int mHeight;
    public FindFaceView(Context context, AttributeSet attrs){
        super(context,attrs);
        mHolder = getHolder();
        mHolder.addCallback(this);
        mHolder.setFormat(PixelFormat.TRANSPARENT);
        setZOrderOnTop(true);
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {

    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        mWidth = width;
        mHeight = height;
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {

    }
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public void drawRect(Face[] faces){
        Canvas canvas = mHolder.lockCanvas();
        if (canvas != null) {
            Paint clipPaint = new Paint();
            clipPaint.setAntiAlias(true);
            clipPaint.setStyle(Paint.Style.STROKE);
            clipPaint
                    .setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
            canvas.drawPaint(clipPaint);
            //canvas.drawColor(getResources().getColor(color.transparent));
            Paint paint = new Paint();
            paint.setAntiAlias(true);
            paint.setColor(Color.GREEN);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(5.0f);
            Log.e("face_show:",faces.length+" faces have been showed!");
            for (int i = 0; i < faces.length; i++) {
                Rect rect = faces[i].getBounds();
                canvas.drawRect(
                        mWidth - (rect.bottom ) * mWidth / 4000f,
                        mHeight - (rect.right ) * mHeight / 5000f,
                        mWidth - (rect.top ) * mWidth / 4000f,
                        mHeight - (rect.left ) * mHeight / 5000f, paint);
            }
            mHolder.unlockCanvasAndPost(canvas);
            canvas.drawPaint(clipPaint);
        }
    }
}

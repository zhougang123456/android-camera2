package com.zhougang.camera2;


import android.Manifest;
import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.Face;
import android.media.FaceDetector;
import android.media.Image;
import android.media.ImageReader;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.support.annotation.RequiresApi;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.util.SparseIntArray;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.ImageView;
import android.widget.Toast;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;

public class MainActivity extends AppCompatActivity  {
    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();

        ///为了使照片竖直显示
    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 90);
        ORIENTATIONS.append(Surface.ROTATION_90, 0);
        ORIENTATIONS.append(Surface.ROTATION_180, 270);
        ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }
    private BufferedOutputStream output;
    private MediaCodec mediaCodec;
    private SurfaceView mSurfaceView;
    private SurfaceHolder mSurfaceHolder;
    private CameraManager mCameraManager;//摄像头管理器
    private Handler childHandler, mainHandler;
    private String mCameraID;//摄像头Id 0 为后  1 为前
    private ImageReader mImageReader;
    private CameraCaptureSession mCameraCaptureSession;
    private CameraDevice mCameraDevice;
    private FindFaceView faceView;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initView();
    }

    private void initView() {
        //mSurfaceView
        mSurfaceView = findViewById(R.id.camera_view);
        faceView = findViewById(R.id.face_view);
        mSurfaceHolder = mSurfaceView.getHolder();
        mSurfaceHolder.setKeepScreenOn(true);
            // mSurfaceView添加回调
        mSurfaceHolder.addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(SurfaceHolder holder) { //SurfaceView创建
                initDecode();
                initCamera2();
            }

            @Override
            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {

            }

            @TargetApi(Build.VERSION_CODES.LOLLIPOP)
            @Override
            public void surfaceDestroyed(SurfaceHolder holder) { //SurfaceView销毁
                    // 释放Camera资源
                if (null != mCameraDevice) {
                    mCameraDevice.close();
                    mCameraDevice = null;
                }
            }
        });
    }
    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    public void onEncodeFrame(byte[] buffer, int offset, int length, int flag) throws IOException {
        ByteBuffer[] inputBuffers = mediaCodec.getInputBuffers();
        ByteBuffer[] outputBuffers = mediaCodec.getOutputBuffers();
        int inputBufferIndex = mediaCodec.dequeueInputBuffer(0);
        if (inputBufferIndex >= 0){
            ByteBuffer inputBuffer = inputBuffers[inputBufferIndex];
            inputBuffer.clear();
            inputBuffer.put(buffer,offset,length);
            mediaCodec.queueInputBuffer(inputBufferIndex,0,length,System.currentTimeMillis(),0);
        }else {
            return;
        }
        MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
        int outBufferIndex = mediaCodec.dequeueOutputBuffer(bufferInfo,0);
        while (outBufferIndex >= 0){

            ByteBuffer outBuffer = outputBuffers[outBufferIndex];
            byte[] outData = new byte[bufferInfo.size];
            outBuffer.get(outData);
            output.write(outData);
            mediaCodec.releaseOutputBuffer(outBufferIndex,true);
            outBufferIndex = mediaCodec.dequeueOutputBuffer(bufferInfo,0);
        }
    }
    public void initDecode(){
        String path = Environment.getExternalStorageDirectory().getAbsolutePath() + "/test1.h264";
        File file = new File(path);
        if (!file.exists()){
            try {
                file.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        try {
            output = new BufferedOutputStream(new FileOutputStream(file));
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                mediaCodec = MediaCodec.createEncoderByType("video/avc");
                Log.e("mediaCodec "," " + mediaCodec);
                MediaFormat mediaFormat = MediaFormat.createVideoFormat("video/avc",640,480);
                mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE,250000);
                mediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE,15);
                mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar);
                mediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL,-1);
                mediaCodec.configure(mediaFormat,null,null,MediaCodec.CONFIGURE_FLAG_ENCODE);
                mediaCodec.start();
            }
        }catch (IOException e){
            e.printStackTrace();
        }

    }
    /**
     * 初始化Camera2
     */
    @TargetApi(Build.VERSION_CODES.LOLLIPOP)

    private void initCamera2() {
        HandlerThread handlerThread = new HandlerThread("Camera2");
        handlerThread.start();
        childHandler = new Handler(handlerThread.getLooper());
        mainHandler = new Handler(getMainLooper());
        mCameraID = "" + CameraCharacteristics.LENS_FACING_BACK;//前摄像头
        mImageReader = ImageReader.newInstance(640, 480, ImageFormat.YUV_420_888,2);
        mImageReader.setOnImageAvailableListener(new ImageReader.OnImageAvailableListener() { //可以在这里处理拍照得到的临时照片 例如，写入本地
            @Override
            public void onImageAvailable(ImageReader reader) {

                Image image = reader.acquireNextImage();

                ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                byte[] bytes = new byte[buffer.remaining()];
                buffer.get(bytes);//由缓冲区存入字节数组
                try {

                    onEncodeFrame(bytes,0,bytes.length,0);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                image.close();
            }
            }, mainHandler);
            //获取摄像头管理

        mCameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                return;
            }
            //打开摄像头
            mCameraManager.openCamera(mCameraID, stateCallback, mainHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }


    /**
     * 摄像头创建监听
     */
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)

    private CameraDevice.StateCallback stateCallback = new CameraDevice.StateCallback() {

        @Override
        public void onOpened(CameraDevice camera) {//打开摄像头
            mCameraDevice = camera;
            //开启预览
            takePreview();
        }

        @Override
        public void onDisconnected(CameraDevice camera) {//关闭摄像头
            if (null != mCameraDevice) {
                mCameraDevice.close();
                mCameraDevice = null;
            }
        }

        @Override
        public void onError(CameraDevice camera, int error) {//发生错误
            Toast.makeText(MainActivity.this, "摄像头开启失败", Toast.LENGTH_SHORT).show();
            }
        };

    /**
     * 开始预览
     */
    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private void takePreview() {
        try {
            // 创建预览需要的CaptureRequest.Builder
            final CaptureRequest.Builder previewRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            // 将SurfaceView的surface作为CaptureRequest.Builder的目标
            previewRequestBuilder.addTarget(mSurfaceHolder.getSurface());
            previewRequestBuilder.addTarget(mImageReader.getSurface());
            previewRequestBuilder.set(CaptureRequest.STATISTICS_FACE_DETECT_MODE,
                    CameraMetadata.STATISTICS_FACE_DETECT_MODE_FULL);
            // 创建CameraCaptureSession，该对象负责管理处理预览请求和拍照请求
            mCameraDevice.createCaptureSession(Arrays.asList(mSurfaceHolder.getSurface(), mImageReader.getSurface()), new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(CameraCaptureSession cameraCaptureSession) {
                    if (null == mCameraDevice) return;
                    // 当摄像头已经准备好时，开始显示预览
                    mCameraCaptureSession = cameraCaptureSession;
                    try {
                        // 自动对焦
                        previewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                        // 打开闪光灯
                        previewRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);
                        // 显示预览
                        CaptureRequest previewRequest = previewRequestBuilder.build();
                        //设置人脸检测
                        previewRequestBuilder.set(CaptureRequest.STATISTICS_FACE_DETECT_MODE,
                                CameraMetadata.STATISTICS_FACE_DETECT_MODE_FULL);
                        mCameraCaptureSession.setRepeatingRequest(previewRequest, new CameraCaptureSession.CaptureCallback() {

                            private void process(CaptureResult result) {
                                //获得Face类
                                Face faces[]=result.get(CaptureResult.STATISTICS_FACES);
                                //如果有人脸的话
                                if (faces != null && faces.length>0 ){
                                    Log.e("MainActivity",  faces.length + " face detected " );
                                    //获取人脸矩形框
                                    Rect bounds = faces[0].getBounds();
                                    Log.w("main_activity","width: " + bounds.width()+" height: " + bounds.height());
                                    Log.e("left" , "left: " + bounds.left);
                                    Log.e("right" , "bottom: " + bounds.bottom);
                                    //faceView.setVisibility(View.VISIBLE);
                                    //faceView.drawRect(faces);
                                } else {
                                    //faceView.setVisibility(View.GONE);
                                }
                            }
                            @Override
                            public void onCaptureStarted(CameraCaptureSession session, CaptureRequest request, long timestamp, long frameNumber) {
                                super.onCaptureStarted(session, request, timestamp, frameNumber);
                            }
                            @Override
                            public void onCaptureProgressed(CameraCaptureSession session, CaptureRequest request, CaptureResult partialResult) {
                                process(partialResult);
                            }
                            @Override
                            public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request, TotalCaptureResult result) {
                                process(result);
                            }
                        }, childHandler);
                    } catch (CameraAccessException e) {
                        e.printStackTrace();
                    }
                }
                @Override
                public void onConfigureFailed(CameraCaptureSession cameraCaptureSession) {
                    Toast.makeText(MainActivity.this, "配置失败", Toast.LENGTH_SHORT).show();
                }
                }, childHandler);
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }

    }
}

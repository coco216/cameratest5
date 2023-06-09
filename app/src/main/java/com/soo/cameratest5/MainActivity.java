package com.soo.cameratest5;

import android.Manifest;
import android.app.DatePickerDialog;
import android.app.Dialog;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.Button;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.TimePicker;
import android.widget.Toast;

//import androidx.annotation.NonNull;
//import androidx.appcompat.app.AppCompatActivity;
//import androidx.core.app.ActivityCompat;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "AndroidCameraApi";
    private Button takePictureButton;
    private TextureView textureView;

    private Button btn_starttime_id;
    private Button btn_endtime_id;
    private Button tv_interval_id;

    private TextView tv_starttime_id;
    private TextView tv_endtime_id;
    private EditText et_interval_id;

    private TextView tvTimeRemainingId;
    private TextView tvShotCountId;

    private Calendar c;
    private Calendar c_end;
    private Calendar calStart, calEnd;
    private int mYear, mMonth, mDay, mHour, mMinute;
    private int mYear_end, mMonth_end, mDay_end, mHour_end, mMinute_end;

    private int year_start, month_start, day_start, hour_start, minute_start;
    private int year_end, month_end, day_end, hour_end, minute_end;

    private String PhotoDirectoryName = "AutoPhoto";

    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();

    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 90);
        ORIENTATIONS.append(Surface.ROTATION_90, 0);
        ORIENTATIONS.append(Surface.ROTATION_180, 270);
        ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }

    private String cameraId;
    protected CameraDevice cameraDevice;
    protected CameraCaptureSession cameraCaptureSessions;
    protected CaptureRequest captureRequest;
    protected CaptureRequest.Builder captureRequestBuilder;
    private Size imageDimension;
    private ImageReader imageReader;
    private File file;
    private static final int REQUEST_CAMERA_PERMISSION = 200;
    private boolean mFlashSupported;
    private Handler mBackgroundHandler;
    private HandlerThread mBackgroundThread;

    int shotCounter=0;
    float nextShotTimeLeft=0;

    int startButtonClicked =0;
    String startTimeStr="";
    String endTimeStr="";
    String intervalStr;
    SimpleDateFormat sdf;
    int intervalMsec=0;
    long waitTimeToStartMsec=0;
    Date startDate, endDate, nowDate;

    private Date currentTime, oneDayLaterTime;

    // 1 sec time checker
    private Handler secHandler = new Handler();

    private Runnable secRunnable = new Runnable() {

        @Override
        public void run() {
            float remainingSec;
            long timeGapSinceBeginning;
            long remainingMsec;
            // Call your function here
            Date nowDateTmp1 = Calendar.getInstance().getTime();
            timeGapSinceBeginning = nowDateTmp1.getTime() - startDate.getTime();
            remainingMsec = intervalMsec - (timeGapSinceBeginning % intervalMsec);

            if (timeGapSinceBeginning<0){
                timeGapSinceBeginning =timeGapSinceBeginning *-1;
                remainingSec =timeGapSinceBeginning/1000;
            }
            else {
                remainingSec =remainingMsec/1000;
            }
            if (remainingMsec<0)
                remainingMsec =0;

            tvTimeRemainingId.setText(String.valueOf(remainingSec)+" sec");

            secHandler.postDelayed(this, 1000);
        }
    };



    private Handler mHandler = new Handler();

    private Runnable mRunnable = new Runnable() {
        @Override
        public void run() {
            // Call your function here
            doSomething();

            Date nowDateTmp = Calendar.getInstance().getTime();
            if (nowDateTmp.getTime()<endDate.getTime())
                {
                    long timeGapSinceBeginning = nowDateTmp.getTime() - startDate.getTime();
                    long remainingMsec = intervalMsec - (timeGapSinceBeginning % intervalMsec);

                    //Log.i("Time_Check: ","startDate:" + sdf.format(startDate));
                    //Log.i("Time_Check: ","nowDateTmp:" + sdf.format(nowDateTmp));
                    //Log.i("Time_Check: ","remainingMsec:" + remainingMsec);

                    // Schedule the function to be called again after 10 seconds
                    if (remainingMsec<0)
                        remainingMsec =0;

                    mHandler.postDelayed(this, remainingMsec);
                }
                else {
                    finishAffinity();
                    System.exit(0);
                }


        }
    };


    private boolean inputErrorCheck(){
        Date nowDateTmp = Calendar.getInstance().getTime();
        if (nowDateTmp.getTime()>=startDate.getTime()){
            Toast.makeText(MainActivity.this, "Input Error: Start time must be later than now!", Toast.LENGTH_SHORT ).show();
            return false;
        }
        if (endDate.getTime()<=startDate.getTime()){
            Toast.makeText(MainActivity.this, "Input Error: End time must be later than Start time!", Toast.LENGTH_SHORT ).show();
            return false;
        }
        if (intervalMsec <=0){
            Toast.makeText(MainActivity.this, "Input Error: Capture time interval must be larger than 0 sec!", Toast.LENGTH_SHORT ).show();
            return false;
        }

        return true;

    }


    private void doSomething() {
        // Your function code here
        if (startButtonClicked>0) {

            Date nowDateTmp1 = Calendar.getInstance().getTime();
            //Log.i("Time_Check: ","Taking Picture: " + sdf.format(nowDateTmp1));
            takePicture();
        }
    }

    private void terminateApp(){
        finishAffinity();
        System.exit(0);
    }

    private void startCamera() {
        mHandler.postDelayed(mRunnable, waitTimeToStartMsec);
        secHandler.postDelayed(secRunnable, 0);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        textureView = (TextureView) findViewById(R.id.texture);
        assert textureView != null;
        textureView.setSurfaceTextureListener(textureListener);
        takePictureButton = (Button) findViewById(R.id.btn_takepicture);

        btn_starttime_id = (Button) findViewById(R.id.btn_starttime);
        btn_endtime_id   = (Button) findViewById(R.id.btn_endtime);
        //tv_interval_id = (Button) findViewById(R.id.tv_interval);

        tv_starttime_id = (TextView) findViewById(R.id.tv_starttime);
        tv_endtime_id = (TextView) findViewById(R.id.tv_endtime);
        et_interval_id = (EditText) findViewById(R.id.et_interval);

        tvTimeRemainingId = (TextView) findViewById(R.id.tvTimeRemaining);
        tvShotCountId = (TextView) findViewById(R.id.tvShotCount);

        sdf = new SimpleDateFormat("yyyy-MM-dd, HH:mm:ss", Locale.getDefault());

        currentTime = Calendar.getInstance().getTime();

        // startTimeStr = sdf.format(new Date());
        startTimeStr = sdf.format(currentTime);
        tv_starttime_id.setText(startTimeStr);

        //oneDayLaterTime
        Calendar cal = Calendar.getInstance();
        cal.setTime(currentTime);
        cal.add(Calendar.HOUR, 24);
        //String newTime = df.format(cal.getTime());
        endTimeStr = sdf.format(cal.getTime());
        tv_endtime_id.setText(endTimeStr);

        File myDirectory = new File(Environment.getExternalStorageDirectory(), PhotoDirectoryName);
        if(!myDirectory.exists()) {
            myDirectory.mkdirs();
        }

        assert takePictureButton != null;
        takePictureButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //takePicture();
                startButtonClicked =1;
                shotCounter =0;
                intervalStr = et_interval_id.getText().toString();

                if (intervalStr.matches("")) {
                    et_interval_id.setText("0");
                    intervalStr = et_interval_id.getText().toString();
                }

                intervalMsec = Integer.parseInt(intervalStr)*1000;

                String tv_starttime_id_str = tv_starttime_id.getText().toString();
                String tv_endtime_id_str = tv_endtime_id.getText().toString();

                try {
                    startDate = sdf.parse(tv_starttime_id_str);

                } catch (ParseException e) {
                    e.printStackTrace();
                }

                try {
                    endDate = sdf.parse(tv_endtime_id_str);
                } catch (ParseException e) {
                    e.printStackTrace();
                }


                nowDate = Calendar.getInstance().getTime();
               waitTimeToStartMsec = startDate.getTime() - nowDate.getTime();

               // Toast.makeText(MainActivity.this, Long.toString(waitTimeToStartMsec), Toast.LENGTH_SHORT).show();
                //Toast.makeText(MainActivity.this,Long.toString(intervalMsec), Toast.LENGTH_SHORT).show();
                if (inputErrorCheck() == true) {
                    startCamera();
                }
                else
                {
                    // do nothing
                }
            }
        });

        btn_starttime_id.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Get Current Date
                c= Calendar.getInstance();
                mYear = c.get(Calendar.YEAR);
                mMonth = c.get(Calendar.MONTH);
                mDay = c.get(Calendar.DAY_OF_MONTH);

                DatePickerDialog datePickerDialog = new DatePickerDialog(MainActivity.this,new DatePickerDialog.OnDateSetListener() {

                            @Override
                            public void onDateSet(DatePicker view, int year,
                                                  int monthOfYear, int dayOfMonth) {
                                year_start= year;
                                month_start = monthOfYear;
                                day_start = dayOfMonth;
                                startTimeStr = dayOfMonth + "-" + (monthOfYear + 1) + "-" + year;
                               // tv_starttime_id.setText(startTimeStr);
                                setStartTime();

                            }
                        }, mYear, mMonth, mDay);
                datePickerDialog.show();
            }
        });

        btn_endtime_id.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Get Current Date
                c_end= Calendar.getInstance();
                mYear_end = c_end.get(Calendar.YEAR);
                mMonth_end = c_end.get(Calendar.MONTH);
                mDay_end = c_end.get(Calendar.DAY_OF_MONTH);

                DatePickerDialog datePickerDialog = new DatePickerDialog(MainActivity.this,new DatePickerDialog.OnDateSetListener() {

                    @Override
                    public void onDateSet(DatePicker view, int year_end_local,
                                          int monthOfYear_end, int dayOfMonth_end) {

                        year_end= year_end_local;
                        month_end = monthOfYear_end ;
                        day_end = dayOfMonth_end;

                        endTimeStr = dayOfMonth_end + "-" + (monthOfYear_end + 1) + "-" + year_end;
                        //tv_endtime_id.setText(endTimeStr);
                        setEndTime();

                    }
                }, mYear_end, mMonth_end, mDay_end);
                datePickerDialog.show();
            }
        });

    }



private void setStartTime(){
    // Get Current Time
    final Calendar c = Calendar.getInstance();
    mHour = c.get(Calendar.HOUR_OF_DAY);
    mMinute = c.get(Calendar.MINUTE);

    // Launch Time Picker Dialog
    TimePickerDialog timePickerDialog = new TimePickerDialog(MainActivity.this, new TimePickerDialog.OnTimeSetListener() {

                @Override
                public void onTimeSet(TimePicker view, int hourOfDay,
                                      int minute) {

                    //endTimeStr = dayOfMonth_end + "-" + (monthOfYear_end + 1) + "-" + year_end;
                    //startTimeStr = startTimeStr + " "+ hourOfDay + ":" + minute;

                    calStart = Calendar.getInstance();
                    calStart.set(year_start,month_start, day_start, hourOfDay, minute, 0 );

                    startTimeStr = sdf.format(calStart.getTime());
                    tv_starttime_id.setText(startTimeStr);
                }
            }, mHour, mMinute, false);
    timePickerDialog.show();
}

    private void setEndTime(){
        // Get Current Time
        c_end = Calendar.getInstance();
        mHour_end = c_end.get(Calendar.HOUR_OF_DAY);
        mMinute_end = c_end.get(Calendar.MINUTE);

        // Launch Time Picker Dialog
        TimePickerDialog timePickerDialog = new TimePickerDialog(MainActivity.this, new TimePickerDialog.OnTimeSetListener() {

            @Override
            public void onTimeSet(TimePicker view, int hourOfDay_end,
                                  int minute_end) {
               // endTimeStr = endTimeStr + " "+ hourOfDay_end + ":" + minute_end;

                calEnd = Calendar.getInstance();
                calEnd.set(year_end,month_end, day_end, hourOfDay_end, minute_end, 0 );

                endTimeStr = sdf.format(calEnd.getTime());
                tv_endtime_id.setText(endTimeStr);
            }
        }, mHour_end, mMinute_end, false);
        timePickerDialog.show();
    }



    TextureView.SurfaceTextureListener textureListener = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
            //open your camera here
            openCamera();
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
            // Transform you image captured size according to the surface width and height
        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
            return false;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surface) {
        }
    };
    private final CameraDevice.StateCallback stateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(CameraDevice camera) {
            //This is called when the camera is open
            Log.e(TAG, "onOpened");
            cameraDevice = camera;
            createCameraPreview();
        }

        @Override
        public void onDisconnected(CameraDevice camera) {
            cameraDevice.close();
        }

        @Override
        public void onError(CameraDevice camera, int error) {
            cameraDevice.close();
            cameraDevice = null;
        }
    };
    final CameraCaptureSession.CaptureCallback captureCallbackListener = new CameraCaptureSession.CaptureCallback() {
        @Override
        public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request, TotalCaptureResult result) {
            super.onCaptureCompleted(session, request, result);
           // Toast.makeText(MainActivity.this, "Saved:" + file, Toast.LENGTH_SHORT).show();
            createCameraPreview();
        }
    };

    protected void startBackgroundThread() {
        mBackgroundThread = new HandlerThread("Camera Background");
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
    }

    protected void stopBackgroundThread() {
        mBackgroundThread.quitSafely();
        try {
            mBackgroundThread.join();
            mBackgroundThread = null;
            mBackgroundHandler = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    protected void takePicture() {

        // create a new filename for the picture
        SimpleDateFormat formatter= new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss");
        Date dateForFilename = new Date(System.currentTimeMillis());
        String photoFilename = formatter.format(dateForFilename);

        if (null == cameraDevice) {
            Log.e(TAG, "cameraDevice is null");
            return;
        }
        CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraDevice.getId());
            Size[] jpegSizes = null;
            if (characteristics != null) {
                jpegSizes = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP).getOutputSizes(ImageFormat.JPEG);
            }
            int width = 640;
            int height = 480;
            if (jpegSizes != null && 0 < jpegSizes.length) {
                width = jpegSizes[0].getWidth();
                height = jpegSizes[0].getHeight();
            }
            ImageReader reader = ImageReader.newInstance(width, height, ImageFormat.JPEG, 1);
            List<Surface> outputSurfaces = new ArrayList<Surface>(2);
            outputSurfaces.add(reader.getSurface());
            outputSurfaces.add(new Surface(textureView.getSurfaceTexture()));
            final CaptureRequest.Builder captureBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            captureBuilder.addTarget(reader.getSurface());
            captureBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
            // Orientation
            int rotation = getWindowManager().getDefaultDisplay().getRotation();
            captureBuilder.set(CaptureRequest.JPEG_ORIENTATION, ORIENTATIONS.get(rotation));



            final File file = new File(Environment.getExternalStorageDirectory() +"/"+PhotoDirectoryName+ "/"+photoFilename+".jpg");
            ImageReader.OnImageAvailableListener readerListener = new ImageReader.OnImageAvailableListener() {
                @Override
                public void onImageAvailable(ImageReader reader) {
                    Image image = null;
                    try {
                        image = reader.acquireLatestImage();
                        ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                        byte[] bytes = new byte[buffer.capacity()];
                        buffer.get(bytes);
                        save(bytes);

                        tvShotCountId.setText(String.valueOf(++shotCounter));

                    } catch (FileNotFoundException e) {
                        e.printStackTrace();
                    } catch (IOException e) {
                        e.printStackTrace();
                    } finally {
                        if (image != null) {
                            image.close();
                        }
                    }
                }

                private void save(byte[] bytes) throws IOException {
                    OutputStream output = null;
                    try {
                        output = new FileOutputStream(file);
                        output.write(bytes);
                       // Toast.makeText(MainActivity.this, "Writing:" + file, Toast.LENGTH_SHORT).show();
                    } finally {
                        if (null != output) {
                            output.close();
                        }
                    }
                }
            };
            reader.setOnImageAvailableListener(readerListener, mBackgroundHandler);
            final CameraCaptureSession.CaptureCallback captureListener = new CameraCaptureSession.CaptureCallback() {
                @Override
                public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request, TotalCaptureResult result) {
                    super.onCaptureCompleted(session, request, result);
                   // Toast.makeText(MainActivity.this, "Saved:" + file, Toast.LENGTH_SHORT).show();
                    createCameraPreview();
                }
            };
            cameraDevice.createCaptureSession(outputSurfaces, new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(CameraCaptureSession session) {
                    try {
                        session.capture(captureBuilder.build(), captureListener, mBackgroundHandler);
                    } catch (CameraAccessException e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onConfigureFailed(CameraCaptureSession session) {
                }
            }, mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    protected void createCameraPreview() {
        try {
            SurfaceTexture texture = textureView.getSurfaceTexture();
            assert texture != null;
            texture.setDefaultBufferSize(imageDimension.getWidth(), imageDimension.getHeight());
            Surface surface = new Surface(texture);
            captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            captureRequestBuilder.addTarget(surface);
            cameraDevice.createCaptureSession(Arrays.asList(surface), new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                    //The camera is already closed
                    if (null == cameraDevice) {
                        return;
                    }
                    // When the session is ready, we start displaying the preview.
                    cameraCaptureSessions = cameraCaptureSession;
                    updatePreview();
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {
                    Toast.makeText(MainActivity.this, "Configuration change", Toast.LENGTH_SHORT).show();
                }
            }, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void openCamera() {
        CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        Log.e(TAG, "is camera open");
        try {
            cameraId = manager.getCameraIdList()[0];
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
            StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            assert map != null;
            imageDimension = map.getOutputSizes(SurfaceTexture.class)[0];
            // Add permission for camera and let user grant the permission
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_CAMERA_PERMISSION);
                return;
            }
            manager.openCamera(cameraId, stateCallback, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
        Log.e(TAG, "openCamera X");
    }

    protected void updatePreview() {
        if (null == cameraDevice) {
            Log.e(TAG, "updatePreview error, return");
        }
        captureRequestBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
        try {
            cameraCaptureSessions.setRepeatingRequest(captureRequestBuilder.build(), null, mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void closeCamera() {
        if (null != cameraDevice) {
            cameraDevice.close();
            cameraDevice = null;
        }
        if (null != imageReader) {
            imageReader.close();
            imageReader = null;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults[0] == PackageManager.PERMISSION_DENIED) {
                // close the app
                Toast.makeText(MainActivity.this, "Sorry!!!, you can't use this app without granting permission", Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.e(TAG, "onResume");
        startBackgroundThread();
        if (textureView.isAvailable()) {
            openCamera();
        } else {
            textureView.setSurfaceTextureListener(textureListener);
        }

        startButtonClicked =0;
        // Start the periodic function call when the activity resumes
        //mHandler.postDelayed(mRunnable, 10000);

    }

    @Override
    protected void onPause() {
        Log.e(TAG, "onPause");
        //closeCamera();
        stopBackgroundThread();
        super.onPause();

        // Stop the periodic function call when the activity pauses
        startButtonClicked=0;
        mHandler.removeCallbacks(mRunnable);
        secHandler.removeCallbacks(secRunnable);
        terminateApp();

    }
}
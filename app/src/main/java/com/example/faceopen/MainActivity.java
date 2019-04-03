package com.example.faceopen;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.hardware.usb.UsbDevice;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.util.Base64;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.Surface;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.android.volley.DefaultRetryPolicy;
import com.android.volley.NetworkResponse;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.jiangdg.usbcamera.UVCCameraHelper;
import com.jiangdg.usbcamera.utils.FileUtils;
import com.serenegiant.usb.CameraDialog;
import com.serenegiant.usb.Size;
import com.serenegiant.usb.USBMonitor;
import com.serenegiant.usb.common.AbstractUVCCameraHandler;
import com.serenegiant.usb.widget.CameraViewInterface;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.Vector;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import static android.Manifest.permission.CAMERA;
import static android.Manifest.permission.WRITE_EXTERNAL_STORAGE;
import static android.Manifest.permission_group.MICROPHONE;
import static java.lang.Math.abs;


public class MainActivity extends AppCompatActivity implements SensorEventListener,CameraDialog.CameraDialogParent, CameraViewInterface.Callback{

    private static final String TAG = "Debug";

    ///sensor

    private SensorManager mSensorManager;

    // Proximity and light sensors, as retrieved from the sensor manager.
    private Sensor mSensorProximity;
    private Sensor mSensorLight;



    TextView t,b;




    public View mTextureView;
    public SeekBar mSeekBrightness;
    public SeekBar mSeekContrast;
    public Switch mSwitchVoice;
    public ImageView img;
    RequestQueue requestQueue;

    long t_start_face,t_start_api;

    int k=0,j=0,th=0,an=0;

    private String[] neededPermissions = new String[]{CAMERA, WRITE_EXTERNAL_STORAGE,MICROPHONE,};

    MTCNN mtcnn;

    private UVCCameraHelper mCameraHelper;
    private CameraViewInterface mUVCCameraView;
    private AlertDialog mDialog;

    private boolean isRequest;
    private boolean isPreview;





    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        //getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);

        t=findViewById(R.id.brightness);
        b=findViewById(R.id.bright);

        mSensorManager = (SensorManager) getSystemService(
                Context.SENSOR_SERVICE);

        mSensorLight = mSensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);
        float maxvalue = mSensorLight.getMaximumRange();
        Log.i("max value",maxvalue+"");

        if (mSensorLight == null) { t.setText("error"); }





        requestQueue = Volley.newRequestQueue(getBaseContext());

        handleSSLHandshake();

        initView();
        mtcnn = new MTCNN(getAssets());
        checkPermission();

        // step.1 initialize UVCCameraHelper
        mUVCCameraView = (CameraViewInterface) mTextureView;
        mUVCCameraView.setCallback(this);
        mCameraHelper = UVCCameraHelper.getInstance();
        mCameraHelper.setDefaultFrameFormat(UVCCameraHelper.FRAME_FORMAT_YUYV);
        mCameraHelper.initUSBMonitor(this, mUVCCameraView, listener);
        mCameraHelper.updateResolution(1280, 720);

        final int[] i = {0};


        mCameraHelper.setOnPreviewFrameListener(new AbstractUVCCameraHandler.OnPreViewResultListener() {
            @Override
            public void onPreviewResult(byte[] bytes) {
                int width = mCameraHelper.getPreviewWidth();
                int height = mCameraHelper.getPreviewHeight();
                YuvImage yuv = new YuvImage(bytes, ImageFormat.NV21, width, height, null);

                ByteArrayOutputStream out = new ByteArrayOutputStream();
                yuv.compressToJpeg(new Rect(0, 0, width, height), 50, out);


                byte[] bytes1 = out.toByteArray();

                final Bitmap bitmap = BitmapFactory.decodeByteArray(bytes1, 0, bytes1.length);
                faceDetection f = new faceDetection();

                //Log.i("yo",mtcnn.isCOmplete()+"");

                if (i[0] ==0)
                {
                    f.execute(bitmap);
                    i[0]++;
                }

                if(mtcnn.isCOmplete() == 1) {
                    //Log.i("yo",width+" "+height);
                    f.execute(bitmap);

                }
            }
        });
    }




    private boolean checkPermission() {
        int currentAPIVersion = Build.VERSION.SDK_INT;
        if (currentAPIVersion >= android.os.Build.VERSION_CODES.M) {
            ArrayList<String> permissionsNotGranted = new ArrayList<>();
            for (String permission : neededPermissions) {
                if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                    permissionsNotGranted.add(permission);
                }
            }
            if (permissionsNotGranted.size() > 0) {
                boolean shouldShowAlert = false;
                for (String permission : permissionsNotGranted) {
                    shouldShowAlert = ActivityCompat.shouldShowRequestPermissionRationale(this, permission);
                }
                if (shouldShowAlert) {
                    showPermissionAlert(permissionsNotGranted.toArray(new String[permissionsNotGranted.size()]));
                } else {
                    requestPermissions(permissionsNotGranted.toArray(new String[permissionsNotGranted.size()]));
                }
                return false;
            }
        }
        return true;
    }

    private void requestPermissions(String[] permissions) {
        ActivityCompat.requestPermissions(MainActivity.this, permissions, 100);
    }

    private void showPermissionAlert(final String[] permissions) {
        android.app.AlertDialog.Builder alertBuilder = new android.app.AlertDialog.Builder(this);
        alertBuilder.setCancelable(true);
        alertBuilder.setTitle(R.string.permission_required);
        alertBuilder.setMessage(R.string.permission_message);
        alertBuilder.setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                requestPermissions(permissions);
            }
        });
        android.app.AlertDialog alert = alertBuilder.create();
        alert.show();
    }


    @SuppressLint("TrulyRandom")
    public static void handleSSLHandshake() {
        try {
            TrustManager[] trustAllCerts = new TrustManager[]{new X509TrustManager() {
                public X509Certificate[] getAcceptedIssuers() {
                    return new X509Certificate[0];
                }

                @Override
                public void checkClientTrusted(X509Certificate[] certs, String authType) {
                }

                @Override
                public void checkServerTrusted(X509Certificate[] certs, String authType) {
                }
            }};

            SSLContext sc = SSLContext.getInstance("SSL");
            sc.init(null, trustAllCerts, new SecureRandom());
            HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
            HttpsURLConnection.setDefaultHostnameVerifier(new HostnameVerifier() {
                @Override
                public boolean verify(String arg0, SSLSession arg1) {
                    return true;
                }
            });
        } catch (Exception ignored) {
        }
    }

    @Override
    public void onSensorChanged(SensorEvent sensorEvent) {
        int sensorType = sensorEvent.sensor.getType();

        // The new data value of the sensor.  Both the light and proximity
        // sensors report one value at a time, which is always the first
        // element in the values array.
        float currentValue = sensorEvent.values[0];



        switch (sensorType) {
            // Event came from the light sensor.
            case Sensor.TYPE_LIGHT:
                // Set the light sensor text view to the light sensor string
                // from the resources, with the placeholder filled in.
                t.setText(currentValue+"");
                break;
            default:
                // do nothing
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }


    private class faceDetection extends AsyncTask<Bitmap,Integer,Bitmap> {
        @Override
        protected void onPreExecute() {
            super.onPreExecute();
        }

        @Override
        protected Bitmap doInBackground(Bitmap... bitmaps) {

            mtcnn.comp=0;
            Bitmap b = bitmaps[0];
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            b.compress(Bitmap.CompressFormat.JPEG, 100, byteArrayOutputStream);
            Bitmap bm= Utils.copyBitmap(b);
            Bitmap transBmp = Bitmap.createBitmap(bm.getWidth(), bm.getHeight(), Bitmap.Config.ARGB_8888);
            JSONObject done = new JSONObject();
            JSONObject face = new JSONObject();
            JSONArray landIDK = new JSONArray();
            JSONArray recIDK = new JSONArray();
            JSONArray poseIDK = new JSONArray();
            try {
                Vector<Box> boxes= mtcnn.detectFaces(bm,180);
                if(boxes.size()>0){
                    t_start_face = System.currentTimeMillis();
                    byte[] byteArray = byteArrayOutputStream .toByteArray();
                    final String encoded = Base64.encodeToString(byteArray, Base64.NO_WRAP);
                    done.put("image","data:image/jpeg;base64,"+encoded);
                }
                for (int i=0;i<boxes.size();i++){
                    JSONArray recArr = new JSONArray();
                    recArr.put(boxes.get(i).box[0]);
                    recArr.put(boxes.get(i).box[1]  );
                    recArr.put(boxes.get(i).box[2]-boxes.get(i).box[0]+1);
                    recArr.put(boxes.get(i).box[3]-boxes.get(i).box[1]+1);
                    recIDK.put(recArr);
                    JSONArray landArray = new JSONArray();
                    for (int j=0;j<boxes.get(i).landmark.length;j++) {
                        double x = boxes.get(i).landmark[j].x;
                        //Log.i("Utils", "[*] landmarkd "+ j+" "  + x );
                        landArray.put(x);
                    }
                    for(int j=0;j<boxes.get(i).landmark.length;j++) {
                        double y = boxes.get(i).landmark[j].y;
                        landArray.put(y);
                    }

                    landIDK.put(landArray);
                    //Utils.drawPoints(bm,boxes.get(i).landmark);
                    String s = getPose(boxes.get(i).landmark);
                    Log.i("yo",s);
                    JSONArray poseArray = new JSONArray();
                    poseArray.put(s);
                    poseIDK.put(poseArray);
                    Utils.drawRect(transBmp,boxes.get(i).transform2Rect());
                }
                face.put("rects",recIDK);
                face.put("landmarks",landIDK);
                face.put("face_pose",poseIDK);
                done.put("faces",face.toString());
                if(boxes.size()>0){
                    sendData(done);
                }
            }catch (Exception e){
                Log.e(TAG,"[*]detect false:"+e);
            }
            return transBmp;
        }
        @Override
        protected void onPostExecute(final Bitmap bitmap) {
            MainActivity.this.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    img.setImageBitmap(bitmap);
                }
            });
            super.onPostExecute(bitmap);
        }
    }

    private String getPose(Point[] landmark) {
        if (abs(landmark[0].x - landmark[2].x) / abs(landmark[1].x - landmark[2].x) > 2)
            return "Left";
        else if(abs(landmark[1].x - landmark[2].x) / abs(landmark[0].x - landmark[2].x) > 2)
            return "Right";
        else
            return "Center";
    }


    private void sendData(JSONObject done) throws JSONException {
        Log.i("coolboy","inside Send data");
        k++;
        Log.i("coolboy",done.getString("image"));
        t_start_api = System.currentTimeMillis();
        JsonObjectRequest request_json = new JsonObjectRequest(Request.Method.POST,"https://192.168.3.182:3500/recognise", done ,
                new Response.Listener<JSONObject>() {
                    @Override
                    public void onResponse(JSONObject response) {
                        //Process os success response
                        j++;
                        Log.i("coolboy",response.toString()+" "+k+" "+j);
                        Log.i("coolboy","Time taken after face detection "+(System.currentTimeMillis()-t_start_face));
                        Log.i("coolboy","Time taken after API Call "+(System.currentTimeMillis()-t_start_api));
                    }
                }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                NetworkResponse networkResponse = error.networkResponse;
                if (networkResponse != null && networkResponse.data != null) {
                    String jsonError = new String(networkResponse.data);
                    Log.i("error",jsonError);
                }
            }
        });
       /* Log.e("check_","1");
        final Handler handler = new Handler();
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                Log.e("check_","2");
                // Do something after 5s = 5000ms

                if(an==0) {
                    an = 1;
                    Log.e("check_","3");
                }
                else {
                    an = 0;
                    Log.e("check_","4");
                }
                Log.e("check_","5");
            }
        }, 200);
        Log.e("check_","6");
        if(an==0)
        {*/
            Log.e("check_","7");
            th++;
            request_json.setRetryPolicy(new DefaultRetryPolicy(200, 0, 0));
            requestQueue.add(request_json);
            Log.e("check_","8 ");
       // }
        Log.i("an value",an+"");
    }



    private UVCCameraHelper.OnMyDevConnectListener listener = new UVCCameraHelper.OnMyDevConnectListener() {

        @Override
        public void onAttachDev(UsbDevice device) {
            if (mCameraHelper == null || mCameraHelper.getUsbDeviceCount() == 0) {
                showShortMsg("check no usb camera");
                return;
            }
            // request open permission
            if (!isRequest) {
                isRequest = true;
                if (mCameraHelper != null) {
                    mCameraHelper.requestPermission(0);
                }
            }
        }

        @Override
        public void onDettachDev(UsbDevice device) {
            // close camera
            if (isRequest) {
                isRequest = false;
                mCameraHelper.closeCamera();
                showShortMsg(device.getDeviceName() + " is out");
            }
        }

        @Override
        public void onConnectDev(UsbDevice device, boolean isConnected) {
            if (!isConnected) {
                showShortMsg("fail to connect,please check resolution params");
                isPreview = false;
            } else {
                isPreview = true;
                showShortMsg("connecting");
                // initialize seekbar
                // need to wait UVCCamera initialize over
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            Thread.sleep(2500);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        Looper.prepare();
                        if(mCameraHelper != null && mCameraHelper.isCameraOpened()) {
                            mSeekBrightness.setProgress(mCameraHelper.getModelValue(UVCCameraHelper.MODE_BRIGHTNESS));
                            mSeekContrast.setProgress(mCameraHelper.getModelValue(UVCCameraHelper.MODE_CONTRAST));
                        }
                        Looper.loop();
                    }
                }).start();
            }
        }

        @Override
        public void onDisConnectDev(UsbDevice device) {
            showShortMsg("disconnecting");
        }
    };


    private void initView() {

        mTextureView =findViewById(R.id.camera_view);
        mSeekBrightness =findViewById(R.id.seekbar_brightness);
        mSeekContrast = findViewById(R.id.seekbar_contrast);
        mSwitchVoice =findViewById(R.id.switch_rec_voice);
        img = findViewById(R.id.img);


        mSeekBrightness.setMax(100);
        if(mCameraHelper != null && mCameraHelper.isCameraOpened()) {
            mCameraHelper.setModelValue(UVCCameraHelper.MODE_BRIGHTNESS, 70);
        }
        mSeekBrightness.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if(mCameraHelper != null && mCameraHelper.isCameraOpened()) {
                    mCameraHelper.setModelValue(UVCCameraHelper.MODE_BRIGHTNESS,progress);
                    Log.i("brightness",progress+"");
                    b.setText(progress);

                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });
        mSeekContrast.setMax(100);
        mSeekContrast.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if(mCameraHelper != null && mCameraHelper.isCameraOpened()) {
                    mCameraHelper.setModelValue(UVCCameraHelper.MODE_CONTRAST,progress);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });
    }

    @Override
    protected void onStart() {

        if (mSensorLight != null) {
            mSensorManager.registerListener(this, mSensorLight, SensorManager.SENSOR_DELAY_NORMAL);
        }


        super.onStart();
        // step.2 register USB event broadcast
        if (mCameraHelper != null) {
            mCameraHelper.registerUSB();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        // step.3 unregister USB event broadcast
        if (mCameraHelper != null) {
            mCameraHelper.unregisterUSB();
        }
        mSensorManager.unregisterListener(this);
    }
/*
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_toolbar, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_resolution:
                if (mCameraHelper == null || !mCameraHelper.isCameraOpened()) {
                    showShortMsg("sorry,camera open failed");
                    return super.onOptionsItemSelected(item);
                }
                showResolutionListDialog();
                break;
        }
        return super.onOptionsItemSelected(item);
    }*/

    private void showResolutionListDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
        View rootView = LayoutInflater.from(MainActivity.this).inflate(R.layout.layout_dialog_list, null);
        ListView listView = rootView.findViewById(R.id.listview_dialog);
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(MainActivity.this, android.R.layout.simple_list_item_1, getResolutionList());
        if (adapter != null) {
            listView.setAdapter(adapter);
        }
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int position, long id) {
                if (mCameraHelper == null || !mCameraHelper.isCameraOpened())
                    return;
                final String resolution = (String) adapterView.getItemAtPosition(position);
                String[] tmp = resolution.split("x");
                if (tmp != null && tmp.length >= 2) {
                    int widht = Integer.valueOf(tmp[0]);
                    int height = Integer.valueOf(tmp[1]);
                    mCameraHelper.updateResolution(widht, height);
                }
                mDialog.dismiss();
            }
        });

        builder.setView(rootView);
        mDialog = builder.create();
        mDialog.show();
    }


    private List<String> getResolutionList() {
        List<Size> list = mCameraHelper.getSupportedPreviewSizes();
        List<String> resolutions = null;
        if (list != null && list.size() != 0) {
            resolutions = new ArrayList<>();
            for (Size size : list) {
                if (size != null) {
                    resolutions.add(size.width + "x" + size.height);
                }
            }
        }
        return resolutions;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        FileUtils.releaseFile();
        // step.4 release uvc camera resources
        if (mCameraHelper != null) {
            mCameraHelper.release();
        }
        Log.i("result", k+" "+j);
    }

    private void showShortMsg(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    @Override
    public USBMonitor getUSBMonitor() {
        return mCameraHelper.getUSBMonitor();
    }

    @Override
    public void onDialogResult(boolean canceled) {
        if (canceled) {
            showShortMsg("IDK");
        }
    }

    public boolean isCameraOpened() {
        return mCameraHelper.isCameraOpened();
    }

    @Override
    public void onSurfaceCreated(CameraViewInterface view, Surface surface) {
        if (!isPreview && mCameraHelper.isCameraOpened()) {
            mCameraHelper.startPreview(mUVCCameraView);
            isPreview = true;
        }
    }

    @Override
    public void onSurfaceChanged(CameraViewInterface view, Surface surface, int width, int height) {

    }

    @Override
    public void onSurfaceDestroy(CameraViewInterface view, Surface surface) {
        if (isPreview && mCameraHelper.isCameraOpened()) {
            mCameraHelper.stopPreview();
            isPreview = false;
        }
    }

}

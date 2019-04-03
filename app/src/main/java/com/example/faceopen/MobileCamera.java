package com.example.faceopen;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.hardware.Camera;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Base64;
import android.util.Log;
import android.view.Display;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import com.android.volley.DefaultRetryPolicy;
import com.android.volley.NetworkResponse;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
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
import static android.hardware.Camera.CameraInfo.CAMERA_FACING_BACK;
import static android.hardware.Camera.CameraInfo.CAMERA_FACING_FRONT;
import static java.lang.Math.abs;

public class MobileCamera extends AppCompatActivity implements Camera.PreviewCallback, SurfaceHolder.Callback{


    Camera camera;
    SurfaceView camView,boxView;
    SurfaceHolder camHolder,boxHolder;

    int rectLeft,rectTop,rectRight,rectBottom;
    private String[] neededPermissions = new String[]{CAMERA};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_mobile_camera);

        camView = findViewById(R.id.camView);
        boxView = findViewById(R.id.boxView);

        if (checkPermission())
            setupSurfaceHolder();
    }

    private void drawBox(){
        Canvas canvas = boxHolder.lockCanvas(null);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setStyle(Paint.Style.STROKE);
        paint.setColor(Color.GREEN);
        paint.setStrokeWidth(3f);
        rectLeft = 450;
        rectTop = 150;
        rectRight = rectLeft+450;
        rectBottom = rectTop+400;
        Rect rect = new Rect(rectLeft,rectTop,rectRight,rectBottom);
        canvas.drawRect(rect,paint);
        boxHolder.unlockCanvasAndPost(canvas);
    }
    private void refreshCamera(){
        if (camHolder.getSurface() == null)
            return;
        try {
            camera.stopPreview();
        } catch (Exception e){
            e.printStackTrace();
        }
        try {
            camera.setPreviewDisplay(camHolder);
            camera.startPreview();
        } catch (Exception e){
            e.printStackTrace();
        }
    }
    private void setupSurfaceHolder(){
        camView.setSecure(true);
        camHolder = camView.getHolder();
        camHolder.addCallback(this);

        boxHolder = boxView.getHolder();
        boxHolder.addCallback(this);
        boxHolder.setFormat(PixelFormat.TRANSLUCENT);
        boxView.setZOrderMediaOverlay(true);
    }

    private boolean checkPermission() {
        int currentAPIVersion = Build.VERSION.SDK_INT;
        if (currentAPIVersion >= android.os.Build.VERSION_CODES.M) {
            ArrayList<String> permissionsNotGranted = new ArrayList<>();
            for (String permission : neededPermissions)
                if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED)
                    permissionsNotGranted.add(permission);
            if (permissionsNotGranted.size() > 0) {
                boolean shouldShowAlert = false;
                for (String permission : permissionsNotGranted)
                    shouldShowAlert = ActivityCompat.shouldShowRequestPermissionRationale(this, permission);
                if (shouldShowAlert)
                    showPermissionAlert(permissionsNotGranted.toArray(new String[permissionsNotGranted.size()]));
                else
                    requestPermissions(permissionsNotGranted.toArray(new String[permissionsNotGranted.size()]));
                return false;
            }
        }
        return true;
    }
    private void showPermissionAlert(final String[] permissions) {
        AlertDialog.Builder alertBuilder = new AlertDialog.Builder(this);
        alertBuilder.setCancelable(true);
        alertBuilder.setTitle("Permission Required");
        alertBuilder.setMessage("You must grant permission to access camera to run this application.");
        alertBuilder.setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                requestPermissions(permissions);
            }
        });
        AlertDialog alert = alertBuilder.create();
        alert.show();
    }

    private void requestPermissions(String[] permissions) {
        ActivityCompat.requestPermissions(this, permissions, 100);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch (requestCode) {
            case 100:
                for (int result : grantResults)
                    if (result == PackageManager.PERMISSION_DENIED) {
                        Toast.makeText(this, "Permission is required", Toast.LENGTH_LONG).show();
                        return;
                    }
                setupSurfaceHolder();
                break;
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }
    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        try {
            synchronized (holder){
                drawBox();
            }
            camera = Camera.open();
        } catch (Exception e){
            e.printStackTrace();
            return;
        }
        Display display = ((WindowManager)getSystemService(WINDOW_SERVICE)).getDefaultDisplay();
        if (display.getRotation() == Surface.ROTATION_0)
            camera.setDisplayOrientation(90);
        try {
            camera.setPreviewDisplay(camHolder);
            camera.startPreview();
        } catch (Exception e){
            e.printStackTrace();
        }
    }
    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        refreshCamera();
    }
    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        camera.release();
    }

    @Override
    public void onPreviewFrame(byte[] data, Camera camera) {

    }
}


   /* String TAG = "yo";

    ImageView img;

    MTCNN mtcnn;

    long t_start_face,t_start_api;

    boolean inPreview;

    int cameraId=1;

    int k=0,j=0;

    SurfaceView camView,boxView;
    SurfaceHolder camHolder,boxHolder;
    private Camera camera;

    public static final int REQUEST_CODE = 100;
    RequestQueue requestQueue;
    Button switchCam;

    private String[] neededPermissions = new String[]{CAMERA};
    final int[] i = {0};


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_mobile_camera);

        handleSSLHandshake();

        camView = findViewById(R.id.camView);
        boxView = findViewById(R.id.boxView);
        switchCam = findViewById(R.id.switchCam);

        if (checkPermission())
            setupSurfaceHolder();

        switchCam.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (inPreview) {
                    camera.stopPreview();
                    inPreview = false;
                }
                camera.release();

                if (cameraId == CAMERA_FACING_FRONT)
                    cameraId = CAMERA_FACING_BACK;
                else
                    cameraId = CAMERA_FACING_FRONT;
                camera = Camera.open(cameraId);
                setCameraDisplay();
                try {
                    camera.setPreviewDisplay(camHolder);
                    camera.startPreview();
                    inPreview = true;
                } catch (Exception e){
                    e.printStackTrace();
                }
            }
        });

        mtcnn = new MTCNN(getAssets());
        requestQueue = Volley.newRequestQueue(getBaseContext());

        img = findViewById(R.id.img);

    }

    @Override
    public void onPreviewFrame(byte[] bytes, Camera camera) {
        Camera.Parameters p = camera.getParameters();
        int width = p.getPreviewSize().width;
        int height = p.getPreviewSize().height;


        Rect rect = new Rect(0, 0, width, height);
        YuvImage yuvimage = new YuvImage(bytes, ImageFormat.NV21, width,
                height, null);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        yuvimage.compressToJpeg(rect, 50, out);

        byte[] bytes1 = out.toByteArray();
        final Bitmap bitmap = BitmapFactory.decodeByteArray(bytes1, 0, bytes1.length);

        faceDetection f = new faceDetection();

        Log.i("yo", "ankit");
        //Log.i("yo",mtcnn.isCOmplete()+"");
        Matrix matrix = new Matrix();
        int rotation = getWindowManager().getDefaultDisplay().getRotation();
        int degrees = 0;

        switch (rotation) {
            case Surface.ROTATION_0:
                degrees = 90;
                break;
            case Surface.ROTATION_90:
                degrees = 0;
                break;
            case Surface.ROTATION_180:
                degrees = 270;
                break;
            case Surface.ROTATION_270:
                degrees = 180;
                break;
        }

        matrix.postRotate(degrees);
        matrix.preScale(-1.0f, 1.0f);

        Bitmap scaledBitmap = Bitmap.createScaledBitmap(bitmap, width, height, true);

        Bitmap rotatedBitmap = Bitmap.createBitmap(scaledBitmap, 0, 0, scaledBitmap.getWidth(), scaledBitmap.getHeight(), matrix, true);
        if (i[0] == 0) {
            f.execute(rotatedBitmap);
            i[0]++;
        }

        if (mtcnn.isCOmplete() == 1) {
            Log.i("mobilecamera", width + " " + height);
            f.execute(rotatedBitmap);
        }
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
                    drawBox(boxes.get(i).transform2Rect());
                }
                face.put("rects",recIDK);
                face.put("landmarks",landIDK);
                face.put("face_pose",poseIDK);
                done.put("faces",face.toString());
                if(boxes.size()>0){
                    //sendData(done);
                }
            }catch (Exception e){
                Log.e(TAG,"[*]detect false:"+e);
            }
            return transBmp;
        }
        @Override
        protected void onPostExecute(final Bitmap bitmap) {
            MobileCamera.this.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    img.setImageBitmap(bitmap);
                }
            });
            super.onPostExecute(bitmap);
        }

    }

    Canvas canvas;


    private void drawBox(Rect rect){
        canvas = boxHolder.lockCanvas(null);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setStyle(Paint.Style.STROKE);
        paint.setColor(Color.GREEN);
        paint.setStrokeWidth(3f);
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
        canvas.drawRect(rect,paint);
        boxHolder.unlockCanvasAndPost(canvas);
    }

    private String getPose(Point[] landmark) {
        if (abs(landmark[0].x - landmark[2].x) / abs(landmark[1].x - landmark[2].x) > 2)
            return "Left";
        else if(abs(landmark[1].x - landmark[2].x) / abs(landmark[0].x - landmark[2].x) > 2)
            return "Right";
        else
            return "Center";
    }

    int an=0;

    private void sendData(JSONObject done) throws JSONException {
        k++;
        t_start_api = System.currentTimeMillis();
        JsonObjectRequest request_json = new JsonObjectRequest(Request.Method.POST,"https://192.168.3.182:3500/recognise", done ,
                new Response.Listener<JSONObject>() {
                    @Override
                    public void onResponse(JSONObject response) {
                        //Process os success response
                        j++;
                        Log.i("coolboy",response.toString());
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
        request_json.setRetryPolicy(new DefaultRetryPolicy(300, 0, 0));
        requestQueue.add(request_json);
    }

    @Override
    protected void onDestroy() {
        Log.i("result", k+" "+j);
        super.onDestroy();
    }



    private void refreshCamera(){
        if (camHolder.getSurface() == null)
            return;
        try {
            camera.stopPreview();
        } catch (Exception e){
            e.printStackTrace();
        }
        try {
            camera.setPreviewDisplay(camHolder);
            camera.startPreview();
        } catch (Exception e){
            e.printStackTrace();
        }
    }
    private void setupSurfaceHolder(){
        Log.i("test","123213");
        camView.setSecure(true);
        camHolder = camView.getHolder();
        camHolder.addCallback(this);

        boxHolder = boxView.getHolder();
        boxHolder.addCallback(this);
        boxHolder.setFormat(PixelFormat.TRANSLUCENT);
        boxView.setZOrderMediaOverlay(true);
    }
    private void setCameraDisplay(){
        Display display = ((WindowManager)getSystemService(WINDOW_SERVICE)).getDefaultDisplay();
        if (display.getRotation() == Surface.ROTATION_0)
            camera.setDisplayOrientation(90);
    }

    private boolean checkPermission() {
        int currentAPIVersion = Build.VERSION.SDK_INT;
        if (currentAPIVersion >= android.os.Build.VERSION_CODES.M) {
            ArrayList<String> permissionsNotGranted = new ArrayList<>();
            for (String permission : neededPermissions)
                if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED)
                    permissionsNotGranted.add(permission);
            if (permissionsNotGranted.size() > 0) {
                boolean shouldShowAlert = false;
                for (String permission : permissionsNotGranted)
                    shouldShowAlert = ActivityCompat.shouldShowRequestPermissionRationale(this, permission);
                if (shouldShowAlert)
                    showPermissionAlert(permissionsNotGranted.toArray(new String[permissionsNotGranted.size()]));
                else
                    requestPermissions(permissionsNotGranted.toArray(new String[permissionsNotGranted.size()]));
                return false;
            }
        }
        return true;
    }
    private void showPermissionAlert(final String[] permissions) {
        AlertDialog.Builder alertBuilder = new AlertDialog.Builder(this);
        alertBuilder.setCancelable(true);
        alertBuilder.setTitle("Permission Required");
        alertBuilder.setMessage("You must grant permission to access camera to run this application.");
        alertBuilder.setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                requestPermissions(permissions);
            }
        });
        AlertDialog alert = alertBuilder.create();
        alert.show();
    }

    private void requestPermissions(String[] permissions) {
        ActivityCompat.requestPermissions(this, permissions, 100);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch (requestCode) {
            case 100:
                for (int result : grantResults)
                    if (result == PackageManager.PERMISSION_DENIED) {
                        Toast.makeText(this, "Permission is required", Toast.LENGTH_LONG).show();
                        return;
                    }
                setupSurfaceHolder();
                break;
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        try {
            camera = Camera.open(cameraId);
        } catch (Exception e){
            e.printStackTrace();
            return;
        }
        setCameraDisplay();
        try {
            camera.setPreviewDisplay(camHolder);
            camera.startPreview();
            inPreview = true;
        } catch (Exception e){
            e.printStackTrace();
        }
    }
    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        refreshCamera();
    }
    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        camera.release();
    }


}
*/
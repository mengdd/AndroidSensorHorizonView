package com.ddmeng.hellohorizonview;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.Camera;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

/**
 * This is based on demo of Pro Android AR 4 AR Demo
 * at: https://github.com/RaghavSood/ProAndroidAugmentedReality
 */
public class MainActivity extends AppCompatActivity {

    private static final String DEBUG_TAG = "HorizonView";

    private static final int MY_PERMISSIONS_REQUEST_LOCATION = 1;
    private static final int MY_PERMISSIONS_REQUEST_CAMERA = 2;

    float[] aValues = new float[3];
    float[] mValues = new float[3];
    HorizonView horizonView;
    SensorManager sensorManager;
    LocationManager locationManager;

    Button updateAltitudeButton;
    TextView altitudeValue;

    SurfaceView cameraPreview;
    SurfaceHolder previewHolder;
    Camera camera;
    boolean inPreview;

    final static String TAG = "PAAR";

    double currentAltitude;
    double pitch;
    double newAltitude;
    double changeInAltitude;
    double thetaTan;

    private int cameraId;


    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        setContentView(R.layout.activity_main);

        inPreview = false;

        cameraPreview = (SurfaceView) findViewById(R.id.cameraPreview);
        previewHolder = cameraPreview.getHolder();
        previewHolder.addCallback(surfaceCallback);
        previewHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);

        altitudeValue = (TextView) findViewById(R.id.altitudeValue);

        updateAltitudeButton = (Button) findViewById(R.id.altitudeUpdateButton);
        updateAltitudeButton.setOnClickListener(new View.OnClickListener() {

            public void onClick(View arg0) {
                updateAltitude();
            }

        });

        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        requestLocationWithPermissionCheck();

        horizonView = (HorizonView) this.findViewById(R.id.horizonView);
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        updateOrientation(new float[]{0, 0, 0});

        cameraId = getDefaultCameraId();
    }

    LocationListener locationListener = new LocationListener() {
        public void onLocationChanged(Location location) {
            currentAltitude = location.getAltitude();
        }

        public void onProviderDisabled(String arg0) {
            //Not Used
        }

        public void onProviderEnabled(String arg0) {
            //Not Used
        }

        public void onStatusChanged(String arg0, int arg1, Bundle arg2) {
            //Not Used
        }
    };

    public void updateAltitude() {
        int time = 300;
        float speed = 4.5f;

        double distanceMovedParallelToGround = (speed * time) * 0.3048;
        if (pitch != 0 && currentAltitude != 0) {
            thetaTan = Math.tan(pitch);
            changeInAltitude = thetaTan * distanceMovedParallelToGround;
            newAltitude = currentAltitude + changeInAltitude;
            altitudeValue.setText(String.valueOf(newAltitude));
        } else {
            altitudeValue.setText("Try Again");
        }
    }

    private Camera.Size getBestPreviewSize(int width, int height, Camera.Parameters parameters) {
        Camera.Size result = null;

        for (Camera.Size size : parameters.getSupportedPreviewSizes()) {
            if (size.width <= width && size.height <= height) {
                if (result == null) {
                    result = size;
                } else {
                    int resultArea = result.width * result.height;
                    int newArea = size.width * size.height;

                    if (newArea > resultArea) {
                        result = size;
                    }
                }
            }
        }

        return (result);
    }

    SurfaceHolder.Callback surfaceCallback = new SurfaceHolder.Callback() {
        public void surfaceCreated(SurfaceHolder holder) {
            try {
                camera.setPreviewDisplay(previewHolder);
            } catch (Throwable t) {
                Log.e(TAG, "Exception in setPreviewDisplay()", t);
            }
        }

        public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            if (camera == null) {
                return;
            }
            Camera.Parameters parameters = camera.getParameters();
            Camera.Size size = getBestPreviewSize(width, height, parameters);

            if (size != null) {
                parameters.setPreviewSize(size.width, size.height);
                camera.setParameters(parameters);
                camera.startPreview();
                inPreview = true;
            }
        }

        public void surfaceDestroyed(SurfaceHolder holder) {
            // not used
        }

    };

    private void updateOrientation(float[] values) {
        if (horizonView != null) {
            horizonView.setBearing(values[0]);
            horizonView.setPitch(values[1]);
            horizonView.setRoll(-values[2]);
            horizonView.invalidate();
        }
    }

    private float[] calculateOrientation() {
        float[] values = new float[3];
        float[] R = new float[9];
        float[] outR = new float[9];

        SensorManager.getRotationMatrix(R, null, aValues, mValues);
        SensorManager.remapCoordinateSystem(R,
                SensorManager.AXIS_X,
                SensorManager.AXIS_Z,
                outR);
        SensorManager.getOrientation(outR, values);

        values[0] = (float) Math.toDegrees(values[0]);
        values[1] = (float) Math.toDegrees(values[1]);
        values[2] = (float) Math.toDegrees(values[2]);

        pitch = values[1];

        return values;
    }

    private final SensorEventListener sensorEventListener = new SensorEventListener() {
        public void onSensorChanged(SensorEvent event) {
            if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER)
                aValues = event.values;
            if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD)
                mValues = event.values;

            updateOrientation(calculateOrientation());
        }

        public void onAccuracyChanged(Sensor sensor, int accuracy) {
        }
    };

    @Override
    protected void onResume() {
        super.onResume();

        Sensor accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        Sensor magField = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);

        sensorManager.registerListener(sensorEventListener,
                accelerometer,
                SensorManager.SENSOR_DELAY_FASTEST);
        sensorManager.registerListener(sensorEventListener,
                magField,
                SensorManager.SENSOR_DELAY_FASTEST);

        resumeCameraWithPermissionCheck();
    }

    private boolean isResumed = false;

    /**
     * A safe way to get an instance of the Camera object.
     */
    public Camera getCameraInstance(int cameraId) {
        if (isResumed) {
            return camera;
        }
        isResumed = true;
        Log.d(DEBUG_TAG, "getCameraInstance");
        Camera c = null;
        try {
            c = Camera.open(cameraId); // attempt to get a Camera instance
        } catch (Exception e) {

            e.printStackTrace();
            Log.e(DEBUG_TAG, "Camera is not available");
        }
        return c; // returns null if camera is unavailable
    }

    private int getDefaultCameraId() {
        Log.d(DEBUG_TAG, "getDefaultCameraId");
        int defaultId = -1;

        // Find the total number of cameras available
        int mNumberOfCameras = Camera.getNumberOfCameras();

        // Find the ID of the default camera
        Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
        for (int i = 0; i < mNumberOfCameras; i++) {
            Camera.getCameraInfo(i, cameraInfo);
            Log.d(DEBUG_TAG, "camera info, orientation: "
                    + cameraInfo.orientation);
            if (cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_BACK) {
                defaultId = i;
            }
        }
        if (-1 == defaultId) {
            if (mNumberOfCameras > 0) {
                // 如果没有后向摄像头
                defaultId = 0;
            } else {
                // 没有摄像头
                Toast.makeText(MainActivity.this, "No camera", Toast.LENGTH_LONG)
                        .show();
            }
        }
        return defaultId;
    }

    @Override
    public void onPause() {
        isResumed = false;
        if (inPreview) {
            camera.stopPreview();
        }
        sensorManager.unregisterListener(sensorEventListener);
        if (null != camera) {
            camera.release();
            camera = null;
        }
        inPreview = false;

        super.onPause();
    }


    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {
        switch (requestCode) {
            case MY_PERMISSIONS_REQUEST_LOCATION: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {

                    locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 2000, 2, locationListener);
                    Log.i(DEBUG_TAG, "user granted the permission!");
                } else {
                    Log.i(DEBUG_TAG, "user denied the permission!");
                }
                break;
            }
            case MY_PERMISSIONS_REQUEST_CAMERA: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {

                    camera = getCameraInstance(cameraId);
                    Log.i(DEBUG_TAG, "user granted the permission!");
                } else {
                    Log.i(DEBUG_TAG, "user denied the permission!");
                }
                break;
            }

            // other 'case' lines to check for other
            // permissions this app might request
        }
    }

    private void requestLocationWithPermissionCheck() {
        if (PackageManager.PERMISSION_GRANTED == ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.ACCESS_FINE_LOCATION)) {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 2000, 2, locationListener);
            Log.i(DEBUG_TAG, "user has the permission already!");
        } else {
            Log.i(DEBUG_TAG, "user do not have this permission!");
            if (ActivityCompat.shouldShowRequestPermissionRationale(MainActivity.this,
                    Manifest.permission.ACCESS_FINE_LOCATION)) {

                // Show an explanation to the user *asynchronously* -- don't block
                // this thread waiting for the user's response! After the user
                // sees the explanation, try again to request the permission.
                Log.i(DEBUG_TAG, "we should explain why we need this permission!");
            } else {

                // No explanation needed, we can request the permission.
                Log.i(DEBUG_TAG, "==request the permission==");

                ActivityCompat.requestPermissions(MainActivity.this,
                        new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                        MY_PERMISSIONS_REQUEST_LOCATION);
            }
        }
    }

    public void resumeCameraWithPermissionCheck() {
        if (PackageManager.PERMISSION_GRANTED == ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.CAMERA)) {
            camera = getCameraInstance(cameraId);
            Log.i(DEBUG_TAG, "user has the permission already!");
        } else {
            Log.i(DEBUG_TAG, "user do not have this permission!");
            if (ActivityCompat.shouldShowRequestPermissionRationale(MainActivity.this,
                    Manifest.permission.CAMERA)) {

                // Show an explanation to the user *asynchronously* -- don't block
                // this thread waiting for the user's response! After the user
                // sees the explanation, try again to request the permission.
                Log.i(DEBUG_TAG, "we should explain why we need this permission!");
            } else {

                // No explanation needed, we can request the permission.
                Log.i(DEBUG_TAG, "==request the permission==");

                ActivityCompat.requestPermissions(MainActivity.this,
                        new String[]{Manifest.permission.CAMERA},
                        MY_PERMISSIONS_REQUEST_CAMERA);
            }
        }
    }
}

package com.example.imageutil;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.hardware.Camera;
import android.hardware.camera2.CameraManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Parcelable;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.annotation.RequiresApi;
import android.support.v4.content.FileProvider;
import android.util.Log;
import android.view.Surface;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static android.hardware.Camera.CameraInfo.CAMERA_FACING_BACK;
import static android.hardware.Camera.CameraInfo.CAMERA_FACING_FRONT;

public class ImageUtil {
    private static final int DEFAULT_MIN_WIDTH_QUALITY = 800;        // min pixels
    private static final String TAG = "ImagePicker";
    private static final String TEMP_IMAGE_NAME = "tempImage";

    public static int minWidthQuality = DEFAULT_MIN_WIDTH_QUALITY;
    private static Uri imageUri = null;
    private static File photoFile;
    private static int activeCameraId = 0;

    public static Intent getPickImageIntent(Context context) throws IOException {
        Intent chooserIntent = null;
        List<Intent> intentList = new ArrayList<>();
        Intent pickIntent = new Intent(Intent.ACTION_PICK,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        Intent takePhotoIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        takePhotoIntent.putExtra("return-data", true);
        Uri tempfile = FileProvider.getUriForFile(context, BuildConfig.APPLICATION_ID+".provider", saveAndGetFile(context));
        takePhotoIntent.putExtra(MediaStore.EXTRA_OUTPUT, tempfile);
        imageUri = tempfile;
        intentList = addIntentsToList(context, intentList, pickIntent);
        intentList = addIntentsToList(context, intentList, takePhotoIntent);
        if (intentList.size() > 0) {
            chooserIntent = Intent.createChooser(intentList.remove(intentList.size() - 1),
                    context.getString(R.string.pick_image_intent_text));
            chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, intentList.toArray(new Parcelable[]{}));
        }

        return chooserIntent;
    }

    private static List<Intent> addIntentsToList(Context context, List<Intent> list, Intent intent) {
        List<ResolveInfo> resInfo = context.getPackageManager().queryIntentActivities(intent, 0);
        for (ResolveInfo resolveInfo : resInfo) {
            String packageName = resolveInfo.activityInfo.packageName;
            Intent targetedIntent = new Intent(intent);
            targetedIntent.setPackage(packageName);
            list.add(targetedIntent);
            Log.d(TAG, "Intent: " + intent.getAction() + " package: " + packageName);
        }
        return list;
    }

    public static Uri getImageUri(){
        return imageUri;
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public static Bitmap getImageFromResult(Context context, int resultCode, Intent imageReturnedIntent, Activity activity) {
        Log.d(TAG, "getImageFromResult, resultCode: " + resultCode);
        Bitmap bm = null;
        File imageFile = photoFile;
        if (resultCode == Activity.RESULT_OK) {
            Uri selectedImage;
            boolean isCamera = (imageReturnedIntent == null ||
                    imageReturnedIntent.getData() == null  ||
                    imageReturnedIntent.getData().toString().contains(imageFile.toString()));
            if (isCamera) {     /** CAMERA **/
                // selected image chosen later
                selectedImage = imageUri;
                // dari kamera size asli nya besar banget
                int[] sampleSizes = new int[]{25};
                bm = getImageResized(context, selectedImage, sampleSizes);
            } else {            /** ALBUM **/
                selectedImage = imageReturnedIntent.getData();
                imageUri = selectedImage;
                int[] sampleSizes = new int[]{1};
                bm = getImageResized(context, selectedImage, sampleSizes);
            }

            // int rotation = getRotation(context, selectedImage, isCamera);
            // method diatas ga manjur yang jalan yang bawah ini
            if (isCamera){
                int rotation = getCorrectCameraOrientation(activity, activeCameraId);
                bm = rotate(bm, rotation);
            }
        }
        return bm;
    }

    private static File saveAndGetFile(Context context) throws IOException {
        String root = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).toString();
        File file = new File(root + "/saved_images");
        file.mkdirs();
        long nameseri = System.currentTimeMillis();
        String imageFileName = "pic"+Long.toString(nameseri)+".jpg";
        File storageDir =
                context.getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        photoFile = File.createTempFile(
                imageFileName,  /* prefix */
                ".jpg",         /* suffix */
                storageDir      /* directory */
        );
        String imageFilePath = photoFile.getAbsolutePath();
        return photoFile;
    }

    private static File getTempFile(Context context) {
        File imageFile = new File(context.getExternalCacheDir(), TEMP_IMAGE_NAME);
        imageFile.getParentFile().mkdirs();
        return imageFile;
    }

    private static Bitmap decodeBitmap(Context context, Uri theUri, int sampleSize) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sampleSize;
        AssetFileDescriptor fileDescriptor = null;
        try {
            fileDescriptor = context.getContentResolver().openAssetFileDescriptor(theUri, "r");
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        Bitmap actuallyUsableBitmap = BitmapFactory.decodeFileDescriptor(
                fileDescriptor.getFileDescriptor(), null, options);
        Log.d(TAG, options.inSampleSize + " sample method bitmap ... " +
                actuallyUsableBitmap.getWidth() + " " + actuallyUsableBitmap.getHeight());
        return actuallyUsableBitmap;
    }

    /**
     * Resize to avoid using too much memory loading big images (e.g.: 2560*1920)
     **/
    private static Bitmap getImageResized(Context context, Uri selectedImage, int[] samplesizes) {
        Bitmap bm = null;
        int i = 0;
        do {
            bm = decodeBitmap(context, selectedImage, samplesizes[i]);
            Log.d(TAG, "resizer: new bitmap width = " + bm.getWidth());
            i++;
        } while (bm.getWidth() < minWidthQuality && i < samplesizes.length);
        return bm;
    }

    private static Bitmap rotate(Bitmap bm, int rotation) {
        if (rotation != 0) {
            Matrix matrix = new Matrix();
            matrix.postRotate(rotation);
            Bitmap bmOut = Bitmap.createBitmap(bm, 0, 0, bm.getWidth(), bm.getHeight(), matrix, true);
            return bmOut;
        }
        return bm;
    }

    public static int getCameraId(int whichCamera){
        // which camera isinya CAMRA_FACING_FRONT atau BACK
        int cameraId = 0;
        int numberOfCameras = Camera.getNumberOfCameras();
        Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
        for (int i = 0; i < numberOfCameras; i++) {
            Camera.getCameraInfo(i, cameraInfo);
            if (cameraInfo.facing == whichCamera) {
                cameraId = i;
            }
        }
        return cameraId;
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public static void getCameraInUseV2(Context mContext){
        CameraManager cam_manager = (CameraManager) mContext.getSystemService(Context.CAMERA_SERVICE);
        CameraManager.AvailabilityCallback camAvailCallback = new CameraManager.AvailabilityCallback() {
            public void onCameraAvailable(String cameraId) {
                activeCameraId =Integer.parseInt(cameraId);
                Log.d(TAG, "notified that camera is not in use.");
            }
            public void onCameraUnavailable(String cameraId) {
                Log.d(TAG, "notified that camera is in use.");

            }
        };
        cam_manager.registerAvailabilityCallback(camAvailCallback, null);
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public static int getCorrectCameraOrientation(Activity activity, int cameraid) {
        getCameraInUseV2(activity);
        int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();
        int degrees = 0;
        switch(rotation){
            case Surface.ROTATION_0:
                degrees = 0;
                break;

            case Surface.ROTATION_90:
                degrees = 90;
                break;

            case Surface.ROTATION_180:
                degrees = 180;
                break;

            case Surface.ROTATION_270:
                degrees = 270;
                break;
        }
        int result=-1;
        int cameraId = cameraid;
        Camera.CameraInfo info = new Camera.CameraInfo();
        Camera.getCameraInfo(cameraId, info);
        if(info.facing== CAMERA_FACING_FRONT){
            result = (info.orientation - degrees + 360) % 360;
            Log.d("kameradepan","lho");
        }else if(info.facing== CAMERA_FACING_BACK){
            result = (info.orientation - degrees + 360) % 360;
            Log.d("kamerablakang","lho");
        }
        Log.d("danresultnya", Integer.toString(result));
        return result;
    }
}

/*
 * Copyright 2019 The TensorFlow Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.capstoneandroidapp;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.hardware.camera2.CameraCharacteristics;
import android.media.ImageReader.OnImageAvailableListener;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.util.Log;
import android.util.Size;
import android.util.TypedValue;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.tasks.OnSuccessListener;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceDetectorOptions;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import com.example.capstoneandroidapp.customview.OverlayView;
import com.example.capstoneandroidapp.customview.OverlayView.DrawCallback;
import com.example.capstoneandroidapp.env.BorderedText;
import com.example.capstoneandroidapp.env.ImageUtils;
import com.example.capstoneandroidapp.env.Logger;
import com.example.capstoneandroidapp.tflite.Classifier;
import com.example.capstoneandroidapp.tflite.MaskTFLiteObjectDetectionAPIModel;
import com.example.capstoneandroidapp.tracking.MaskMultiBoxTracker;

/**
 * An activity that uses a TensorFlowMultiBoxDetector and ObjectTracker to detect and then track
 * objects.
 */
public class MaskDetectorActivity extends MaskCameraActivity implements OnImageAvailableListener {
  private static final Logger LOGGER = new Logger();

  // Configuration values for the prepackaged SSD model.
  //private static final int TF_OD_API_INPUT_SIZE = 300;
  //private static final boolean TF_OD_API_IS_QUANTIZED = true;
  //private static final String TF_OD_API_MODEL_FILE = "detect.tflite";
  //private static final String TF_OD_API_LABELS_FILE = "file:///android_asset/labelmap.txt";

  //private static final Size DESIRED_PREVIEW_SIZE = new Size(640, 480);

  // Face Mask
  private static final int TF_OD_API_INPUT_SIZE = 224;
  private static final boolean TF_OD_API_IS_QUANTIZED = false;
  private static final String TF_OD_API_MODEL_FILE = "mask_detector.tflite";
  private static final String TF_OD_API_LABELS_FILE = "file:///android_asset/mask_labelmap.txt";

  private static final DetectorMode MODE = DetectorMode.TF_OD_API;
  // Minimum detection confidence to track a detection.
  private static final float MINIMUM_CONFIDENCE_TF_OD_API = 0.5f;
  private static final boolean MAINTAIN_ASPECT = false;

  private static final Size DESIRED_PREVIEW_SIZE = new Size(800, 600);
  //private static final int CROP_SIZE = 320;
  //private static final Size CROP_SIZE = new Size(320, 320);



  private static final boolean SAVE_PREVIEW_BITMAP = false;
  private static final float TEXT_SIZE_DIP = 10;
  OverlayView trackingOverlay;
  private Integer sensorOrientation;

  private Classifier detector;

  private long lastProcessingTimeMs;
  private Bitmap rgbFrameBitmap = null;
  private Bitmap croppedBitmap = null;
  private Bitmap cropCopyBitmap = null;

  private boolean computingDetection = false;

  private long timestamp = 0;

  private Matrix frameToCropTransform;
  private Matrix cropToFrameTransform;

  private MaskMultiBoxTracker tracker;

  private BorderedText borderedText;

  // Face detector
  private FaceDetector faceDetector;

  // here the preview image is drawn in portrait way
  private Bitmap portraitBmp = null;
  // here the face is cropped and drawn
  private Bitmap faceBmp = null;

  private int phase;
  private int dialogFlag = 0;
  private int countPhaseOneMaskOn = 0;
  private int countPhaseOneMaskOff = 0;
  private int countPhaseThreeMaskOn = 0;
  private int countPhaseThreeMaskOff = 0;
  private long start, end;
  private double term;
  private String dialogMessage = "";
  private String name;
  private AlertDialog alertDialog;

  private ProgressBar progressBar;
  private int progressValuePhaseOneMaskOn = 0;
  private int progressValuePhaseOneMaskOff = 0;
  private int progressValuePhaseThreeMaskOn = 0;
  private int progressValuePhaseThreeMaskOff = 0;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    progressBar = (ProgressBar)findViewById(R.id.progressBar);
    progressBar.setProgress(0);

    Intent intent = getIntent();
    phase = intent.getIntExtra("phase", 1);
    name = intent.getStringExtra("name");

    // Real-time contour detection of multiple faces
    FaceDetectorOptions options =
            new FaceDetectorOptions.Builder()
                    .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                    .setContourMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
                    .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
                    .build();


    FaceDetector detector = FaceDetection.getClient(options);

    faceDetector = detector;

    //checkWritePermission();

  }

  @Override
  public void onPreviewSizeChosen(final Size size, final int rotation) {
    final float textSizePx =
            TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP, TEXT_SIZE_DIP, getResources().getDisplayMetrics());
    borderedText = new BorderedText(textSizePx);
    borderedText.setTypeface(Typeface.MONOSPACE);

    tracker = new MaskMultiBoxTracker(this);


    try {
      detector =
              MaskTFLiteObjectDetectionAPIModel.create(
                      getAssets(),
                      TF_OD_API_MODEL_FILE,
                      TF_OD_API_LABELS_FILE,
                      TF_OD_API_INPUT_SIZE,
                      TF_OD_API_IS_QUANTIZED);
      //cropSize = TF_OD_API_INPUT_SIZE;
    } catch (final IOException e) {
      e.printStackTrace();
      LOGGER.e(e, "Exception initializing classifier!");
      Toast toast =
              Toast.makeText(
                      getApplicationContext(), "Classifier could not be initialized", Toast.LENGTH_SHORT);
      toast.show();
      finish();
    }

    previewWidth = size.getWidth();
    previewHeight = size.getHeight();

    int screenOrientation = getScreenOrientation();
    sensorOrientation = rotation - screenOrientation;
    LOGGER.i("Camera orientation relative to screen canvas: %d", sensorOrientation);

    LOGGER.i("Initializing at size %dx%d", previewWidth, previewHeight);
    rgbFrameBitmap = Bitmap.createBitmap(previewWidth, previewHeight, Config.ARGB_8888);


    int targetW, targetH;
    if (sensorOrientation == 90 || sensorOrientation == 270) {
      targetH = previewWidth;
      targetW = previewHeight;
    }
    else {
      targetW = previewWidth;
      targetH = previewHeight;
    }
    int cropW = (int) (targetW / 2.0);
    int cropH = (int) (targetH / 2.0);


    croppedBitmap = Bitmap.createBitmap(cropW, cropH, Config.ARGB_8888);

    portraitBmp = Bitmap.createBitmap(targetW, targetH, Config.ARGB_8888);
    faceBmp = Bitmap.createBitmap(TF_OD_API_INPUT_SIZE, TF_OD_API_INPUT_SIZE, Config.ARGB_8888);

    frameToCropTransform =
            ImageUtils.getTransformationMatrix(
                    previewWidth, previewHeight,
                    cropW, cropH,
                    sensorOrientation, MAINTAIN_ASPECT);

//    frameToCropTransform =
//            ImageUtils.getTransformationMatrix(
//                    previewWidth, previewHeight,
//                    previewWidth, previewHeight,
//                    sensorOrientation, MAINTAIN_ASPECT);

    cropToFrameTransform = new Matrix();
    frameToCropTransform.invert(cropToFrameTransform);



    trackingOverlay = (OverlayView) findViewById(R.id.tracking_overlay);
    trackingOverlay.addCallback(
            new DrawCallback() {
              @Override
              public void drawCallback(final Canvas canvas) {
                tracker.draw(canvas);
                if (isDebug()) {
                  tracker.drawDebug(canvas);
                }
              }
            });

    tracker.setFrameConfiguration(previewWidth, previewHeight, sensorOrientation);
  }

  @Override
  protected void processImage() {
    ++timestamp;
    final long currTimestamp = timestamp;
    trackingOverlay.postInvalidate();

    // No mutex needed as this method is not reentrant.
    if (computingDetection) {
      readyForNextImage();
      return;
    }
    computingDetection = true;
    LOGGER.i("Preparing image " + currTimestamp + " for detection in bg thread.");

    rgbFrameBitmap.setPixels(getRgbBytes(), 0, previewWidth, 0, 0, previewWidth, previewHeight);

    readyForNextImage();

    final Canvas canvas = new Canvas(croppedBitmap);
    canvas.drawBitmap(rgbFrameBitmap, frameToCropTransform, null);
    // For examining the actual TF input.
    if (SAVE_PREVIEW_BITMAP) {
      ImageUtils.saveBitmap(croppedBitmap);
    }

        InputImage image = InputImage.fromBitmap(croppedBitmap, 0);
        faceDetector
                .process(image)
                .addOnSuccessListener(new OnSuccessListener<List<Face>>() {
                  @Override
                  public void onSuccess(List<Face> faces) {

                    if(dialogFlag == 1){
                      end = System.currentTimeMillis();
                      term = term + (end - start);
                      if(term >= 5000){
                        alertDialog.cancel();
                        dialogFlag = 0;
                        term = 0;
                        if(phase == 3 && countPhaseThreeMaskOn == 5) {
                          countPhaseOneMaskOn = 0;
                          countPhaseOneMaskOff = 0;
                          countPhaseThreeMaskOn = 0;
                          countPhaseThreeMaskOff = 0;
                          Intent intent9 = new Intent(MaskDetectorActivity.this, MaskDetectorActivity.class);
                          phase = 1;
                          intent9.putExtra("phase", phase);
                          intent9.putExtra("name", ".");
                          intent9.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                          //finish();
                          startActivity(intent9);
                        }
                        countPhaseOneMaskOn = 0;
                        countPhaseOneMaskOff = 0;
                        countPhaseThreeMaskOn = 0;
                        countPhaseThreeMaskOff = 0;
                      }
                      else{
                        start = System.currentTimeMillis();
                        alertDialog.setMessage(dialogMessage + "\n\n" + (int)((5000 - term)/1000));
                        alertDialog.show();
                      }
                    }

                    if (faces.size() == 0) { // 마스크 안 씀
                      updateResults(currTimestamp, new LinkedList<>());
                      return;
                    }
                    runInBackground(
                            new Runnable() {
                              @Override
                              public void run() {
                                onFacesDetected(currTimestamp, faces);
                              }
                            });
                  }

                });
  }

  @Override
  protected int getLayoutId() {
    return R.layout.tfe_od_camera_connection_fragment_tracking;
  }

  @Override
  protected Size getDesiredPreviewFrameSize() {
    return DESIRED_PREVIEW_SIZE;
  }

  // Which detection model to use: by default uses Tensorflow Object Detection API frozen
  // checkpoints.
  private enum DetectorMode {
    TF_OD_API;
  }

  @Override
  protected void setUseNNAPI(final boolean isChecked) {
    runInBackground(() -> detector.setUseNNAPI(isChecked));
  }

  @Override
  protected void setNumThreads(final int numThreads) {
    runInBackground(() -> detector.setNumThreads(numThreads));
  }


  // Face Mask Processing
  private Matrix createTransform(
          final int srcWidth,
          final int srcHeight,
          final int dstWidth,
          final int dstHeight,
          final int applyRotation) {

    Matrix matrix = new Matrix();
    if (applyRotation != 0) {
      if (applyRotation % 90 != 0) {
        LOGGER.w("Rotation of %d % 90 != 0", applyRotation);
      }

      // Translate so center of image is at origin.
      matrix.postTranslate(-srcWidth / 2.0f, -srcHeight / 2.0f);

      // Rotate around origin.
      matrix.postRotate(applyRotation);
    }

//        // Account for the already applied rotation, if any, and then determine how
//        // much scaling is needed for each axis.
//        final boolean transpose = (Math.abs(applyRotation) + 90) % 180 == 0;
//
//        final int inWidth = transpose ? srcHeight : srcWidth;
//        final int inHeight = transpose ? srcWidth : srcHeight;

    if (applyRotation != 0) {

      // Translate back from origin centered reference to destination frame.
      matrix.postTranslate(dstWidth / 2.0f, dstHeight / 2.0f);
    }

    return matrix;

  }

  private void updateResults(long currTimestamp, final List<Classifier.Recognition> mappedRecognitions) {

    tracker.trackResults(mappedRecognitions, currTimestamp);
    trackingOverlay.postInvalidate();
    computingDetection = false;


    runOnUiThread(
            new Runnable() {
              @Override
              public void run() {
                showFrameInfo(previewWidth + "x" + previewHeight);
                showCropInfo(croppedBitmap.getWidth() + "x" + croppedBitmap.getHeight());
                showInference(lastProcessingTimeMs + "ms");
              }
            });

  }

  private void onFacesDetected(long currTimestamp, List<Face> faces) {

    cropCopyBitmap = Bitmap.createBitmap(croppedBitmap);
    final Canvas canvas = new Canvas(cropCopyBitmap);
    final Paint paint = new Paint();
    paint.setColor(Color.RED);
    paint.setStyle(Style.STROKE);
    paint.setStrokeWidth(2.0f);

    float minimumConfidence = MINIMUM_CONFIDENCE_TF_OD_API;
    switch (MODE) {
      case TF_OD_API:
        minimumConfidence = MINIMUM_CONFIDENCE_TF_OD_API;
        break;
    }

    final List<Classifier.Recognition> mappedRecognitions =
            new LinkedList<Classifier.Recognition>();


    //final List<Classifier.Recognition> results = new ArrayList<>();

    // Note this can be done only once
    int sourceW = rgbFrameBitmap.getWidth();
    int sourceH = rgbFrameBitmap.getHeight();
    int targetW = portraitBmp.getWidth();
    int targetH = portraitBmp.getHeight();
    Matrix transform = createTransform(
            sourceW,
            sourceH,
            targetW,
            targetH,
            sensorOrientation);
    final Canvas cv = new Canvas(portraitBmp);

    // draws the original image in portrait mode.
    cv.drawBitmap(rgbFrameBitmap, transform, null);

    final Canvas cvFace = new Canvas(faceBmp);

    boolean saved = false;

    Handler handler = new Handler();
    handler.postDelayed(new Runnable(){
      @Override
      public void run() {
        Intent intent2 = getIntent();
        phase = intent2.getIntExtra("phase", 1);

        for (Face face : faces) {
          LOGGER.i("FACE" + face.toString());

          LOGGER.i("Running detection on face " + currTimestamp);

          //results = detector.recognizeImage(croppedBitmap);


          final RectF boundingBox = new RectF(face.getBoundingBox());

          //final boolean goodConfidence = result.getConfidence() >= minimumConfidence;
          final boolean goodConfidence = true; //face.get;
          if (boundingBox != null && goodConfidence) {

            // maps crop coordinates to original
            cropToFrameTransform.mapRect(boundingBox);

            // maps original coordinates to portrait coordinates
            RectF faceBB = new RectF(boundingBox);
            transform.mapRect(faceBB);

            // translates portrait to origin and scales to fit input inference size
            //cv.drawRect(faceBB, paint);
            float sx = ((float) TF_OD_API_INPUT_SIZE) / faceBB.width();
            float sy = ((float) TF_OD_API_INPUT_SIZE) / faceBB.height();
            Matrix matrix = new Matrix();
            matrix.postTranslate(-faceBB.left, -faceBB.top);
            matrix.postScale(sx, sy);

            cvFace.drawBitmap(portraitBmp, matrix, null);


            String label = "";
            float confidence = -1f;
            Integer color = Color.BLUE;

            final long startTime = SystemClock.uptimeMillis();
            final List<Classifier.Recognition> resultsAux = detector.recognizeImage(faceBmp);
            lastProcessingTimeMs = SystemClock.uptimeMillis() - startTime;

            if (resultsAux.size() > 0) {

              Classifier.Recognition result = resultsAux.get(0);

              float conf = result.getConfidence();

              if (conf >= 0.6f) {
                confidence = conf;
                label = result.getTitle();
                if (result.getId().equals("0")) {
                  color = Color.GREEN;
                  // 마스크 쓴 경우
                  if(phase == 1){
                    if(countPhaseOneMaskOn == 5){ // count = 10이어야만 다이얼로그를 띄움
                      if(dialogFlag == 0){
                        start = System.currentTimeMillis();
                        term = 0;
                        dialogFlag = 1;
                        progressValuePhaseOneMaskOn = 0;
                        progressBar.setProgress(progressValuePhaseOneMaskOn);
                        ContextThemeWrapper cw = new ContextThemeWrapper(MaskDetectorActivity.this, R.style.AlertDialogTheme);
                        AlertDialog.Builder builder = new AlertDialog.Builder(cw);
                        dialogMessage = "마스크를 벗고 카메라 앞에 서 주세요.";
                        builder.setTitle("공지").setMessage(dialogMessage + "\n\n" + (int)((5000 - term)/1000));
                        builder.setPositiveButton("확인", new DialogInterface.OnClickListener(){
                          @Override
                          public void onClick(DialogInterface dialog, int id) {
                            dialogFlag = 0;
                            term = 0;
                            countPhaseOneMaskOn = 0;
                            countPhaseOneMaskOff = 0;
                            countPhaseThreeMaskOn = 0;
                            countPhaseThreeMaskOff = 0;
                          }
                        });
                        alertDialog = builder.create();
                        alertDialog.show();
                        TextView messageView = (TextView)alertDialog.findViewById(android.R.id.message);
                        messageView.setGravity(Gravity.CENTER);
                      }
                    }
                    else{
                      countPhaseOneMaskOn = countPhaseOneMaskOn + 1;
                      progressValuePhaseOneMaskOff = 0;
                      progressValuePhaseOneMaskOn = progressValuePhaseOneMaskOn + 20;
                      progressBar.setProgress(progressValuePhaseOneMaskOn);
                      countPhaseOneMaskOff = 0;
                      //Log.v("test", "->" + countPhaseOneMaskOn);
                    }
                    /*
                    // 마스크 벗어달라고 팝업 띄우기 (PopupActivity 로 전환 + Phase 값 넘기기 -> MaskDetectorActivity 닫기)
                    Intent intent3 = new Intent(MaskDetectorActivity.this, PopupActivity.class);
                    intent3.putExtra("data", "마스크를 벗어주세요.");
                    intent3.putExtra("phase", phase);
                    intent3.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    //finish();
                    startActivity(intent3);
                    */
                  }
                  else if(phase == 3){
                    if(countPhaseThreeMaskOn == 5){
                      if(dialogFlag == 0){
                        start = System.currentTimeMillis();
                        term = 0;
                        dialogFlag = 1;
                        progressValuePhaseThreeMaskOn = 0;
                        progressBar.setProgress(progressValuePhaseThreeMaskOn);
                        ContextThemeWrapper cw = new ContextThemeWrapper(MaskDetectorActivity.this, R.style.AlertDialogTheme);
                        AlertDialog.Builder builder = new AlertDialog.Builder(cw);
                        dialogMessage = name + "님,\n환영합니다!";
                        builder.setTitle("공지").setMessage(dialogMessage + "\n\n" + (int)((5000 - term)/1000));
                        builder.setPositiveButton("확인", new DialogInterface.OnClickListener(){
                          @Override
                          public void onClick(DialogInterface dialog, int id) {
                            phase = 1;
                            dialogFlag = 0;
                            term = 0;
                            countPhaseOneMaskOn = 0;
                            countPhaseOneMaskOff = 0;
                            countPhaseThreeMaskOn = 0;
                            countPhaseThreeMaskOff = 0;
                            Intent intent9 = new Intent(MaskDetectorActivity.this, MaskDetectorActivity.class);
                            phase = 1;
                            intent9.putExtra("phase", phase);
                            intent9.putExtra("name", ".");
                            intent9.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                            //finish();
                            startActivity(intent9);
                          }
                        });
                        alertDialog = builder.create();
                        alertDialog.show();
                        TextView messageView = (TextView)alertDialog.findViewById(android.R.id.message);
                        messageView.setGravity(Gravity.CENTER);
                      }
                    }
                    else{
                      countPhaseThreeMaskOn = countPhaseThreeMaskOn + 1;
                      progressValuePhaseThreeMaskOff = 0;
                      progressValuePhaseThreeMaskOn = progressValuePhaseThreeMaskOn + 20;
                      progressBar.setProgress(progressValuePhaseThreeMaskOn);
                      countPhaseThreeMaskOff = 0;
                    }
                    /*
                    // 환영한다는 팝업 띄우기 (PopupActivity 로 전환 + Phase 값 넘기기 -> MaskDetectorActivity 닫기)
                    Intent intent4 = new Intent(MaskDetectorActivity.this, PopupActivity.class);
                    intent4.putExtra("data", "환영합니다.");
                    phase = 1;
                    intent4.putExtra("phase", phase);
                    intent4.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    //finish();
                    startActivity(intent4);
                     */
                  }
                }
                else {
                  color = Color.RED;
                  // 마스크 안 쓴 경우
                    if(phase == 1){
                      if(countPhaseOneMaskOff == 5){
                        countPhaseOneMaskOn = 0;
                        countPhaseOneMaskOff = 0;
                        countPhaseThreeMaskOn = 0;
                        countPhaseThreeMaskOff = 0;
                        progressValuePhaseOneMaskOff = 0;
                        progressBar.setProgress(progressValuePhaseOneMaskOff);
                        // DetectorActivity 로 전환 + Phase 값 (=2) 넘기기 -> MaskDetectorActivity 닫기
                        Intent intent5 = new Intent(MaskDetectorActivity.this, DetectorActivity.class);
                        phase = 2;
                        intent5.putExtra("phase", phase);
                        intent5.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        //finish();
                        startActivity(intent5);
                      }
                      else{
                        countPhaseOneMaskOff = countPhaseOneMaskOff + 1;
                        progressValuePhaseOneMaskOn = 0;
                        progressValuePhaseOneMaskOff = progressValuePhaseOneMaskOff + 20;
                        progressBar.setProgress(progressValuePhaseOneMaskOff);
                        countPhaseOneMaskOn = 0;
                      }

                    }
                    else if(phase == 3){
                      if(countPhaseThreeMaskOff == 5){
                        if(dialogFlag == 0){
                          start = System.currentTimeMillis();
                          term = 0;
                          dialogFlag = 1;
                          progressValuePhaseThreeMaskOff = 0;
                          progressBar.setProgress(progressValuePhaseThreeMaskOff);
                          ContextThemeWrapper cw = new ContextThemeWrapper(MaskDetectorActivity.this, R.style.AlertDialogTheme);
                          AlertDialog.Builder builder = new AlertDialog.Builder(cw);
                          dialogMessage = name + "님,\n마스크를 다시 써주세요!";
                          builder.setTitle("공지").setMessage(dialogMessage + "\n\n" + (int)((5000 - term)/1000));
                          builder.setPositiveButton("확인", new DialogInterface.OnClickListener(){
                            @Override
                            public void onClick(DialogInterface dialog, int id) {
                              dialogFlag = 0;
                              term = 0;
                              countPhaseOneMaskOn = 0;
                              countPhaseOneMaskOff = 0;
                              countPhaseThreeMaskOn = 0;
                              countPhaseThreeMaskOff = 0;
                            }
                          });
                          alertDialog = builder.create();
                          alertDialog.show();
                          TextView messageView = (TextView)alertDialog.findViewById(android.R.id.message);
                          messageView.setGravity(Gravity.CENTER);
                        }
                      }
                      else{
                        countPhaseThreeMaskOff = countPhaseThreeMaskOff + 1;
                        progressValuePhaseThreeMaskOn = 0;
                        progressValuePhaseThreeMaskOff = progressValuePhaseThreeMaskOff + 20;
                        progressBar.setProgress(progressValuePhaseThreeMaskOff);
                        countPhaseThreeMaskOn = 0;
                      }
                      /*
                      // 마스크 다시 써달라고 팝업 띄우기 (PopupActivity 로 전환 + Phase 값 넘기기 -> MaskDetectorActivity 닫기)
                      Intent intent6 = new Intent(MaskDetectorActivity.this, PopupActivity.class);
                      intent6.putExtra("data", "마스크를 다시 써주세요.");
                      intent6.putExtra("phase", phase);
                      intent6.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                      //finish();
                      startActivity(intent6);
                       */
                    }

                }
              }

            }

            if (getCameraFacing() == CameraCharacteristics.LENS_FACING_FRONT) {

              // camera is frontal so the image is flipped horizontally
              // flips horizontally
              Matrix flip = new Matrix();
              if (sensorOrientation == 90 || sensorOrientation == 270) {
                flip.postScale(1, -1, previewWidth / 2.0f, previewHeight / 2.0f);
              }
              else {
                flip.postScale(-1, 1, previewWidth / 2.0f, previewHeight / 2.0f);
              }
              //flip.postScale(1, -1, targetW / 2.0f, targetH / 2.0f);
              flip.mapRect(boundingBox);

            }

            final Classifier.Recognition result = new Classifier.Recognition(
                    "0", label, confidence, boundingBox);

            result.setColor(color);
            result.setLocation(boundingBox);
            mappedRecognitions.add(result);
          }



        }
      }
    }, 1000);

    //    if (saved) {
//      lastSaved = System.currentTimeMillis();
//    }

    updateResults(currTimestamp, mappedRecognitions);


  }


}

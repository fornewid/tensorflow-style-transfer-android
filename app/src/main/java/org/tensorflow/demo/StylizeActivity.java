/*
 * Copyright 2017 The TensorFlow Authors. All Rights Reserved.
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

package org.tensorflow.demo;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.os.Build;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnTouchListener;
import android.widget.Button;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.ml.vision.common.FirebaseVisionImage;
import com.google.firebase.ml.vision.common.FirebaseVisionImageMetadata;
import com.otaliastudios.cameraview.CameraView;
import com.otaliastudios.cameraview.frame.Frame;
import com.otaliastudios.cameraview.frame.FrameProcessor;

import timber.log.Timber;

/**
 * Sample activity that stylizes the camera preview according to "A Learned Representation For
 * Artistic Styles" (https://arxiv.org/abs/1610.07629)
 */
public class StylizeActivity extends AppCompatActivity implements FrameProcessor {

    private static final int PERMISSIONS_REQUEST = 1;

    private static final String PERMISSION_CAMERA = Manifest.permission.CAMERA;

    private CameraView cameraView;

    // Whether to actively manipulate non-selected sliders so that sum of activations always appears
    // to be 1.0. The actual style input tensor will be normalized to sum to 1.0 regardless.
    private static final boolean NORMALIZE_SLIDERS = true;

    private static final int[] SIZES = {128, 192, 256, 384, 512, 720};

    // Start at a medium size, but let the user step up through smaller sizes so they don't get
    // immediately stuck processing a large image.
    private int desiredSizeIndex = -1;
    private int desiredSize = 720;
    private int initializedSize = 0;

    private Bitmap croppedBitmap = null;

    private boolean computing = false;

    private Matrix frameToCropTransform;

    private int lastOtherStyle = 1;

    private boolean allZero = false;

    private ImageGridAdapter adapter;

    private ImageView overlay;

    private Stylize stylize;

    private Button sizeButton;

    private final OnTouchListener gridTouchAdapter = new OnTouchListener() {
        ImageSlider slider = null;

        @Override
        public boolean onTouch(final View v, final MotionEvent event) {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    for (int i = 0; i < Styles.getCount(); ++i) {
                        final ImageSlider child = adapter.getItem(i);
                        final Rect rect = new Rect();
                        child.getHitRect(rect);
                        if (rect.contains((int) event.getX(), (int) event.getY())) {
                            slider = child;
                            slider.setHighlight(true);
                        }
                    }
                    break;

                case MotionEvent.ACTION_MOVE:
                    if (slider != null) {
                        final Rect rect = new Rect();
                        slider.getHitRect(rect);

                        final float newSliderVal = (float) Math.min(1.0, Math.max(0.0, 1.0 - (event.getY() - slider.getTop()) / slider.getHeight()));

                        setStyle(slider, newSliderVal);
                    }
                    break;

                case MotionEvent.ACTION_UP:
                    if (slider != null) {
                        slider.setHighlight(false);
                        slider = null;
                    }
                    break;

                default: // fall out

            }
            return true;
        }
    };

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_stylize);

        stylize = new Stylize(getAssets());

        cameraView = findViewById(R.id.cameraView);
        overlay = findViewById(R.id.preview);
        sizeButton = findViewById(R.id.sizeButton);
        sizeButton.setText("" + desiredSize);
        sizeButton.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(final View v) {
                desiredSizeIndex = (desiredSizeIndex + 1) % SIZES.length;
                desiredSize = SIZES[desiredSizeIndex];
                sizeButton.setText("" + desiredSize);
                sizeButton.postInvalidate();
            }
        });

        adapter = new ImageGridAdapter(this, Styles.getThumbnails());
        GridView grid = findViewById(R.id.grid_layout);
        grid.setAdapter(adapter);
        grid.setOnTouchListener(gridTouchAdapter);

        setStyle(adapter.getItem(0), 1.0f);

        if (hasPermission()) {
            bindCamera();
        } else {
            requestPermission();
        }
    }

    @Override
    public void process(@NonNull Frame frame) {
        if (computing) return;
        computing = true;

        int previewWidth = frame.getSize().getWidth();
        int previewHeight = frame.getSize().getHeight();

        if (desiredSize != initializedSize) {
            Timber.i("Initializing at size preview size %dx%d, stylize size %d",
                    previewWidth, previewHeight, desiredSize);
            croppedBitmap = Bitmap.createBitmap(desiredSize, desiredSize, Bitmap.Config.ARGB_8888);

            frameToCropTransform =
                    ImageUtils.getTransformationMatrix(
                            previewWidth, previewHeight,
                            desiredSize, desiredSize,
                            0, true);
            initializedSize = desiredSize;
        }

        Bitmap bitmap = FirebaseVisionImage
                .fromByteArray(frame.getData(), new FirebaseVisionImageMetadata.Builder()
                        .setFormat(FirebaseVisionImageMetadata.IMAGE_FORMAT_NV21)
                        .setWidth(frame.getSize().getWidth())
                        .setHeight(frame.getSize().getHeight())
                        .setRotation(FirebaseVisionImageMetadata.ROTATION_90)
                        .build())
                .getBitmap();

        final Canvas canvas = new Canvas(croppedBitmap);
        canvas.drawBitmap(bitmap, frameToCropTransform, null);

        final Bitmap stylizedImage = stylize.stylize(croppedBitmap, desiredSize);

        computing = false;

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                overlay.setImageBitmap(stylizedImage);
            }
        });
    }

    private void setStyle(final ImageSlider slider, final float value) {
        slider.setValue(value);

        final int styleCount = Styles.getCount();

        if (NORMALIZE_SLIDERS) {
            // Slider vals correspond directly to the input tensor vals, and normalization is visually
            // maintained by remanipulating non-selected sliders.
            float otherSum = 0.0f;

            for (int i = 0; i < styleCount; ++i) {
                if (adapter.getItem(i) != slider) {
                    otherSum += adapter.getItem(i).getValue();
                }
            }

            if (otherSum > 0.0) {
                float highestOtherVal = 0;
                final float factor = otherSum > 0.0f ? (1.0f - value) / otherSum : 0.0f;
                for (int i = 0; i < styleCount; ++i) {
                    final ImageSlider child = adapter.getItem(i);
                    if (child == slider) {
                        continue;
                    }
                    final float newVal = child.getValue() * factor;
                    child.setValue(newVal > 0.01f ? newVal : 0.0f);

                    if (child.getValue() > highestOtherVal) {
                        lastOtherStyle = i;
                        highestOtherVal = child.getValue();
                    }
                }
            } else {
                // Everything else is 0, so just pick a suitable slider to push up when the
                // selected one goes down.
                if (adapter.getItem(lastOtherStyle) == slider) {
                    lastOtherStyle = (lastOtherStyle + 1) % styleCount;
                }
                adapter.getItem(lastOtherStyle).setValue(1.0f - value);
            }
        }

        final boolean lastAllZero = allZero;
        float sum = 0.0f;
        for (int i = 0; i < styleCount; ++i) {
            sum += adapter.getItem(i).getValue();
        }
        allZero = sum == 0.0f;

        for (ImageSlider item : adapter.getItems()) {
            item.setAllZero(allZero);
        }

        // Now update the values used for the input tensor. If nothing is set, mix in everything
        // equally. Otherwise everything is normalized to sum to 1.0.
        final float[] styleVals = new float[styleCount];
        for (int i = 0; i < styleCount; ++i) {
            styleVals[i] = allZero ? 1.0f / styleCount : adapter.getItem(i).getValue() / sum;

            if (lastAllZero != allZero) {
                adapter.getItem(i).postInvalidate();
            }
        }
        stylize.setStyleValues(styleVals);
    }

    ///////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onDestroy() {
        cameraView.clearFrameProcessors();
        super.onDestroy();
    }

    @Override
    public void onRequestPermissionsResult(
            final int requestCode, @NonNull final String[] permissions, @NonNull final int[] grantResults) {
        if (requestCode == PERMISSIONS_REQUEST) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                bindCamera();
            } else {
                requestPermission();
            }
        }
    }

    private boolean hasPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return checkSelfPermission(PERMISSION_CAMERA) == PackageManager.PERMISSION_GRANTED;
        } else {
            return true;
        }
    }

    private void requestPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (shouldShowRequestPermissionRationale(PERMISSION_CAMERA)) {
                Toast.makeText(StylizeActivity.this, "Camera AND storage permission are required for this demo", Toast.LENGTH_LONG).show();
            }
            requestPermissions(new String[]{PERMISSION_CAMERA}, PERMISSIONS_REQUEST);
        }
    }

    private void bindCamera() {
        cameraView.setLifecycleOwner(this);
        cameraView.addFrameProcessor(this);
    }
}

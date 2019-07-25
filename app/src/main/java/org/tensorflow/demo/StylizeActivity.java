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
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.Rect;
import android.os.Build;
import android.os.Bundle;
import android.view.Display;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnTouchListener;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
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

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;

import timber.log.Timber;

/**
 * Sample activity that stylizes the camera preview according to "A Learned Representation For
 * Artistic Style" (https://arxiv.org/abs/1610.07629)
 */
public class StylizeActivity extends AppCompatActivity implements FrameProcessor {

    private static final int PERMISSIONS_REQUEST = 1;

    private static final String PERMISSION_CAMERA = Manifest.permission.CAMERA;

    private CameraView cameraView;

    private static final int NUM_STYLES = 26;

    // Whether to actively manipulate non-selected sliders so that sum of activations always appears
    // to be 1.0. The actual style input tensor will be normalized to sum to 1.0 regardless.
    private static final boolean NORMALIZE_SLIDERS = true;

    private static final int[] SIZES = {128, 192, 256, 384, 512, 720};

    // Start at a medium size, but let the user step up through smaller sizes so they don't get
    // immediately stuck processing a large image.
    private int desiredSizeIndex = -1;
    private int desiredSize = 720;
    private int initializedSize = 0;

    private Integer sensorOrientation;

    private int previewWidth = 0;
    private int previewHeight = 0;
    private Bitmap croppedBitmap = null;

    private final float[] styleVals = new float[NUM_STYLES];

    private boolean computing = false;

    private Matrix frameToCropTransform;

    private int lastOtherStyle = 1;

    private boolean allZero = false;

    private ImageGridAdapter adapter;

    private ImageView overlay;

    private Stylize stylize;

    private final OnTouchListener gridTouchAdapter = new OnTouchListener() {
        ImageSlider slider = null;

        @Override
        public boolean onTouch(final View v, final MotionEvent event) {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    for (int i = 0; i < NUM_STYLES; ++i) {
                        final ImageSlider child = adapter.items[i];
                        final Rect rect = new Rect();
                        child.getHitRect(rect);
                        if (rect.contains((int) event.getX(), (int) event.getY())) {
                            slider = child;
                            slider.setHilighted(true);
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
                        slider.setHilighted(false);
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
        overlay = findViewById(R.id.overlay);

        final Display display = getWindowManager().getDefaultDisplay();
        final int screenOrientation = display.getRotation();

        Timber.i("Sensor orientation: %d, Screen orientation: %d", 0, screenOrientation);

        sensorOrientation = screenOrientation;

        adapter = new ImageGridAdapter();
        GridView grid = findViewById(R.id.grid_layout);
        grid.setAdapter(adapter);
        grid.setOnTouchListener(gridTouchAdapter);

        setStyle(adapter.items[0], 1.0f);

        if (hasPermission()) {
            bindCamera();
        } else {
            requestPermission();
        }
    }

    public static Bitmap getBitmapFromAsset(final Context context, final String filePath) {
        final AssetManager assetManager = context.getAssets();

        Bitmap bitmap = null;
        try {
            final InputStream inputStream = assetManager.open(filePath);
            bitmap = BitmapFactory.decodeStream(inputStream);
        } catch (final IOException e) {
            Timber.e(e, "Error opening bitmap!");
        }

        return bitmap;
    }

    private class ImageSlider extends ImageView {
        private float value = 0.0f;
        private boolean hilighted = false;

        private final Paint boxPaint;
        private final Paint linePaint;

        public ImageSlider(final Context context) {
            super(context);

            boxPaint = new Paint();
            boxPaint.setColor(Color.BLACK);
            boxPaint.setAlpha(128);

            linePaint = new Paint();
            linePaint.setColor(Color.WHITE);
            linePaint.setStrokeWidth(10.0f);
            linePaint.setStyle(Style.STROKE);
        }

        @Override
        public void onDraw(final Canvas canvas) {
            super.onDraw(canvas);
            final float y = (1.0f - value) * getHeight();

            // If all sliders are zero, don't bother shading anything.
            if (!allZero) {
                canvas.drawRect(0, 0, getWidth(), y, boxPaint);
            }

            if (value > 0.0f) {
                canvas.drawLine(0, y, getWidth(), y, linePaint);
            }

            if (hilighted) {
                canvas.drawRect(0, 0, getWidth(), getHeight(), linePaint);
            }
        }

        @Override
        protected void onMeasure(final int widthMeasureSpec, final int heightMeasureSpec) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            setMeasuredDimension(getMeasuredWidth(), getMeasuredWidth());
        }

        public void setValue(final float value) {
            this.value = value;
            postInvalidate();
        }

        public void setHilighted(final boolean highlighted) {
            this.hilighted = highlighted;
            this.postInvalidate();
        }
    }

    private class ImageGridAdapter extends BaseAdapter {
        final ImageSlider[] items = new ImageSlider[NUM_STYLES];
        final ArrayList<Button> buttons = new ArrayList<>();

        {
            final Button sizeButton = new Button(StylizeActivity.this) {
                @Override
                protected void onMeasure(final int widthMeasureSpec, final int heightMeasureSpec) {
                    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
                    setMeasuredDimension(getMeasuredWidth(), getMeasuredWidth());
                }
            };
            sizeButton.setText("" + desiredSize);
            sizeButton.setOnClickListener(
                    new OnClickListener() {
                        @Override
                        public void onClick(final View v) {
                            desiredSizeIndex = (desiredSizeIndex + 1) % SIZES.length;
                            desiredSize = SIZES[desiredSizeIndex];
                            sizeButton.setText("" + desiredSize);
                            sizeButton.postInvalidate();
                        }
                    });
            buttons.add(sizeButton);

            for (int i = 0; i < NUM_STYLES; ++i) {
                Timber.v("Creating item %d", i);

                if (items[i] == null) {
                    final ImageSlider slider = new ImageSlider(StylizeActivity.this);
                    final Bitmap bm =
                            getBitmapFromAsset(StylizeActivity.this, "thumbnails/style" + i + ".jpg");
                    slider.setImageBitmap(bm);

                    items[i] = slider;
                }
            }
        }

        @Override
        public int getCount() {
            return buttons.size() + NUM_STYLES;
        }

        @Override
        public Object getItem(final int position) {
            if (position < buttons.size()) {
                return buttons.get(position);
            } else {
                return items[position - buttons.size()];
            }
        }

        @Override
        public long getItemId(final int position) {
            return getItem(position).hashCode();
        }

        @Override
        public View getView(final int position, final View convertView, final ViewGroup parent) {
            if (convertView != null) {
                return convertView;
            }
            return (View) getItem(position);
        }
    }

    @Override
    public void process(@NonNull Frame frame) {
        previewWidth = frame.getSize().getWidth();
        previewHeight = frame.getSize().getHeight();

        Bitmap bitmap = FirebaseVisionImage
                .fromByteArray(frame.getData(), new FirebaseVisionImageMetadata.Builder()
                        .setFormat(FirebaseVisionImageMetadata.IMAGE_FORMAT_NV21)
                        .setWidth(frame.getSize().getWidth())
                        .setHeight(frame.getSize().getHeight())
                        .setRotation(FirebaseVisionImageMetadata.ROTATION_90)
                        .build())
                .getBitmap();
        onImageAvailable(bitmap);
    }

    private void setStyle(final ImageSlider slider, final float value) {
        slider.setValue(value);

        if (NORMALIZE_SLIDERS) {
            // Slider vals correspond directly to the input tensor vals, and normalization is visually
            // maintained by remanipulating non-selected sliders.
            float otherSum = 0.0f;

            for (int i = 0; i < NUM_STYLES; ++i) {
                if (adapter.items[i] != slider) {
                    otherSum += adapter.items[i].value;
                }
            }

            if (otherSum > 0.0) {
                float highestOtherVal = 0;
                final float factor = otherSum > 0.0f ? (1.0f - value) / otherSum : 0.0f;
                for (int i = 0; i < NUM_STYLES; ++i) {
                    final ImageSlider child = adapter.items[i];
                    if (child == slider) {
                        continue;
                    }
                    final float newVal = child.value * factor;
                    child.setValue(newVal > 0.01f ? newVal : 0.0f);

                    if (child.value > highestOtherVal) {
                        lastOtherStyle = i;
                        highestOtherVal = child.value;
                    }
                }
            } else {
                // Everything else is 0, so just pick a suitable slider to push up when the
                // selected one goes down.
                if (adapter.items[lastOtherStyle] == slider) {
                    lastOtherStyle = (lastOtherStyle + 1) % NUM_STYLES;
                }
                adapter.items[lastOtherStyle].setValue(1.0f - value);
            }
        }

        final boolean lastAllZero = allZero;
        float sum = 0.0f;
        for (int i = 0; i < NUM_STYLES; ++i) {
            sum += adapter.items[i].value;
        }
        allZero = sum == 0.0f;

        // Now update the values used for the input tensor. If nothing is set, mix in everything
        // equally. Otherwise everything is normalized to sum to 1.0.
        for (int i = 0; i < NUM_STYLES; ++i) {
            styleVals[i] = allZero ? 1.0f / NUM_STYLES : adapter.items[i].value / sum;

            if (lastAllZero != allZero) {
                adapter.items[i].postInvalidate();
            }
        }
        stylize.setStyleValues(styleVals);
    }

    private void onImageAvailable(final Bitmap bitmap) {
        if (computing) return;

        if (desiredSize != initializedSize) {
            Timber.i("Initializing at size preview size %dx%d, stylize size %d",
                    previewWidth, previewHeight, desiredSize);
            croppedBitmap = Bitmap.createBitmap(desiredSize, desiredSize, Bitmap.Config.ARGB_8888);

            frameToCropTransform =
                    ImageUtils.getTransformationMatrix(
                            previewWidth, previewHeight,
                            desiredSize, desiredSize,
                            sensorOrientation, true);
            initializedSize = desiredSize;
        }

        computing = true;

        final Canvas canvas = new Canvas(croppedBitmap);
        canvas.drawBitmap(bitmap, frameToCropTransform, null);

        Bitmap cropCopyBitmap = Bitmap.createBitmap(croppedBitmap);
        final Bitmap stylizedImage = stylize.stylize(croppedBitmap, desiredSize);

        computing = false;

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                overlay.setImageBitmap(stylizedImage);
            }
        });
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

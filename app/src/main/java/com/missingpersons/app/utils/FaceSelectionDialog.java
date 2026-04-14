package com.missingpersons.app.utils;

import android.app.Dialog;
import android.content.Context;
import android.graphics.*;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AlertDialog;
import com.google.mlkit.vision.face.Face;
import java.util.List;

/**
 * FaceSelectionDialog v3.0
 * عرض الوجوه المكتشفة في شبكة واضحة مع إمكانية الاختيار
 * يُغلق تلقائياً عند الاختيار
 */
public class FaceSelectionDialog {

    private final Context context;
    private final Bitmap originalImage;
    private final List<Face> detectedFaces;
    private Dialog dialog;

    public FaceSelectionDialog(Context context, Bitmap image, List<Face> faces) {
        this.context = context;
        this.originalImage = image;
        this.detectedFaces = faces;
    }

    public void show(OnFaceSelectedListener listener) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("👥 اختر الوجه الصحيح (" + detectedFaces.size() + " وجوه)");
        builder.setMessage("اضغط على الوجه المطلوب:");

        ScrollView scrollView = new ScrollView(context);
        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(24, 16, 24, 16);

        // عرض الوجوه في صفوف (2 في كل صف)
        LinearLayout currentRow = null;
        for (int i = 0; i < detectedFaces.size(); i++) {
            if (i % 2 == 0) {
                currentRow = new LinearLayout(context);
                currentRow.setOrientation(LinearLayout.HORIZONTAL);
                currentRow.setGravity(Gravity.CENTER);
                LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
                rowParams.setMargins(0, 8, 0, 8);
                currentRow.setLayoutParams(rowParams);
                container.addView(currentRow);
            }

            Face face = detectedFaces.get(i);
            Bitmap faceCrop = cropFaceFromImage(originalImage, face);

            // حاوية لكل وجه
            LinearLayout faceLayout = new LinearLayout(context);
            faceLayout.setOrientation(LinearLayout.VERTICAL);
            faceLayout.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams flp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            flp.setMargins(8, 4, 8, 4);
            faceLayout.setLayoutParams(flp);
            faceLayout.setPadding(8, 8, 8, 8);
            faceLayout.setBackgroundColor(0x10000000);

            // صورة الوجه
            ImageView faceIv = new ImageView(context);
            int imgSize = (int)(120 * context.getResources().getDisplayMetrics().density);
            LinearLayout.LayoutParams ivlp = new LinearLayout.LayoutParams(imgSize, imgSize);
            faceIv.setLayoutParams(ivlp);
            faceIv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            faceIv.setImageBitmap(faceCrop);
            faceLayout.addView(faceIv);

            // تسمية
            TextView tvLabel = new TextView(context);
            tvLabel.setText("الوجه " + (i + 1));
            tvLabel.setGravity(Gravity.CENTER);
            tvLabel.setTextSize(14);
            tvLabel.setPadding(0, 8, 0, 4);
            faceLayout.addView(tvLabel);

            // زر اختيار
            final int index = i;
            faceLayout.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onFaceSelected(detectedFaces.get(index));
                }
                if (dialog != null) dialog.dismiss();
            });

            // تأثير الضغط
            faceLayout.setClickable(true);
            faceLayout.setFocusable(true);
            faceLayout.setForeground(context.getDrawable(
                android.R.drawable.list_selector_background));

            if (currentRow != null) currentRow.addView(faceLayout);
        }

        // إذا عدد فردي، أضف مساحة فارغة
        if (detectedFaces.size() % 2 != 0 && currentRow != null) {
            View spacer = new View(context);
            spacer.setLayoutParams(new LinearLayout.LayoutParams(
                0, 1, 1f));
            currentRow.addView(spacer);
        }

        scrollView.addView(container);
        builder.setView(scrollView);
        builder.setNegativeButton("إلغاء", (d, which) -> d.dismiss());

        dialog = builder.create();
        dialog.show();
    }

    /**
     * قص الوجه مع هامش حوله
     */
    private Bitmap cropFaceFromImage(Bitmap source, Face face) {
        Rect box = face.getBoundingBox();
        int padX = (int)(box.width() * 0.2);
        int padY = (int)(box.height() * 0.2);

        int x = Math.max(0, box.left - padX);
        int y = Math.max(0, box.top - padY);
        int width = Math.min(box.width() + padX * 2, source.getWidth() - x);
        int height = Math.min(box.height() + padY * 2, source.getHeight() - y);

        if (width <= 0 || height <= 0) return source;

        try {
            return Bitmap.createBitmap(source, x, y, width, height);
        } catch (Exception e) {
            return source;
        }
    }

    public interface OnFaceSelectedListener {
        void onFaceSelected(Face selectedFace);
    }
}

package com.liftlog.app;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Rect;
import android.net.Uri;

import com.google.android.gms.tasks.Tasks;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.InputStream;

/**
 * Reads the text of a body report image on the phone (ML Kit, offline) and returns every line
 * with its word boxes, so the page can match each value to its label.
 * Tall screenshots are read in overlapping slices so small text stays legible.
 */
final class ReportOcr {
    interface Callback {
        void done(String json);
    }

    private ReportOcr() {}

    static void read(Context context, Uri uri, Callback callback) {
        final Context app = context.getApplicationContext();
        new Thread(() -> {
            String out;
            try {
                out = run(app, uri);
            } catch (Exception e) {
                out = error(e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
            } catch (OutOfMemoryError e) {
                out = error("the image is too large");
            }
            callback.done(out);
        }, "liftlog-ocr").start();
    }

    private static String error(String message) {
        try {
            return new JSONObject().put("status", "error").put("message", message).toString();
        } catch (JSONException e) {
            return "{\"status\":\"error\"}";
        }
    }

    private static Bitmap decode(Context ctx, Uri uri) throws Exception {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream in = ctx.getContentResolver().openInputStream(uri)) {
            BitmapFactory.decodeStream(in, null, bounds);
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw new Exception("this file is not an image");
        int sample = 1;
        while (bounds.outWidth / (sample * 2) >= 1000) sample *= 2;
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inSampleSize = sample;
        try (InputStream in = ctx.getContentResolver().openInputStream(uri)) {
            Bitmap bmp = BitmapFactory.decodeStream(in, null, opts);
            if (bmp == null) throw new Exception("could not open the image");
            return bmp;
        }
    }

    private static JSONObject box(String text, Rect r, int top) throws JSONException {
        return new JSONObject()
            .put("t", text)
            .put("x", r.left)
            .put("y", r.top + top)
            .put("w", r.width())
            .put("h", r.height());
    }

    private static String run(Context ctx, Uri uri) throws Exception {
        Bitmap bmp = decode(ctx, uri);
        int width = bmp.getWidth(), height = bmp.getHeight();
        int slice = Math.max(800, (int) (width * 1.6f));
        int overlap = Math.max(120, width / 6);
        JSONArray lines = new JSONArray();
        TextRecognizer recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
        try {
            int top = 0;
            while (true) {
                int h = Math.min(slice, height - top);
                boolean last = top + h >= height;
                Bitmap part = (top == 0 && last) ? bmp : Bitmap.createBitmap(bmp, 0, top, width, h);
                Text text = Tasks.await(recognizer.process(InputImage.fromBitmap(part, 0)));
                // keep each line once: lines whose centre falls in the shared strip belong to one slice only
                int keepFrom = top == 0 ? 0 : top + overlap / 2;
                int keepTo = last ? height : top + h - overlap / 2;
                for (Text.TextBlock block : text.getTextBlocks()) {
                    for (Text.Line line : block.getLines()) {
                        Rect r = line.getBoundingBox();
                        if (r == null) continue;
                        int cy = top + r.centerY();
                        if (cy < keepFrom || cy >= keepTo) continue;
                        JSONObject lo = box(line.getText(), r, top);
                        JSONArray words = new JSONArray();
                        for (Text.Element el : line.getElements()) {
                            Rect er = el.getBoundingBox();
                            if (er != null) words.put(box(el.getText(), er, top));
                        }
                        lo.put("words", words);
                        lines.put(lo);
                    }
                }
                if (part != bmp) part.recycle();
                if (last) break;
                top += slice - overlap;
            }
        } finally {
            recognizer.close();
            bmp.recycle();
        }
        return new JSONObject()
            .put("status", "ok")
            .put("width", width)
            .put("height", height)
            .put("lines", lines)
            .toString();
    }
}

package com.example.imageloader.loader.processor;

import java.io.InputStream;

import android.graphics.Bitmap;

public class ResizeProcessor extends ImageProcessor {
    private int mWidth;
    private int mHeight;
    public ResizeProcessor(int reqWidth, int reqHeight) {
        mWidth = reqWidth;
        mHeight = reqHeight;
    }

    @Override
    public Bitmap process(InputStream is) {
        return null;
    }
}

package com.example.imageloader.loader.processor;

import java.io.InputStream;

import android.graphics.Bitmap;

public abstract class ImageProcessor {
    public abstract Bitmap process(InputStream is);
}

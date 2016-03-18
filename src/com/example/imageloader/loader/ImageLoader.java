package com.example.imageloader.loader;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.os.Environment;
import android.text.TextUtils;
import android.util.LruCache;
import android.widget.ImageView;

/**
 * 要实现的功能:
 * 1. 双缓存:从本地内存中加载图片LRUCache,从本地磁盘中加载图片DiskLRUCache
 * 2. 使用线程池提高加载效率
 * 3. 防止出现图片错位问题
 * 4. 要考虑到多个imageview对应同一个图片url的问题
 */
public class ImageLoader {
    private static final int KB = 1024;
    private static ImageLoader sImageLoader;
    private LruCache<String, Bitmap> mMemLruCache;
    private DiskLruCache mDiskLruCache;
    private final Object mDiskCacheLock = new Object();
    private boolean mDiskCacheStarting = true;
    private static final String DISK_CACHE_SUBDIR = "thumbnails";
    private int mMaxMemCacheSize;
    private int mMaxDiskCacheSize;

    private ImageLoader(Context context) {
        init(context);
    }

    public static ImageLoader get(Context context) {
        if (sImageLoader == null) {
            sImageLoader = new ImageLoader(context);
        }
        return sImageLoader;
    }

    private void init(Context context) {
        initMaxCacheSize();
        initLruCache();
        initDiskLruCache(context);
    }

    public void load(ImageView imageView, String url) {
        Bitmap bmp = getBitmapFromCache(url);
        if (bmp != null) {
            imageView.setImageBitmap(bmp);
        } else {
            imageView.setTag(url);
            loadBitmapFromHttp(imageView, url);
        }
    }

    private void loadBitmapFromHttp(ImageView imageView, String url) {

    }

    private Bitmap getBitmapFromCache(String url) {
        // 得到md5
        String key = LoaderUtils.encodeMd5(url);
        if (TextUtils.isEmpty(key)) {
            return null;
        }
        Bitmap bitmap = getBitmapFromMemory(key);
        if (bitmap == null) {
            bitmap = getBitmapFromDiskCache(key);
        }
        return bitmap;
    }

    public void initMaxCacheSize() {
        int maxMemory = (int)(Runtime.getRuntime().maxMemory()/KB);  // 以kb为单位
        mMaxMemCacheSize = maxMemory/8;

        // 外部存储所占空间
        mMaxDiskCacheSize = 20 * 1024 * 1024; // 20MB
    }

    private void initLruCache() {
        if (mMemLruCache == null) {
            mMemLruCache = new LruCache<String, Bitmap>(mMaxMemCacheSize) {
                protected int sizeOf(String key, Bitmap value) {
                    return value.getByteCount()/KB;    // 以kb为单位
                }
            };
        }
    }

    private Bitmap getBitmapFromMemory(String key) {
        return mMemLruCache.get(key);
    }

    private void initDiskLruCache(Context context) {
        File diskCacheDir = getDiskCacheDir(context, DISK_CACHE_SUBDIR);
        new InitDiskCacheTask().execute(diskCacheDir);
    }

    private File getDiskCacheDir(Context context, String dirName) {
        boolean mounted = Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState());
        boolean inPhoneSdCard = Environment.isExternalStorageRemovable();
        String cacheDirPath = "";
        if (mounted || inPhoneSdCard) {
            cacheDirPath = context.getExternalCacheDir().getPath();
        } else {
            cacheDirPath = context.getCacheDir().getPath();
        }
        return new File(cacheDirPath + File.separator + dirName);
    }

    private class InitDiskCacheTask extends AsyncTask<File, Void, Void> {
        @Override
        protected Void doInBackground(File... params) {
            synchronized (mDiskCacheLock) {
                File cacheDir = params[0];
                try {
                    mDiskLruCache = DiskLruCache.open(cacheDir, 1, 1, mMaxDiskCacheSize);
                    mDiskCacheStarting = false;
                    mDiskCacheLock.notifyAll();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            return null;
        }
    }

    private Bitmap getBitmapFromDiskCache(String key) {
        DiskLruCache.Snapshot snap = getBitmapSnapshotFromDiskCache(key);
        return getBitmapFromSnapshot(snap);
    }

    private DiskLruCache.Snapshot getBitmapSnapshotFromDiskCache(String key) {
        DiskLruCache.Snapshot snapShort = null;
        synchronized (mDiskCacheLock) {
            while(mDiskCacheStarting) {
                try {
                    mDiskCacheLock.wait();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            if(mDiskLruCache != null) {
                try {
                    snapShort = mDiskLruCache.get(key);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return snapShort;
    }

    /////////////这个地方好像有问题,需要确认
    private Bitmap getBitmapFromSnapshot(DiskLruCache.Snapshot snap) {
        if (snap == null) {
            return null;
        }
        InputStream is = snap.getInputStream(0);
        return BitmapFactory.decodeStream(is);
    }

}

package com.example.imageloader.loader;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;

import com.example.imageloader.loader.processor.ImageProcessor;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
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
    private ThreadPoolManager mThreadPoolManager;
    private ArrayList<ImageProcessor> mImageProcessorList;

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
        initThreadPool();
    }

    public void addImageProcessor(ImageProcessor processor) {
        if (mImageProcessorList == null) {
            mImageProcessorList = new ArrayList<ImageProcessor>();
        }
        mImageProcessorList.add(processor);
    }

    private String getUrlKey(String url) {
        String urlKey = LoaderUtils.encodeMd5(url);
        return urlKey;
    }

    public void load(ImageView imageView, String url, int defaultRes) {
        String urlKey = LoaderUtils.encodeMd5(url);
        if (TextUtils.isEmpty(urlKey)) {
            return;
        }
        Bitmap bmp = getBitmapFromMemory(urlKey);
        if (bmp != null) {
            imageView.setImageBitmap(bmp);
        } else {
            imageView.setImageResource(defaultRes);
            imageView.setTag(url);
            loadBitmapAsync(imageView, url, urlKey);
        }
    }

    private void loadBitmapAsync(final ImageView imageView, final String url, final String urlKey) {
        mThreadPoolManager.addTask(new Runnable() {
            @Override
            public void run() {
                Bitmap bmp = getBitmapFromDiskCache(urlKey);
                if (bmp != null) {
                    addBitmapToMemCache(url, bmp);
                    PostAndSetBitmap(imageView, bmp, url);
                } else {
                    bmp = getBitmapFromHttp(url);
                    if (bmp != null) {
                        addBitmapToCache(url, bmp);
                        PostAndSetBitmap(imageView, bmp, url);
                    }
                }
            }
        });
    }

    private void addBitmapToMemCache(String url, Bitmap bmp) {
        mMemLruCache.put(getUrlKey(url), bmp);
    }

    private void addBitmapToDiskCache(String url, Bitmap bmp) {

    }

    private void addBitmapToCache(String url, Bitmap bmp) {
        addBitmapToMemCache(url, bmp);
        addBitmapToDiskCache(url, bmp);
    }

    private void PostAndSetBitmap(final ImageView imageView, final Bitmap bmp, final String url) {
        mMainHandler.post(new Runnable() {
            @Override
            public void run() {
                String taggedUrl = (String)imageView.getTag();
                if (TextUtils.equals(taggedUrl, url)) {
                    imageView.setImageBitmap(bmp);
                }
            }
        });
    }

    private Handler mMainHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {

        }
    };

    private Bitmap getBitmapFromHttp(String url) {
        BufferedInputStream bis = null;
        try {
            URL uri = new URL(url);
            HttpURLConnection connection = (HttpURLConnection) uri.openConnection();
            bis = new BufferedInputStream(connection.getInputStream());
            if (mImageProcessorList != null) {
                for(ImageProcessor processor : mImageProcessorList) {
                    processor.process(bis);
                }
            }
            return BitmapFactory.decodeStream(bis);
        } catch(Exception e) {
            e.printStackTrace();
        } finally {
            if (bis != null) {
                try {
                    bis.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                bis = null;
            }
        }
        return null;
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

    private void initThreadPool() {
        if (mThreadPoolManager == null) {
            mThreadPoolManager = new ThreadPoolManager();
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

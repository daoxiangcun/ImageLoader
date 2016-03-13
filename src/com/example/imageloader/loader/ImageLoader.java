package com.example.imageloader.loader;

import java.io.File;
import java.io.IOException;

import com.example.imageloader.loader.DiskLruCache.Snapshot;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.os.Environment;
import android.util.LruCache;

/**
 * Ҫʵ�ֵĹ���:
 * 1. ˫����:�ӱ����ڴ��м���ͼƬLRUCache,�ӱ��ش����м���ͼƬDiskLRUCache
 * 2. ʹ���̳߳���߼���Ч��
 * 3. ��ֹ����ͼƬ��λ����
 * 4. Ҫ���ǵ����imageview��Ӧͬһ��ͼƬurl������
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

    private ImageLoader() {
        init();
    }

    public static ImageLoader get() {
        if (sImageLoader == null) {
            sImageLoader = new ImageLoader();
        }
        return sImageLoader;
    }

    private void init() {
        initMaxCacheSize();
        initLruCache();
        initDiskLruCache();
    }

    public void initMaxCacheSize() {
        int maxMemory = (int)(Runtime.getRuntime().maxMemory()/KB);  // ��kbΪ��λ
        mMaxMemCacheSize = maxMemory/8;

        // �ⲿ�洢��ռ�ռ�
        mMaxDiskCacheSize = 20 * 1024 * 1024; // 20MB
    }

    private void initLruCache() {
        if (mMemLruCache == null) {
            mMemLruCache = new LruCache<String, Bitmap>(mMaxMemCacheSize) {
                protected int sizeOf(String key, Bitmap value) {
                    return value.getByteCount()/KB;    // ��kbΪ��λ
                }
            };
        }
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

    private DiskLruCache.Snapshot getBitmapFromDiskCache(String key) {
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
}

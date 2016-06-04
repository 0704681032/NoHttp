/*
 * Copyright 2015 Yan Zhenjie
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.yolanda.nohttp;

import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;

import com.yolanda.nohttp.able.Finishable;
import com.yolanda.nohttp.able.Startable;
import com.yolanda.nohttp.tools.IOUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLConnection;

/**
 * <p>
 * A basic implementation of Binary.
 * All the methods are called in Son thread.
 * </p>
 * Created in Oct 17, 2015 12:40:54 PM.
 *
 * @author Yan Zhenjie.
 */
public abstract class BasicBinary implements Binary, Startable, Finishable {

    private static Handler sPosterHandler;

    private boolean isStarted = false;

    private boolean isCancel = false;

    private boolean isFinish = false;

    private int what;

    private OnUploadListener mUploadListener;

    private String fileName;

    private String mimeType;

    public BasicBinary(String fileName, String mimeType) {
        this.fileName = fileName;
        this.mimeType = mimeType;
    }

    /**
     * To monitor file upload progress.
     *
     * @param what             in {@link OnUploadListener} will return to you.
     * @param mProgressHandler {@link OnUploadListener}.
     */
    public void setUploadListener(int what, OnUploadListener mProgressHandler) {
        this.what = what;
        this.mUploadListener = mProgressHandler;
    }

    @Override
    public final long getLength() {
        if (!isCanceled())
            return getBinaryLength();
        return 0;
    }

    public abstract long getBinaryLength();

    protected abstract InputStream getInputStream() throws IOException;

    @Override
    public void onWriteBinary(OutputStream outputStream) {
        if (!isCanceled()) {
            InputStream inputStream = null;
            try {
                inputStream = IOUtils.toBufferedInputStream(getInputStream());
                start();
                postStart();

                int oldProgress = 0;
                long totalLength = getLength();
                int len;

                byte[] buffer = new byte[4096];

                long hasUpCount = 0;

                while (!isCanceled() && (len = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, len);
                    if (totalLength != 0 && mUploadListener != null) {
                        hasUpCount += len;
                        int progress = (int) (hasUpCount * 100 / totalLength);
                        if ((0 == progress % 3 || 0 == progress % 5 || 0 == progress % 7) && oldProgress != progress) {
                            oldProgress = progress;
                            postProgress(oldProgress);
                        }
                    }
                }
            } catch (IOException e) {
                Logger.e(e);
                postError(e);
            } finally {
                IOUtils.closeQuietly(inputStream);
                postFinish();
            }
        }
        finish();
    }

    @Override
    public String getFileName() {
        return fileName;
    }

    @Override
    public String getMimeType() {
        String fileName = getFileName();
        if (TextUtils.isEmpty(mimeType) && !TextUtils.isEmpty(fileName))
            mimeType = URLConnection.guessContentTypeFromName(getFileName());
        if (TextUtils.isEmpty(mimeType))
            mimeType = NoHttp.APPLICATION_OCTET_STREAM;
        return mimeType;
    }

    /**
     * Inform the task start.
     */
    protected void postStart() {
        UploadPoster start = new UploadPoster(what, mUploadListener);
        start.start();
        getPosterHandler().post(start);
    }

    /**
     * The update task schedule.
     *
     * @param progress progress.
     */
    protected void postProgress(int progress) {
        UploadPoster progressPoster = new UploadPoster(what, mUploadListener);
        progressPoster.progress(progress);
        getPosterHandler().post(progressPoster);
    }

    /**
     * Inform the task cancel.
     */
    protected void postCancel() {
        UploadPoster cancelPoster = new UploadPoster(what, mUploadListener);
        cancelPoster.cancel();
        getPosterHandler().post(cancelPoster);
    }

    /**
     * Error notification tasks.
     *
     * @param e exception.
     */
    protected void postError(Exception e) {
        UploadPoster error = new UploadPoster(what, mUploadListener);
        error.error(e);
        getPosterHandler().post(error);
    }

    /**
     * Inform the task finish.
     */
    protected void postFinish() {
        UploadPoster finish = new UploadPoster(what, mUploadListener);
        finish.finish();
        getPosterHandler().post(finish);
    }

    @Override
    public void start() {
        isStarted = true;
    }

    @Override
    public boolean isStarted() {
        return isStarted;
    }

    /**
     * Cancel handle.
     *
     * @param cancel true or false.
     * @deprecated {@link #cancel()} instead.
     */
    @Deprecated
    @Override
    public void cancel(boolean cancel) {
        if (cancel)
            cancel();
    }

    @Override
    public void cancel() {
        if (!isCancel) {
            this.isCancel = true;
            postCancel();
        }
    }

    @Override
    public boolean isCanceled() {
        return isCancel;
    }

    @Override
    public void finish() {
        isFinish = true;
    }

    @Override
    public boolean isFinished() {
        return isFinish;
    }

    private class UploadPoster implements Runnable {

        private final int what;
        private final OnUploadListener mOnUploadListener;

        private int command;

        public static final int ON_START = 0;
        public static final int ON_CANCEL = 1;
        public static final int ON_PROGRESS = 2;
        public static final int ON_FINISH = 3;
        public static final int ON_ERROR = 4;

        private int progress;
        private Exception exception;

        public UploadPoster(int what, OnUploadListener onUploadListener) {
            this.what = what;
            this.mOnUploadListener = onUploadListener;
        }

        public void start() {
            this.command = ON_START;
        }

        public void cancel() {
            this.command = ON_CANCEL;
        }

        public void progress(int progress) {
            this.command = ON_PROGRESS;
            this.progress = progress;
        }

        public void finish() {
            this.command = ON_FINISH;
        }

        public void error(Exception exception) {
            this.command = ON_ERROR;
            this.exception = exception;
        }

        @Override
        public void run() {
            if (mOnUploadListener != null) {
                if (command == ON_START)
                    mOnUploadListener.onStart(what);
                else if (command == ON_FINISH)
                    mOnUploadListener.onFinish(what);
                else if (command == ON_PROGRESS)
                    mOnUploadListener.onProgress(what, progress);
                else if (command == ON_CANCEL)
                    mOnUploadListener.onCancel(what);
                else if (command == ON_ERROR)
                    mOnUploadListener.onError(what, exception);
            }
        }

    }

    private Handler getPosterHandler() {
        synchronized (BasicBinary.class) {
            if (sPosterHandler == null)
                sPosterHandler = new Handler(Looper.getMainLooper());
        }
        return sPosterHandler;
    }
}

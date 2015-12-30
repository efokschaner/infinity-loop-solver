package efokschaner.infinityloopsolver;

import android.app.Service;
import android.app.UiAutomation;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.os.Binder;
import android.os.IBinder;
import android.util.DisplayMetrics;
import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;
import java.nio.Buffer;
import java.text.SimpleDateFormat;
import java.util.Date;

public class SolverService extends Service {
    private static final String TAG = SolverService.class.getSimpleName();

    public static class MediaHandles {
        public DisplayMetrics metrics;
        public MediaProjection mediaProjection;

        public MediaHandles(DisplayMetrics metrics, MediaProjection mediaProjection) {
            this.metrics = metrics;
            this.mediaProjection = mediaProjection;
        }
    }

    public void SetMediaHandles(MediaHandles mh) {
        Log.d(TAG, "mMediaHandles set");
        mMediaHandles = mh;
        StartOrStopSolver();
    }

    public void SetInfinityLoopIsFocused(boolean b) {
        Log.d(TAG, String.format("mInfinityLoopIsFocused set to %s", b));
        mInfinityLoopIsFocused = b;
        StartOrStopSolver();
    }

    private void SetServiceEnabled(boolean b) {
        Log.d(TAG, String.format("mServiceEnabled set to %s", b));
        mServiceEnabled = b;
        StartOrStopSolver();
    }

    public class SolverServiceBinder extends Binder {
        SolverService getService() {
            return SolverService.this;
        }
    }

    private final SolverServiceBinder mBinder = new SolverServiceBinder();

    @Override
    public IBinder onBind(Intent intent) {
        SetServiceEnabled(true);
        RequestMediaHandles();
        return mBinder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        SetServiceEnabled(false);
        return false;
    }

    private Thread mSolverThread;
    private boolean mServiceEnabled = false;
    private boolean mInfinityLoopIsFocused = false;
    private MediaHandles mMediaHandles;

    private void StartOrStopSolver() {
        if(mServiceEnabled && mMediaHandles != null && mInfinityLoopIsFocused) {
            StartSolver();
        } else {
            ShutdownSolver();
        }
    }

    private void RequestMediaHandles() {
        Intent intent = new Intent(this, MediaProjectionRequest.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        startActivity(intent);
    }

    private void StartSolver() {
        if(mSolverThread == null) {
            mSolverThread = new Thread(mRunnableSolver);
            mSolverThread.start();
        }
    }

    private void ShutdownSolver() {
        Log.d(TAG, "Shutting down");
        if(mSolverThread != null) {
            Thread t = mSolverThread;
            mSolverThread = null;
            t.interrupt();
            try {
                t.join();
            } catch (InterruptedException e) {
                // ignore
            }
        }
    }

    public SolverService() {
    }

    @Override
    public void onCreate() {
        super.onCreate();

    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    private static void TryAcquireImage(ImageReader imageReader) {
        try (Image image = imageReader.acquireLatestImage()) {
            if(image != null) {
                try {
                    String timestamp = new SimpleDateFormat("HH_mm_ss").format(new Date());
                    URL url = new URL("http://10.0.2.2:8888/" + timestamp + ".png");
                    try {
                        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                        try {
                            conn.setDoOutput(true);
                            conn.setChunkedStreamingMode(0);
                            conn.setRequestMethod("POST");
                            conn.setRequestProperty("Content-Type", "application/octet-stream");
                            try (OutputStream ostream = conn.getOutputStream()) {
                                final Image.Plane[] planes = image.getPlanes();
                                final Buffer buffer = planes[0].getBuffer();
                                final int pixelStride = planes[0].getPixelStride();
                                final int rowStride = planes[0].getRowStride();
                                Bitmap bitmap = Bitmap.createBitmap(rowStride / pixelStride, image.getHeight(), Bitmap.Config.ARGB_8888);
                                try {
                                    bitmap.copyPixelsFromBuffer(buffer);
                                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, ostream);
                                } finally {
                                    bitmap.recycle();
                                }
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                            final int responseCode = conn.getResponseCode();
                            if (!(responseCode >= 200 && responseCode < 300)) {
                                throw new AssertionError(String.format("Http response was: %d", responseCode));
                            }
                            conn.getResponseMessage();
                            BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                            while (in.readLine() != null){
                                // ignore contents
                            }
                            in.close();
                        } catch (ProtocolException e) {
                            e.printStackTrace();
                        } finally {
                            conn.disconnect();
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                } catch (MalformedURLException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private Runnable mRunnableSolver = new Runnable() {
        @Override
        public void run() {
            try (ImageReader imageReader = ImageReader.newInstance(
                    mMediaHandles.metrics.widthPixels,
                    mMediaHandles.metrics.heightPixels,
                    PixelFormat.RGBA_8888,
                    2)) {
                VirtualDisplay virtualDisplay = mMediaHandles.mediaProjection.createVirtualDisplay(
                        "ScreenCapture",
                        mMediaHandles.metrics.widthPixels,
                        mMediaHandles.metrics.heightPixels,
                        mMediaHandles.metrics.densityDpi,
                        DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                        imageReader.getSurface(),
                        null,
                        null);
                try {
                    while(true) {
                        Log.d(TAG, "Running");
                        TryAcquireImage(imageReader);
                        Thread.sleep(5000);
                    }
                } finally {
                    virtualDisplay.release();
                }
            }
            catch (InterruptedException e) {
                // ignore
            }
        }
    };
}

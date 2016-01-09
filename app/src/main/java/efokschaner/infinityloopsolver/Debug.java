package efokschaner.infinityloopsolver;


import android.app.UiAutomation;
import android.graphics.Bitmap;

import org.opencv.android.Utils;
import org.opencv.core.Mat;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


public class Debug {
    private static ExecutorService mExecutor = Executors.newSingleThreadExecutor();

    private static String mLastTimeStamp = "";
    private static int mNonce = 0;
    private static String getNewTimestamp() {
        String newStamp = new SimpleDateFormat("HH_mm_ss_SSS").format(new Date());
        if(newStamp.equals(mLastTimeStamp)) {
            mNonce += 1;
        } else {
            mNonce = 0;
        }
        mLastTimeStamp = newStamp;
        newStamp += String.format("_%s", mNonce);
        return newStamp;
    }

    public static void sendBitmap(Bitmap bitmap) {
        final Bitmap copy = bitmap.copy(bitmap.getConfig(), false);
        mExecutor.submit(new Runnable() {
            @Override
            public void run() {
                try {
                    URL url = new URL("http://efoks1ml1:8888/" + getNewTimestamp() + ".png");
                    try {
                        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                        try {
                            conn.setDoOutput(true);
                            conn.setChunkedStreamingMode(0);
                            conn.setRequestMethod("POST");
                            conn.setRequestProperty("Content-Type", "application/octet-stream");
                            try (OutputStream ostream = conn.getOutputStream()) {
                                copy.compress(Bitmap.CompressFormat.PNG, 100, ostream);
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
                } finally {
                    copy.recycle();
                }
            }
        });
    }

    public static void sendMatrix(Mat m) {
        Bitmap b2 = Bitmap.createBitmap(m.width(), m.height(), Bitmap.Config.ARGB_8888);
        Utils.matToBitmap(m, b2);
        sendBitmap(b2);
        b2.recycle();
    }

    public static void sendScreenshot(UiAutomation uiAutomation) {
        Bitmap b = uiAutomation.takeScreenshot();
        try{
            sendBitmap(b);
        } finally {
            b.recycle();
        }
    }
}

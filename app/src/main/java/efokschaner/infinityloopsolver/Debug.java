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

public class Debug {
    public static void sendBitmap(Bitmap bitmap) {
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
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, ostream);
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

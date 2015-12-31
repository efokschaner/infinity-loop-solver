package efokschaner.infinityloopsolver;


import android.app.UiAutomation;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.SystemClock;
import android.util.Log;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;

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
import java.util.List;

public class Solver {
    private static final String TAG = Solver.class.getSimpleName();

    private final UiAutomation mUiAutomation;
    private Thread mSolverThread;

    public Solver(UiAutomation uiAutomation, Runnable launchInfinityLoop) {
        mUiAutomation = uiAutomation;
        mUiAutomation.setOnAccessibilityEventListener(new UiAutomation.OnAccessibilityEventListener() {
            @Override
            public void onAccessibilityEvent(AccessibilityEvent event) {
                if(isInfinityLoopReady(mUiAutomation, event)) {
                    if(mSolverThread == null) {
                        mSolverThread = new Thread(mRunnableSolver);
                        mSolverThread.start();
                    }
                } else {
                    if(mSolverThread != null) {
                        shutdown();
                    }
                }
            }
        });
        launchInfinityLoop.run();
    }

    public void shutdown() {
        Log.d(TAG, "Shutting down");
        if(mSolverThread != null) {
            mSolverThread.interrupt();
            try {
                mSolverThread.join();
            } catch (InterruptedException e) {
                // ignore
            } finally {
                mSolverThread = null;
            }
        }
    }

    private static final Runnable NOOP = new Runnable() { public void run() {} };

    private static void sendBitmap(Bitmap bitmap) {
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

    private static void sendScreenshot(UiAutomation uiAutomation) {
        Bitmap b = uiAutomation.takeScreenshot();
        try{
            sendBitmap(b);
        } finally {
            b.recycle();
        }
    }

    private static AccessibilityNodeInfo findInfinityLoopView(AccessibilityNodeInfo node) {
        final String viewIdResourceName = node.getViewIdResourceName();
        if(viewIdResourceName != null && viewIdResourceName.equals("com.balysv.loop:id/game_scene_view_light")) {
            return node;
        }
        final int numChildren = node.getChildCount();
        for(int i = 0; i < numChildren; ++i) {
            AccessibilityNodeInfo childNode;
            if((childNode = findInfinityLoopView(node.getChild(i))) != null) {
                return childNode;
            }
        }
        return null;
    }

    private static boolean isInfinityLoopReady(List<AccessibilityWindowInfo> windows) {
        // Determine if InfinityLoop (and only InfinityLoop) is on screen
        Log.d(TAG, String.format("windows.size() = %s", windows.size()));
        return (windows.size() == 1 &&
                (findInfinityLoopView(windows.get(0).getRoot())) != null);
    }

    private static boolean isInfinityLoopReady(UiAutomation uiAutomation, AccessibilityEvent event) {
        if(event.getEventType() == AccessibilityEvent.TYPE_WINDOWS_CHANGED) {
            final List<AccessibilityWindowInfo> windows = uiAutomation.getWindows();
            return isInfinityLoopReady(windows);
        } else {
            return false;
        }
    }

    /**
     * Helper method injects a click event at a point on the active screen via the UiAutomation object.
     * @param x the x position on the screen to inject the click event
     * @param y the y position on the screen to inject the click event
     * @param automation a UiAutomation object retreived through the current Instrumentation
     */
    private static void injectClickEvent(float x, float y, UiAutomation automation){
        //A MotionEvent is a type of InputEvent.
        //The event time must be the current uptime.
        final long eventTime = SystemClock.uptimeMillis();
        //A typical click event triggered by a user click on the touchscreen creates two MotionEvents,
        //first one with the action KeyEvent.ACTION_DOWN and the 2nd with the action KeyEvent.ACTION_UP
        MotionEvent motionDown = MotionEvent.obtain(
                eventTime,
                eventTime,
                MotionEvent.ACTION_DOWN,
                x,
                y,
                0);
        //We must set the source of the MotionEvent or the click doesn't work.
        motionDown.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        automation.injectInputEvent(motionDown, true);
        MotionEvent motionUp = MotionEvent.obtain(
                eventTime,
                eventTime,
                KeyEvent.ACTION_UP,
                x,
                y,
                0);
        motionUp.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        automation.injectInputEvent(motionUp, true);
        //Recycle our events back to the system pool.
        motionUp.recycle();
        motionDown.recycle();
    }

    private Runnable mRunnableSolver = new Runnable() {
        @Override
        public void run() {
            Log.d(TAG, "run()");
            try {
                sendScreenshot(mUiAutomation);
                for(int i = 0; i < 10; ++i)
                {
                    Rect windowBounds = new Rect();
                    mUiAutomation.getWindows().get(0).getBoundsInScreen(windowBounds);
                    final float xpos = windowBounds.exactCenterX() + 20;
                    final float ypos = windowBounds.exactCenterY() + 20;
                    injectClickEvent(xpos, ypos, mUiAutomation);
                    Thread.sleep(1000);
                    sendScreenshot(mUiAutomation);
                }
            } catch (InterruptedException e) {
                // ignore
            }
        }
    };



}

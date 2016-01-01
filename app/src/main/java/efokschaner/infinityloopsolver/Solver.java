package efokschaner.infinityloopsolver;


import android.app.UiAutomation;
import android.graphics.Bitmap;
import android.os.SystemClock;
import android.util.Log;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;

import java.util.List;

public class Solver {
    private static final String TAG = Solver.class.getSimpleName();

    private final UiAutomation mUiAutomation;
    private Thread mSolverThread;

    public Solver(UiAutomation uiAutomation) {
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
                    stopSolver();
                }
            }
        });
    }

    private void stopSolver() {
        if(mSolverThread != null) {
            Log.d(TAG, "Shutting down");
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

    public void shutdown() {
        mUiAutomation.setOnAccessibilityEventListener(null);
        stopSolver();
    }

    private static final Runnable NOOP = new Runnable() { public void run() {} };

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
                while(!Thread.interrupted()) {
                    Bitmap b = mUiAutomation.takeScreenshot();
                    try{
                        final GameState gameStateFromImage = ImageProcessor.getGameStateFromImage(b);
                        // Perform moves...
                        // using injectClickEvent(xpos, ypos, mUiAutomation);
                        // and some sleeps / waits between moves
                        Thread.sleep(1000);
                    } finally {
                        b.recycle();
                    }
                }
            } catch (InterruptedException e) {
                // ignore
            }
        }
    };

}

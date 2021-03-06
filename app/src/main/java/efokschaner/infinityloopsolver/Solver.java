package efokschaner.infinityloopsolver;


import android.app.UiAutomation;
import android.databinding.Observable;
import android.databinding.ObservableBoolean;
import android.databinding.ObservableField;
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
import java.util.concurrent.TimeoutException;

public class Solver {
    private static final String TAG = Solver.class.getSimpleName();

    private final UiAutomation mUiAutomation;
    private final ImageProcessor mImageProcessor;
    private Thread mSolverThread;
    private boolean mNextRunIsOnce;

    public final ObservableBoolean isEnabled = new ObservableBoolean(false);
    public final ObservableField<String> lastError = new ObservableField<>();

    private void startOrStopSolverThread() {
        if(isEnabled.get() && isInfinityLoopReady(mUiAutomation.getWindows())) {
            if(mSolverThread == null) {
                mSolverThread = new Thread(getSolverFunc(mNextRunIsOnce));
                mNextRunIsOnce = false;
                mSolverThread.start();
            }
        } else {
            stopSolver();
        }
    }

    public void runOnce() {
        mNextRunIsOnce = true;
        isEnabled.set(true);
    }

    public Solver(UiAutomation uiAutomation, ImageProcessor imageProcessor) {
        isEnabled.addOnPropertyChangedCallback(new Observable.OnPropertyChangedCallback() {
            @Override
            public void onPropertyChanged(Observable sender, int propertyId) {
                startOrStopSolverThread();
            }
        });
        mImageProcessor = imageProcessor;
        mUiAutomation = uiAutomation;
        mUiAutomation.setOnAccessibilityEventListener(new UiAutomation.OnAccessibilityEventListener() {
            @Override
            public void onAccessibilityEvent(AccessibilityEvent event) {
                if(event.getEventType() == AccessibilityEvent.TYPE_WINDOWS_CHANGED) {
                    startOrStopSolverThread();
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

    private Runnable getSolverFunc(final boolean runOnce) {
        return new Runnable() {
            @Override
            public void run() {
                try {
                    mUiAutomation.waitForIdle(1000, 10000);
                    while(!Thread.interrupted()) {
                        // click to complete the level
                        Log.d(TAG, "Completing level");
                        injectClickEvent(20, 20, mUiAutomation);
                        Thread.sleep(3200);
                        Log.d(TAG, "Interpreting");
                        Bitmap b = mUiAutomation.takeScreenshot();
                        try{
                            final GameState gameStateFromImage = mImageProcessor.getGameStateFromImage(b);
                            if(gameStateFromImage != null) {
                                Log.d(TAG, "Solving");
                                final List<ClickAction> actions = gameStateFromImage.getSolution();
                                if(!Thread.interrupted()) {
                                    Log.d(TAG, "Acting");
                                    for(ClickAction action : actions) {
                                        injectClickEvent(action.xpos, action.ypos, mUiAutomation);
                                    }
                                    Thread.sleep(1500);
                                }
                            }
                        } finally {
                            b.recycle();
                        }
                        if(runOnce) {
                            break;
                        }
                    }
                } catch (TimeoutException | InterruptedException | GameState.UnsolvableError e) {
                    e.printStackTrace();
                    lastError.set(Log.getStackTraceString(e));
                } finally {
                    isEnabled.set(false);
                }
            }
        };
    }
}

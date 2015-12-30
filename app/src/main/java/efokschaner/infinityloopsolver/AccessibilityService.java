package efokschaner.infinityloopsolver;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;

import java.util.List;


public class AccessibilityService extends android.accessibilityservice.AccessibilityService {
    private static final String TAG = AccessibilityService.class.getSimpleName();

    private AccessibilityNodeInfo mGameView;
    private SolverService mSolverService;
    private ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            mSolverService = ((SolverService.SolverServiceBinder)service).getService();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mSolverService = null;
        }
    };

    @Override
    protected void onServiceConnected() {
        Log.d(TAG, "onServiceConnected");
        super.onServiceConnected();
        bindService(new Intent(this, SolverService.class), mConnection, Context.BIND_AUTO_CREATE);
    }

    private AccessibilityNodeInfo findInfinityLoopView(AccessibilityNodeInfo node) {
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

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if(mSolverService == null) {
            // Ignore all events until we're connected to the solver service
            return;
        }
        if(event.getEventType() == AccessibilityEvent.TYPE_WINDOWS_CHANGED) {
            final List<AccessibilityWindowInfo> windows = getWindows();
            // Determine if InfinityLoop (and only InfinityLoop) is on screen
            if(windows.size() == 1 && (mGameView = findInfinityLoopView(windows.get(0).getRoot())) != null) {
                mSolverService.SetInfinityLoopIsFocused(true);
            } else {
                mSolverService.SetInfinityLoopIsFocused(false);
            }
        }
    }

    @Override
    public void onInterrupt() {
    }

    @Override
    public boolean onUnbind(Intent i) {
        Log.d(TAG, "onUnbind");
        unbindService(mConnection);
        return false;
    }
}

package efokschaner.infinityloopsolver;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Bundle;
import android.os.IBinder;
import android.util.DisplayMetrics;
import android.util.Log;

public class MediaProjectionRequest extends Activity {
    private static final String TAG = MediaProjectionRequest.class.getSimpleName();

    private MediaProjectionManager mMediaProjectionManager;
    private SolverService mSolverService;

    private ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            mSolverService = ((SolverService.SolverServiceBinder)service).getService();
            startActivityForResult(mMediaProjectionManager.createScreenCaptureIntent(), 0);
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mSolverService = null;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mMediaProjectionManager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        bindService(new Intent(this, SolverService.class), mConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        MediaProjection mp = mMediaProjectionManager.getMediaProjection(resultCode, data);
        if(mp != null) {
            Log.d(TAG, "Accepted");
            DisplayMetrics metrics = new DisplayMetrics();
            getWindowManager().getDefaultDisplay().getMetrics(metrics);
            mSolverService.SetMediaHandles(new SolverService.MediaHandles(metrics, mp));
        } else {
            Log.d(TAG, "Declined");
        }

        super.onActivityResult(requestCode, resultCode, data);
        finish();
    }

    @Override
    protected void onDestroy() {
        unbindService(mConnection);
        super.onDestroy();
    }
}

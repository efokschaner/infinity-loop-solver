package efokschaner.infinityloopsolver;

import android.databinding.DataBindingUtil;
import android.databinding.ObservableField;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Switch;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;

import efokschaner.infinityloopsolver.databinding.ActivityMainBinding;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = MainActivity.class.getSimpleName();

    public ObservableField<Solver> solver;

    public View.OnClickListener onEnable = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            solver.get().isEnabled.set(((Switch)v).isChecked());
        }
    };

    public View.OnClickListener onRunOnce = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            solver.get().runOnce();
        }
    };

    private BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch(status) {
                case LoaderCallbackInterface.SUCCESS:
                    Log.i(TAG, "OpenCV Manager Connected");
                    ((SolverApplication) getApplication()).createSolver();
                    break;
                case LoaderCallbackInterface.INIT_FAILED:
                    Log.i(TAG,"Init Failed");
                    break;
                case LoaderCallbackInterface.INSTALL_CANCELED:
                    Log.i(TAG,"Install Cancelled");
                    break;
                case LoaderCallbackInterface.INCOMPATIBLE_MANAGER_VERSION:
                    Log.i(TAG,"Incompatible Version");
                    break;
                case LoaderCallbackInterface.MARKET_ERROR:
                    Log.i(TAG,"Market Error");
                    break;
                default:
                    Log.i(TAG,"OpenCV Manager Install");
                    super.onManagerConnected(status);
                    break;
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final SolverApplication app = (SolverApplication) getApplication();
        solver = app.solver;
        //initialize OpenCV manager
        OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_1_0, this, mLoaderCallback);
        ActivityMainBinding m =  DataBindingUtil.setContentView(this, R.layout.activity_main);
        m.setModel(this);
    }

    @Override
    protected void onDestroy() {
        final SolverApplication app = (SolverApplication) getApplication();
        final Solver solver = app.solver.get();
        if(solver != null) {
            solver.isEnabled.set(false);
        }
        super.onDestroy();
    }
}

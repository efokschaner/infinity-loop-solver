package efokschaner.infinityloopsolver;

import android.app.Application;
import android.app.UiAutomation;
import android.content.Intent;
import android.content.pm.PackageManager;

public class SolverApplication extends Application {
    private UiAutomation mUiAutomation;

    public void setUiAutomation(UiAutomation mUiAutomation) {
        this.mUiAutomation = mUiAutomation;
    }

    private Solver mSolver;

    public void enableSolver() {
        if(mSolver == null) {
            mSolver = new Solver(mUiAutomation);
            final PackageManager packageManager = SolverApplication.this.getPackageManager();
            final Intent launchIntent = packageManager.getLaunchIntentForPackage("com.balysv.loop");
            if(launchIntent != null) {
                SolverApplication.this.startActivity(launchIntent);
            }
        }
    }

    public void disableSolver() {
        if(mSolver != null) {
            mSolver.shutdown();
            mSolver = null;
        }
    }

}

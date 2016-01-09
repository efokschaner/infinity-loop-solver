package efokschaner.infinityloopsolver;

import android.app.Application;
import android.app.UiAutomation;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.databinding.Observable;
import android.databinding.ObservableField;

public class SolverApplication extends Application {
    public final ObservableField<Solver> solver = new ObservableField<>();

    private ImageProcessor mProcessor;
    private UiAutomation mUiAutomation;

    private ImageProcessor getImageProcessor() {
        if (mProcessor == null) {
            mProcessor = new ImageProcessor(getAssets());
        }
        return mProcessor;
    }

    public void createSolver() {
        solver.set(new Solver(mUiAutomation, getImageProcessor()));
        solver.get().isEnabled.addOnPropertyChangedCallback(new Observable.OnPropertyChangedCallback() {
            @Override
            public void onPropertyChanged(Observable sender, int propertyId) {
                if (solver.get().isEnabled.get()) {
                    final PackageManager packageManager = SolverApplication.this.getPackageManager();
                    final Intent launchIntent = packageManager.getLaunchIntentForPackage("com.balysv.loop");
                    if (launchIntent != null) {
                        SolverApplication.this.startActivity(launchIntent);
                    }
                }
            }
        });
    }

    public void setUiAutomation(UiAutomation uiAutomation) {
        mUiAutomation = uiAutomation;
    }
}

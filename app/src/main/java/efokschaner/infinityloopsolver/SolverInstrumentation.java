package efokschaner.infinityloopsolver;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.Application;
import android.app.Instrumentation;
import android.app.UiAutomation;
import android.content.Intent;
import android.os.Bundle;

public class SolverInstrumentation extends Instrumentation {
    private static final String TAG = SolverInstrumentation.class.getSimpleName();

    @Override
    public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
    }

    @Override
    public void callApplicationOnCreate(final Application app) {
        super.callApplicationOnCreate(app);
        // This can't be done on the main thread it seems...
        new Thread(new Runnable() {
            @Override
            public void run() {
                final UiAutomation uiAutomation = getUiAutomation();
                final AccessibilityServiceInfo serviceInfo = uiAutomation.getServiceInfo();
                serviceInfo.flags |= AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
                uiAutomation.setServiceInfo(serviceInfo);
                ((SolverApplication) app).setUiAutomation(getUiAutomation());
            }
        }).start();

        Intent i = app.getPackageManager().getLaunchIntentForPackage(app.getPackageName());
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        app.startActivity(i);
    }
}

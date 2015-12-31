package efokschaner.infinityloopsolver;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.widget.CompoundButton;
import android.widget.Switch;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final SolverApplication app = (SolverApplication) getApplication();
        setContentView(R.layout.activity_main);
        final Switch enabledSwitch = (Switch) findViewById(R.id.switch_solver_enabled);
        enabledSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if(isChecked) {
                    app.enableSolver();
                } else {
                    app.disableSolver();
                }
            }
        });
    }
}

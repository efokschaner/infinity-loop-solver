package efokschaner.infinityloopsolver;

import android.content.Intent;
import android.provider.Settings;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        final Button button = (Button) findViewById(R.id.button);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent settingsActivityIntent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
                settingsActivityIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(settingsActivityIntent);
            }
        });
    }
}

package efokschaner.infinityloopsolver;

import android.graphics.Bitmap;

import org.opencv.android.Utils;
import org.opencv.core.Mat;


public class ImageProcessor {
    public static GameState getGameStateFromImage(Bitmap b) {
        Mat m = new Mat();
        Utils.bitmapToMat(b, m);
        Debug.sendMatrix(m);
        return new GameState(new GameState.TileState[2][2]);
    }
}

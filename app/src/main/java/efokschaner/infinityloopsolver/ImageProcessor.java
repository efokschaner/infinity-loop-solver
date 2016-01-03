package efokschaner.infinityloopsolver;

import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ImageProcessor {
    private static final String TAG = ImageProcessor.class.getSimpleName();

    private final Map<GameState.TileType, PrecomputedTileImageData> mTileImages = new HashMap<>();

    public static Mat rotateImage(Mat img, double angleDegrees) {
        Point center = new Point(img.cols() / 2, img.rows() / 2);
        Mat rotMat = Imgproc.getRotationMatrix2D(center, -angleDegrees, 1.0);
        Mat dest = new Mat();
        Imgproc.warpAffine(img, dest, rotMat, img.size());
        return dest;
    }

    public static Mat bitmapToBinaryMat(Bitmap b) {
        Mat colorMat = new Mat();
        Utils.bitmapToMat(b, colorMat);
        Mat greyMat = new Mat();
        Imgproc.cvtColor(colorMat, greyMat, Imgproc.COLOR_BGR2GRAY);
        Mat bwMat = new Mat();
        Imgproc.threshold(greyMat, bwMat, 0, 255, Imgproc.THRESH_OTSU | Imgproc.THRESH_BINARY_INV);
        return bwMat;
    }

    private class PrecomputedTileImageData {
        public final Map<Double, Map<GameState.TileOrientation, Mat>> precomputedImages = new HashMap<>();

        public PrecomputedTileImageData(GameState.TileType tt, Bitmap baseImage) {
            Mat binaryMat = bitmapToBinaryMat(baseImage);
            // just using 0.5 for now, try some lower values too if the map scales with tiles
            for(double scale : new double[]{0.5}) {
                Map<GameState.TileOrientation, Mat> orientationMap = new HashMap<>();
                Mat resizedMat = new Mat();
                Imgproc.resize(binaryMat, resizedMat, new Size(), scale, scale, Imgproc.INTER_AREA);
                for(GameState.TileOrientation o : tt.getPossibleOrientations()) {
                    Mat rotatedScaledImage = rotateImage(resizedMat, o.getAngle());
                    orientationMap.put(o, rotatedScaledImage);
                }
                precomputedImages.put(scale, orientationMap);
            }
        }
    }

    public ImageProcessor(AssetManager assMan) {
        for(GameState.TileType t: GameState.TileType.values()) {
            if(t.getImageFileName() != null) {
                try(final InputStream ifstream = assMan.open(t.getImageFileName())) {
                    Bitmap bitmap = BitmapFactory.decodeStream(ifstream);
                    try {
                        mTileImages.put(t, new PrecomputedTileImageData(t, bitmap));
                    } finally {
                        bitmap.recycle();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    // Represents a likely match of a Tile to a location
    private static class GuessRecord {
        GameState.TileType type;
        GameState.TileOrientation orientation;
        Mat matchedImage;
        double xpos;
        double ypos;
        double certainty;

        public GuessRecord(
                GameState.TileType type,
                GameState.TileOrientation orientation,
                Mat matchedImage,
                double xpos,
                double ypos,
                double certainty) {
            this.type = type;
            this.orientation = orientation;
            this.matchedImage = matchedImage;
            this.xpos = xpos;
            this.ypos = ypos;
            this.certainty = certainty;
        }
    }

    public GameState getGameStateFromImage(Bitmap b) {
        ArrayList<GuessRecord> guesses = new ArrayList<>();
        Mat gameImage = bitmapToBinaryMat(b);
        for(Map.Entry<GameState.TileType, PrecomputedTileImageData> tileTypeEntry : mTileImages.entrySet()) {
            for(Map.Entry<Double, Map<GameState.TileOrientation, Mat>> tileScaleEntry : tileTypeEntry.getValue().precomputedImages.entrySet()) {
                for(Map.Entry<GameState.TileOrientation, Mat> tileOrientationEntry : tileScaleEntry.getValue().entrySet()) {
                    Mat match = new Mat();
                    Mat tileImageToMatch = tileOrientationEntry.getValue();
                    Imgproc.matchTemplate(gameImage, tileImageToMatch, match, Imgproc.TM_SQDIFF_NORMED);
                    Mat matchThreshed = new Mat();
                    double certainty = 0.99;
                    Imgproc.threshold(match, matchThreshed, 1-certainty, 255, Imgproc.THRESH_BINARY_INV);
                    Mat eightBitMatchThreshed = new Mat();
                    matchThreshed.convertTo(eightBitMatchThreshed, CvType.CV_8U);
                    List<MatOfPoint> contours = new ArrayList<>();
                    Mat hierarchy = new Mat();
                    Imgproc.findContours(
                            eightBitMatchThreshed,
                            contours,
                            hierarchy,
                            Imgproc.RETR_LIST,
                            Imgproc.CHAIN_APPROX_NONE);
                    for(MatOfPoint contour : contours) {
                        final Rect rect = Imgproc.boundingRect(contour);

                        // Straight line pieces suffer from the fact that when we see multiple
                        // ones lined up, our classifier sees a long stretch of perfect matches.
                        // To compensate we test to see if the bounding Rect is
                        // comparable to the size of the tile or larger and if so we insert multiple
                        // guessrecords for multiple pieces.
                        // When this happens, the length of the matched area should be almost exactly
                        // (n - 1) * (length of piece) where n is the number of tiles that are matched
                        // in one stretch

                        double centerOfRectX = rect.x + (double)rect.width / 2;
                        double centerOfRectY = rect.y + (double)rect.height / 2;

                        if(rect.width > 0.5 * tileImageToMatch.width()) {
                            double exactRatioOfWidths = (double)rect.width / (double)tileImageToMatch.width();
                            // We assert that the
                            if(BuildConfig.DEBUG &&
                                    Math.abs(exactRatioOfWidths - Math.round(exactRatioOfWidths)) > 0.05) {
                                throw new AssertionError();
                            }
                            long numTilesMatched = Math.round(exactRatioOfWidths) + 1;
                            for(long i = 0; i < numTilesMatched; ++i) {
                                double xpos = centerOfRectX + i * tileImageToMatch.width() - (numTilesMatched - 1) * (double)tileImageToMatch.width() / 2;
                                guesses.add(new GuessRecord(
                                        tileTypeEntry.getKey(),
                                        tileOrientationEntry.getKey(),
                                        tileImageToMatch,
                                        xpos,
                                        centerOfRectY,
                                        certainty));
                            }
                        } else if(rect.height > 0.5 * tileImageToMatch.height()) {
                            double exactRatioOfHeights = (double)rect.height / (double)tileImageToMatch.height();
                            if(BuildConfig.DEBUG &&
                                    Math.abs(exactRatioOfHeights - Math.round(exactRatioOfHeights)) > 0.05) {
                                throw new AssertionError();
                            }

                            long numTilesMatched = Math.round(exactRatioOfHeights) + 1;
                            for(long i = 0; i < numTilesMatched; ++i) {
                                double ypos = centerOfRectY + i * tileImageToMatch.height() - (numTilesMatched - 1) * (double)tileImageToMatch.height() / 2;
                                guesses.add(new GuessRecord(
                                        tileTypeEntry.getKey(),
                                        tileOrientationEntry.getKey(),
                                        tileImageToMatch,
                                        centerOfRectX,
                                        ypos,
                                        certainty));
                            }
                        } else {
                            guesses.add(new GuessRecord(
                                    tileTypeEntry.getKey(),
                                    tileOrientationEntry.getKey(),
                                    tileImageToMatch,
                                    centerOfRectX,
                                    centerOfRectY,
                                    certainty));
                        }
                    }
                }
            }
        }
        Mat guessesVisualized = new Mat(gameImage.size(), CvType.CV_8UC1);
        for(GuessRecord guess: guesses) {
            Rect roiRect = new Rect(
                    (int)Math.round(guess.xpos),
                    (int)Math.round(guess.ypos),
                    guess.matchedImage.width(),
                    guess.matchedImage.height());
            Mat roi = new Mat(guessesVisualized, roiRect);
            Core.add(roi, guess.matchedImage, roi);
        }
        Log.d(TAG, String.format("Num guesses = %s", guesses.size()));
        Debug.sendMatrix(guessesVisualized);

        return new GameState(new GameState.TileState[2][2]);
    }
}

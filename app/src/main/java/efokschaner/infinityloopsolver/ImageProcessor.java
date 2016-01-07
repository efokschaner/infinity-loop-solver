package efokschaner.infinityloopsolver;

import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.support.annotation.NonNull;
import android.util.Log;

import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ImageProcessor {
    private static final String TAG = ImageProcessor.class.getSimpleName();

    private static final boolean DEBUG = false;

    private final Map<TileType, PrecomputedTileImageData> mTileImages = new HashMap<>();

    private static double globalScaleFactor = 0.5;
    private final ArrayList<Double> tileImageScalesRange = new ArrayList<>();

    public static Mat rotateImage(Mat img, double angleDegrees) {
        Point center = new Point(img.cols() / 2, img.rows() / 2);
        Mat rotMat = Imgproc.getRotationMatrix2D(center, -angleDegrees, 1.0);
        Mat dest = new Mat();
        Imgproc.warpAffine(img, dest, rotMat, img.size());
        return dest;
    }

    public static Mat bitmapToBinaryMat(Bitmap b, double scaleFactor) {
        Mat colorMat = new Mat();
        Utils.bitmapToMat(b, colorMat);
        Mat greyMat = new Mat();
        Imgproc.cvtColor(colorMat, greyMat, Imgproc.COLOR_BGR2GRAY);
        Mat resizedMat = new Mat();
        Imgproc.resize(greyMat, resizedMat, new Size(), scaleFactor, scaleFactor, Imgproc.INTER_AREA);
        Mat bwMat = new Mat();
        Imgproc.threshold(resizedMat, bwMat, 0, 255, Imgproc.THRESH_OTSU | Imgproc.THRESH_BINARY_INV);
        return bwMat;
    }

    private class PrecomputedTileImageData {
        public final Map<Double, Map<TileOrientation, Mat>> precomputedImages = new HashMap<>();
        public PrecomputedTileImageData(TileType tt, Bitmap baseImage) {
            // extra 0.5 factor because sample images were double size from screenshot
            Mat binaryMat = bitmapToBinaryMat(baseImage, globalScaleFactor * 0.5);
            for(double scale : tileImageScalesRange) {
                Map<TileOrientation, Mat> orientationMap = new HashMap<>();
                Mat resizedMat = new Mat();
                Imgproc.resize(binaryMat, resizedMat, new Size(), scale, scale, Imgproc.INTER_AREA);
                for(TileOrientation o : tt.getPossibleOrientations()) {
                    Mat rotatedScaledImage = rotateImage(resizedMat, o.getAngle());
                    orientationMap.put(o, rotatedScaledImage);
                }
                precomputedImages.put(scale, orientationMap);
            }
        }
    }

    public ImageProcessor(AssetManager assMan) {
        for (double s = 1.0; s > 0.79; s -= 0.02) {
            tileImageScalesRange.add(s);
        }
        for(TileType t: TileType.values()) {
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
        TileType type;
        TileOrientation orientation;
        Mat matchedImage;
        double xpos;
        double ypos;
        double certainty;

        public GuessRecord(
                TileType type,
                TileOrientation orientation,
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
        Mat lowResBinaryGameImage = bitmapToBinaryMat(b, globalScaleFactor);
        // zero out the options menu from the bottom of the game otherwise it gets in the way
        // of image processing
        int heightOfMenuButtonSection = (int) Math.round(0.075 * lowResBinaryGameImage.height());
        Rect menuButtonRoiRect = new Rect(
                0,
                lowResBinaryGameImage.height() - heightOfMenuButtonSection - 1,
                lowResBinaryGameImage.width(),
                heightOfMenuButtonSection);
        Mat menuButtonRoi = new Mat(lowResBinaryGameImage, menuButtonRoiRect);
        menuButtonRoi.setTo(new Scalar(0));

        // Crop the game image down to eliminate some of the search space
        final Rect tilesBoundingRect = getTilesBoundingRect(lowResBinaryGameImage);
        // Widen the bounding rect slightly to avoid making recognition near the edges suffer
        int largestTileWidth = 144;
        // buffer selected to add around half a tile of padding around the image.
        int buffer = (int) (0.5 * globalScaleFactor * largestTileWidth);
        final Rect gameImageRoiRect = new Rect(
                Math.max(tilesBoundingRect.x - buffer, 0),
                Math.max(tilesBoundingRect.y - buffer, 0),
                Math.min(tilesBoundingRect.width + 2*buffer, lowResBinaryGameImage.width() - tilesBoundingRect.x + buffer),
                Math.min(tilesBoundingRect.height + 2*buffer, lowResBinaryGameImage.height() - tilesBoundingRect.y + buffer));

        final Rect tilesBoundingRectRelativeToGameRoiRect = new Rect(
                tilesBoundingRect.x - gameImageRoiRect.x,
                tilesBoundingRect.y - gameImageRoiRect.y,
                tilesBoundingRect.width,
                tilesBoundingRect.height);

        Mat gameImageRoi = new Mat(lowResBinaryGameImage, gameImageRoiRect);
        if(DEBUG) {
            Debug.sendMatrix(gameImageRoi);
        }
        double derivedScale = getTileScale(gameImageRoi);
        Log.d(TAG, String.format("Tile scale: %s", derivedScale));
        for(Map.Entry<TileType, PrecomputedTileImageData> tileTypeEntry : mTileImages.entrySet()) {
            Map<TileOrientation, Mat> orientationImageMap = tileTypeEntry.getValue().precomputedImages.get(derivedScale);
            for(Map.Entry<TileOrientation, Mat> tileOrientationEntry : orientationImageMap.entrySet()) {
                Mat tileImageToMatch = tileOrientationEntry.getValue();
                Mat match = new Mat();
                Imgproc.matchTemplate(gameImageRoi, tileImageToMatch, match, Imgproc.TM_SQDIFF_NORMED);
                Mat matchThreshed = new Mat();
                Imgproc.threshold(match, matchThreshed, 0.3, 255, Imgproc.THRESH_BINARY_INV);
                Mat eightBitMatchThreshed = new Mat();
                matchThreshed.convertTo(eightBitMatchThreshed, CvType.CV_8U);
                if(DEBUG) {
                    if(tileTypeEntry.getKey().equals(TileType.LINE)) {
                        Debug.sendMatrix(eightBitMatchThreshed);
                    }
                }
                List<MatOfPoint> contours = new ArrayList<>();
                Mat hierarchy = new Mat();
                Imgproc.findContours(
                        eightBitMatchThreshed.clone(),
                        contours,
                        hierarchy,
                        Imgproc.RETR_LIST,
                        Imgproc.CHAIN_APPROX_NONE);
                for(MatOfPoint contour : contours) {
                    final Rect rect = Imgproc.boundingRect(contour);

                    double centerOfRectX = rect.x + (double)rect.width / 2;
                    double centerOfRectY = rect.y + (double)rect.height / 2;

                    // get the min value within the contour for the purpose of getting
                    // the quality of the match
                    Mat roi = new Mat(match, rect);
                    final Core.MinMaxLocResult minMaxLocResult = Core.minMaxLoc(roi);
                    double certainty = 1 - minMaxLocResult.minVal;

                    // Straight line pieces suffer from the fact that when we see multiple
                    // ones lined up, our classifier sees a long stretch of perfect matches.
                    // To compensate we test to see if the bounding Rect is
                    // comparable to the size of the tile or larger and if so we insert multiple
                    // guessrecords for multiple pieces.
                    // When this happens, the length of the matched area should be almost exactly
                    // (n - 1) * (length of piece) where n is the number of tiles that are matched
                    // in one stretch
                    if(rect.width > tileImageToMatch.width()) {
                        double exactRatioOfWidths = (double)rect.width / (double)tileImageToMatch.width();
                        int numTilesMatched = (int)Math.ceil(exactRatioOfWidths);
                        for(int i = 0; i < numTilesMatched; ++i) {
                            // for these long matches we decrease the certainty of the match near the edges
                            // because the long matches tend to bleed into the adjacent tiles...
                            double localCertainty = certainty;
                            if(i == 0 || i == numTilesMatched - 1) {
                                localCertainty *= 0.8;
                            }
                            double xpos = centerOfRectX + i * tileImageToMatch.width() - (numTilesMatched - 1) * (double)tileImageToMatch.width() / 2;
                            // This approach sometimes bleeds outside the tile space pretty badly
                            // so clamp the x val to within the tiles bounding rect
                            xpos = Math.min(Math.max(tilesBoundingRectRelativeToGameRoiRect.x, xpos), tilesBoundingRectRelativeToGameRoiRect.br().x);
                            guesses.add(new GuessRecord(
                                    tileTypeEntry.getKey(),
                                    tileOrientationEntry.getKey(),
                                    tileImageToMatch,
                                    xpos,
                                    centerOfRectY,
                                    localCertainty));
                        }
                    } else if(rect.height > tileImageToMatch.height()) {
                        double exactRatioOfHeights = (double)rect.height / (double)tileImageToMatch.height();
                        int numTilesMatched = (int)Math.ceil(exactRatioOfHeights);
                        for(int i = 0; i < numTilesMatched; ++i) {
                            // for these long matches we decrease the certainty of the match near the edges
                            // because the long matches tend to bleed into the adjacent tiles...
                            double localCertainty = certainty;
                            if(i == 0 || i == numTilesMatched - 1) {
                                localCertainty *= 0.9;
                            }
                            double ypos = centerOfRectY + i * tileImageToMatch.height() - (numTilesMatched - 1) * (double)tileImageToMatch.height() / 2;
                            // This approach sometimes bleeds outside the tile space pretty badly
                            // so clamp the x val to within the tiles bounding rect
                            ypos = Math.min(Math.max(tilesBoundingRectRelativeToGameRoiRect.y, ypos), tilesBoundingRectRelativeToGameRoiRect.br().y);
                            guesses.add(new GuessRecord(
                                    tileTypeEntry.getKey(),
                                    tileOrientationEntry.getKey(),
                                    tileImageToMatch,
                                    centerOfRectX,
                                    ypos,
                                    localCertainty));
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

        if(guesses.isEmpty()) {
            return null;
        }

        // find smallest and largest xpos and ypos to build the bounds for our grid
        GuessRecord first = guesses.get(0);
        double smallestXpos = first.xpos;
        double largestXpos = first.xpos + first.matchedImage.width();
        double smallestYpos = first.ypos;
        double largestYpos = first.ypos + first.matchedImage.height();
        double meanMatchedImageWidth = 0;
        double meanMatchedImageHeight = 0;
        double perGuessFactor = 1 / (double)guesses.size();
        for(GuessRecord guess: guesses) {
            if(!guess.type.equals(TileType.EMPTY)) {
                smallestXpos = Math.min(smallestXpos, guess.xpos);
                largestXpos = Math.max(largestXpos, guess.xpos + guess.matchedImage.width());
                smallestYpos = Math.min(smallestYpos, guess.ypos);
                largestYpos = Math.max(largestYpos, guess.ypos + guess.matchedImage.height());
            }
            meanMatchedImageWidth += perGuessFactor * guess.matchedImage.width();
            meanMatchedImageHeight += perGuessFactor * guess.matchedImage.height();
        }

        int cols = (int) Math.round((largestXpos - smallestXpos) / meanMatchedImageWidth);
        int rows = (int) Math.round((largestYpos - smallestYpos) / meanMatchedImageHeight);
        GridInfo gridInfo = new GridInfo();
        gridInfo.colWidth = meanMatchedImageWidth;
        gridInfo.rowHeight = meanMatchedImageHeight;
        gridInfo.originX = (largestXpos - (cols*gridInfo.colWidth) + smallestXpos) / 2;
        gridInfo.originY = (largestYpos - (rows*gridInfo.rowHeight) + smallestYpos) / 2;


        if(DEBUG) {
            Mat guessesVisualized = new Mat(gameImageRoi.size(), CvType.CV_8UC1);
            for(GuessRecord guess: guesses) {
                Rect roiRect = new Rect(
                        (int)guess.xpos,
                        (int)guess.ypos,
                        guess.matchedImage.width(),
                        guess.matchedImage.height());
                Mat roi = new Mat(guessesVisualized, roiRect);
                if(guess.type.equals(TileType.EMPTY)) {
                    Core.add(roi, new Scalar(127), roi);
                } else {
                    Core.add(roi, guess.matchedImage, roi);
                }
            }

            for(int colIndex = 0; colIndex <= cols; ++colIndex) {
                double xCol = gridInfo.originX + colIndex * gridInfo.colWidth;
                Imgproc.line(
                        guessesVisualized,
                        new Point(xCol, 0),
                        new Point(xCol, guessesVisualized.height() - 1),
                        new Scalar(255));
            }
            for(int rowIndex = 0; rowIndex <= rows; ++rowIndex) {
                double yRow = gridInfo.originY + rowIndex * gridInfo.rowHeight;
                Imgproc.line(
                        guessesVisualized,
                        new Point(0, yRow),
                        new Point(guessesVisualized.width() - 1, yRow),
                        new Scalar(255));
            }
            Debug.sendMatrix(guessesVisualized);
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        TileState[][] gridState = new TileState[cols][rows];
        for(int colIndex = 0; colIndex < cols; ++colIndex) {
            for(int rowIndex = 0; rowIndex < rows; ++rowIndex) {
                gridState[colIndex][rowIndex] = TileState.EMPTY;
            }
        }

        double[][] gridCertainties = new double[cols][rows];

        for(GuessRecord guess: guesses) {
            int colIndex = (int) Math.round((guess.xpos - gridInfo.originX) / gridInfo.colWidth);
            int rowIndex = (int) Math.round((guess.ypos - gridInfo.originY) / gridInfo.rowHeight);
            TileState targetEntry = gridState[colIndex][rowIndex];
            double priorCertainty = gridCertainties[colIndex][rowIndex];
            if (guess.certainty > priorCertainty) {
                TileState newVal = new TileState();
                newVal.type = guess.type;
                newVal.orientation = guess.orientation;
                gridState[colIndex][rowIndex] = newVal;
                gridCertainties[colIndex][rowIndex] = guess.certainty;
            }
        }

        // Now we offset the grid info because of the ROI cropping we did before processing
        gridInfo.originX += gameImageRoiRect.x;
        gridInfo.originY += gameImageRoiRect.y;

        // Now scale up the grid info up because we downsize all the images for processing.
        // The multiplication puts the grid back in the original scale of the screen capture.
        double reverseScaleFactor = 1 / globalScaleFactor;
        gridInfo.originX *= reverseScaleFactor;
        gridInfo.originY *= reverseScaleFactor;
        gridInfo.colWidth *= reverseScaleFactor;
        gridInfo.rowHeight *= reverseScaleFactor;


        if(DEBUG) {
            Mat debugImage = new Mat(b.getHeight(), b.getWidth(), CvType.CV_8UC1);
            for (int colIndex = 0; colIndex < cols; ++colIndex) {
                for (int rowIndex = 0; rowIndex < rows; ++rowIndex) {
                    TileState t = gridState[colIndex][rowIndex];
                    if(t.type != TileType.EMPTY) {
                        final Mat tileImage = mTileImages.get(t.type).precomputedImages.get(derivedScale).get(t.orientation);
                        final Mat resizedTileImage = new Mat();
                        Imgproc.resize(tileImage, resizedTileImage, new Size(gridInfo.colWidth, gridInfo.rowHeight));
                        Rect roiRect = new Rect(
                                (int) (gridInfo.originX + colIndex * gridInfo.colWidth),
                                (int) (gridInfo.originY + rowIndex * gridInfo.rowHeight),
                                resizedTileImage.width(),
                                resizedTileImage.height());
                        //Log.d(TAG, roiRect.toString());
                        Mat roi = new Mat(debugImage, roiRect);
                        Core.add(roi, resizedTileImage, roi);
                    }
                }
            }
            Debug.sendMatrix(debugImage);
        }

        return new GameState(gridInfo, gridState);
    }

    private static Rect getTilesBoundingRect(Mat gameImage) {
        List<MatOfPoint> contours = new ArrayList<>();
        Mat hierarchy = new Mat();
        Imgproc.findContours(
                gameImage.clone(),
                contours,
                hierarchy,
                Imgproc.RETR_LIST,
                Imgproc.CHAIN_APPROX_NONE);
        ArrayList<Point> nonZeroPointsList = new ArrayList<>();
        for(MatOfPoint contour : contours) {
            nonZeroPointsList.addAll(contour.toList());
        }
        MatOfPoint nonZeroPoints = new MatOfPoint();
        nonZeroPoints.fromList(nonZeroPointsList);
        return Imgproc.boundingRect(nonZeroPoints);
    }

    private double getTileScale(Mat gameImage) {
        // A level MUST have either Corners or End tiles in order to be topologically sound.
        // So we scan for these two types and see what scale image matches best in order to detect
        // the scale factor for images for the full sweep
        final TileType[] scaleSamplingTileTypes = {TileType.CORNER, TileType.END};
        for (TileType type : scaleSamplingTileTypes) {
            Map<Double, Double> scaleScoreMapping = new HashMap<>();
            for(Map.Entry<Double, Map<TileOrientation, Mat>> tileScaleEntry : mTileImages.get(type).precomputedImages.entrySet()) {
                double bestScore = 1.0;
                for(Map.Entry<TileOrientation, Mat> tileOrientationEntry : tileScaleEntry.getValue().entrySet()) {
                    Mat tileImageToMatch = tileOrientationEntry.getValue();
                    Mat match = new Mat();
                    Imgproc.matchTemplate(gameImage, tileImageToMatch, match, Imgproc.TM_SQDIFF_NORMED);
                    final Core.MinMaxLocResult minMaxLocResult = Core.minMaxLoc(match);
                    bestScore = Math.min(bestScore, minMaxLocResult.minVal);
                }
                // Just in case there weren't any matches at all
                if(bestScore < 0.2) {
                    scaleScoreMapping.put(tileScaleEntry.getKey(), bestScore);
                }
            }
            if (!scaleScoreMapping.isEmpty()) {
                double bestScoreFound = Collections.min(scaleScoreMapping.values());
                for(Map.Entry<Double, Double> scaleScoreEntry : scaleScoreMapping.entrySet()) {
                    if(scaleScoreEntry.getValue() == bestScoreFound) {
                        return scaleScoreEntry.getKey();
                    }
                }
            }
        }
        throw new AssertionError("Expected to match an image scale before reaching this point");
    }
}

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
    private static final boolean PROFILE = false;
    private static final int PYRAMID_LEVELS = 3;
    private static final FastMatchThresholdCallback SQDIFF_NORMED_FAST_MATCH_CALLBACK = new FastMatchThresholdCallback() {
        @Override
        public Mat call(Mat match) {
            Mat matchThreshed = new Mat();
            Imgproc.threshold(match, matchThreshed, 0.75, 1, Imgproc.THRESH_BINARY_INV);
            // Reset to 1 because with sqdiff algorithm zero corresponds to match
            match.setTo(new Scalar(1));
            return matchThreshed;
        }
    };

    private final Map<TileType, PrecomputedTileImageData> mTileImages = new HashMap<>();

    private static double globalScaleFactor = 0.5;
    private final ArrayList<Double> tileImageScalesRange = new ArrayList<>();

    // cv::BuildPyramid from Imgproc
    public static void buildPyramid(Mat src, List<Mat> dst, int maxlevel, int borderType) {
        if(BuildConfig.DEBUG) {
            if(borderType == Core.BORDER_CONSTANT) {
                throw new AssertionError("No support for Core.BORDER_CONSTANT");
            }
        }
        dst.clear();
        dst.add(src.clone());
        Mat prevLevel = dst.get(0);
        for (int i = 1; i <= maxlevel; ++i) {
            Mat nextLevel = new Mat();
            Imgproc.pyrDown(prevLevel, nextLevel, new Size(), borderType);
            dst.add(nextLevel);
            prevLevel = nextLevel;
        }
    }

    // cv::BuildPyramid from Imgproc
    public static void buildPyramid(Mat src, List<Mat> dst, int maxlevel) {
        buildPyramid(src, dst, maxlevel, Core.BORDER_DEFAULT);
    }


    // This callback should do two things.
    // 1. Return a thresholded copy of match that masks the region to check on the next iteration
    // 2. Reset the match Mat so that it possible to accumulate fresh matchResults into it.
    // This may involve zeroing it out or filling it with large vals depending on your choice
    // of matchTemplate method / thresholds etc.
    public interface FastMatchThresholdCallback {
        Mat call(Mat match);
    }

    public static Mat fastMatchTemplate(
            List<Mat> scenePyr,
            List<Mat> templatePyr,
            int method,
            FastMatchThresholdCallback cb) {
        final int maxLevel = Math.min(scenePyr.size(), templatePyr.size()) - 1;
        Mat prevMatchResult = new Mat();
        Imgproc.matchTemplate(scenePyr.get(maxLevel), templatePyr.get(maxLevel), prevMatchResult, method);
        for (int curLevel = maxLevel - 1; curLevel >= 0; --curLevel) {
            Mat scene = scenePyr.get(curLevel);
            Mat template = templatePyr.get(curLevel);
            Mat prevMatchResultUp = new Mat();
            Imgproc.pyrUp(prevMatchResult, prevMatchResultUp);
            // prevMatchResult is conceptually an identical space to the new matchResult,
            // but due to quantisation errors in the halving / doubling process it can be slightly
            // different size. We'll resize it to be identical though as it allows for less
            // defensive coding in the subsequent operations
            Mat prevMatchResultResized = new Mat();
            Size newMatchResultSize = new Size(
                    scene.width() - template.width() + 1,
                    scene.height() - template.height() + 1);
            Imgproc.resize(prevMatchResult, prevMatchResultResized, newMatchResultSize);
            Mat prevMatchResultThreshed = cb.call(prevMatchResultResized);
            // Renaming for clarity as the callback should have reset the matrix
            Mat matchResult = prevMatchResultResized;
            Mat mask8u = new Mat();
            prevMatchResultThreshed.convertTo(mask8u, CvType.CV_8U);
            List<MatOfPoint> contours = new ArrayList<>();
            Imgproc.findContours(
                    mask8u,
                    contours,
                    new Mat(),
                    Imgproc.RETR_EXTERNAL,
                    Imgproc.CHAIN_APPROX_NONE);
            for(MatOfPoint contour : contours) {
                Rect boundingRect = Imgproc.boundingRect(contour);
                Rect sceneRoiRect = new Rect(
                        boundingRect.x,
                        boundingRect.y,
                        boundingRect.width + template.width() - 1,
                        boundingRect.height + template.height() - 1);
                Mat sceneRoi = new Mat(scene, sceneRoiRect);
                Imgproc.matchTemplate(
                        sceneRoi,
                        template,
                        new Mat(matchResult, boundingRect),
                        method);
            }
            prevMatchResult = matchResult;
        }
        return prevMatchResult;
    }

    public static Mat rotateImage(Mat img, double angleDegrees) {
        Point center = new Point(img.cols() / 2, img.rows() / 2);
        Mat rotMat = Imgproc.getRotationMatrix2D(center, -angleDegrees, 1.0);
        Mat dest = new Mat();
        Imgproc.warpAffine(img, dest, rotMat, img.size());
        return dest;
    }

    public static Mat bitmapToBinaryMat(Bitmap b, double scaleFactor) {
        Mat colorMat = new Mat();
        Utils.bitmapToMat(b, colorMat, b.isPremultiplied());
        Mat greyMat = new Mat();
        Imgproc.cvtColor(colorMat, greyMat, Imgproc.COLOR_BGR2GRAY);
        Mat resizedMat = new Mat();
        Imgproc.resize(greyMat, resizedMat, new Size(), scaleFactor, scaleFactor, Imgproc.INTER_AREA);
        Mat bwMat = new Mat();
        Imgproc.threshold(resizedMat, bwMat, 0, 255, Imgproc.THRESH_OTSU | Imgproc.THRESH_BINARY_INV);
        return bwMat;
    }

    private class PrecomputedTileImageData {
        public final Map<Double, Map<TileOrientation, List<Mat>>> precomputedImages = new HashMap<>();
        public PrecomputedTileImageData(TileType tt, Bitmap baseImage) {
            // extra 0.5 factor because sample images were double size from screenshot
            Mat binaryMat = bitmapToBinaryMat(baseImage, globalScaleFactor * 0.5);
            for(double scale : tileImageScalesRange) {
                Map<TileOrientation, List<Mat>> orientationMap = new HashMap<>();
                Mat resizedMat = new Mat();
                Imgproc.resize(binaryMat, resizedMat, new Size(), scale, scale, Imgproc.INTER_AREA);
                for(TileOrientation o : tt.getPossibleOrientations()) {
                    Mat rotatedScaledImage = rotateImage(resizedMat, o.getAngle());
                    List<Mat> pyramid = new ArrayList<>();
                    buildPyramid(rotatedScaledImage, pyramid, PYRAMID_LEVELS);
                    orientationMap.put(o, pyramid);
                }
                precomputedImages.put(scale, orientationMap);
            }
        }
    }

    public ImageProcessor(AssetManager assMan) {
        for (double s = 1.0; s > 0.75; s -= 0.02) {
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
        if (PROFILE) {
            android.os.Debug.startMethodTracing();
        }
        // Seems opencv doesnt handle the bitmap very well when
        // there's aligment, so we copy it here to unalign it
        Bitmap unalignedBitmap = b.copy(b.getConfig(), true);
        try {
            if (DEBUG) {
                Debug.sendBitmap(unalignedBitmap);
            }
            ArrayList<GuessRecord> guesses = new ArrayList<>();
            // Testing is calibrated to the 1080 px wide emulator...
            double lowResScaleFactor = globalScaleFactor * 1080 / unalignedBitmap.getWidth();
            Mat lowResBinaryGameImage = bitmapToBinaryMat(unalignedBitmap, lowResScaleFactor);

            if (DEBUG) {
                Debug.sendMatrix(lowResBinaryGameImage);
            }
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
            int gameImageRoiRectX = Math.max(tilesBoundingRect.x - buffer, 0);
            int gameImageRoiRectY = Math.max(tilesBoundingRect.y - buffer, 0);
            final Rect gameImageRoiRect = new Rect(
                    gameImageRoiRectX,
                    gameImageRoiRectY,
                    Math.min(tilesBoundingRect.width + 2 * buffer, lowResBinaryGameImage.width() - gameImageRoiRectX),
                    Math.min(tilesBoundingRect.height + 2 * buffer, lowResBinaryGameImage.height() - gameImageRoiRectY));

            final Rect tilesBoundingRectRelativeToGameRoiRect = new Rect(
                    tilesBoundingRect.x - gameImageRoiRect.x,
                    tilesBoundingRect.y - gameImageRoiRect.y,
                    tilesBoundingRect.width,
                    tilesBoundingRect.height);

            if(DEBUG) {
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

            Mat gameImageRoi = new Mat(lowResBinaryGameImage, gameImageRoiRect);
            if (DEBUG) {
                Debug.sendMatrix(gameImageRoi);
            }
            ArrayList<Mat> gameImageRoiPyramid = new ArrayList<>();
            buildPyramid(gameImageRoi, gameImageRoiPyramid, PYRAMID_LEVELS);
            double derivedScale = getTileScale(gameImageRoiPyramid);
            Log.d(TAG, String.format("Tile scale: %s", derivedScale));
            for (Map.Entry<TileType, PrecomputedTileImageData> tileTypeEntry : mTileImages.entrySet()) {
                Map<TileOrientation, List<Mat>> orientationImageMap = tileTypeEntry.getValue().precomputedImages.get(derivedScale);
                for (Map.Entry<TileOrientation, List<Mat>> tileOrientationEntry : orientationImageMap.entrySet()) {
                    List<Mat> tileImageToMatchPyr = tileOrientationEntry.getValue();
                    Mat tileImageToMatch = tileImageToMatchPyr.get(0);
                    if(DEBUG) {
                        Debug.sendMatrix(tileImageToMatch);
                    }
                    Mat match = fastMatchTemplate(
                            gameImageRoiPyramid,
                            tileImageToMatchPyr,
                            Imgproc.TM_SQDIFF_NORMED,
                            SQDIFF_NORMED_FAST_MATCH_CALLBACK);
                    Mat matchThreshed = new Mat();
                    Imgproc.threshold(match, matchThreshed, 0.3, 255, Imgproc.THRESH_BINARY_INV);
                    Mat eightBitMatchThreshed = new Mat();
                    matchThreshed.convertTo(eightBitMatchThreshed, CvType.CV_8U);
                    if (DEBUG) {
                        //if (tileTypeEntry.getKey().equals(TileType.LINE)) {
                            Debug.sendMatrix(eightBitMatchThreshed);
                        //}
                    }
                    List<MatOfPoint> contours = new ArrayList<>();
                    Mat hierarchy = new Mat();
                    Imgproc.findContours(
                            eightBitMatchThreshed.clone(),
                            contours,
                            hierarchy,
                            Imgproc.RETR_LIST,
                            Imgproc.CHAIN_APPROX_NONE);
                    for (MatOfPoint contour : contours) {
                        final Rect rect = Imgproc.boundingRect(contour);

                        double centerOfRectX = rect.x + (double) rect.width / 2;
                        double centerOfRectY = rect.y + (double) rect.height / 2;

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
                        if (rect.width > tileImageToMatch.width()) {
                            double exactRatioOfWidths = (double) rect.width / (double) tileImageToMatch.width();
                            int numTilesMatched = (int) Math.floor(exactRatioOfWidths) + 1;
                            for (int i = 0; i < numTilesMatched; ++i) {
                                // for these long matches we decrease the certainty of the match near the edges
                                // because the long matches tend to bleed into the adjacent tiles...
                                double localCertainty = certainty;
                                if (i == 0 || i == numTilesMatched - 1) {
                                    localCertainty *= 0.8;
                                }
                                double xpos = centerOfRectX + i * tileImageToMatch.width() - (numTilesMatched - 1) * (double) tileImageToMatch.width() / 2;
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
                        } else if (rect.height > tileImageToMatch.height()) {
                            double exactRatioOfHeights = (double) rect.height / (double) tileImageToMatch.height();
                            int numTilesMatched = (int) Math.floor(exactRatioOfHeights) + 1;
                            for (int i = 0; i < numTilesMatched; ++i) {
                                // for these long matches we decrease the certainty of the match near the edges
                                // because the long matches tend to bleed into the adjacent tiles...
                                double localCertainty = certainty;
                                if (i == 0 || i == numTilesMatched - 1) {
                                    localCertainty *= 0.9;
                                }
                                double ypos = centerOfRectY + i * tileImageToMatch.height() - (numTilesMatched - 1) * (double) tileImageToMatch.height() / 2;
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

            if (guesses.isEmpty()) {
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
            double perGuessFactor = 1 / (double) guesses.size();
            for (GuessRecord guess : guesses) {
                if (!guess.type.equals(TileType.EMPTY)) {
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
            gridInfo.originX = (largestXpos - (cols * gridInfo.colWidth) + smallestXpos) / 2;
            gridInfo.originY = (largestYpos - (rows * gridInfo.rowHeight) + smallestYpos) / 2;


            if (DEBUG) {
                Mat guessesVisualized = new Mat(gameImageRoi.size(), CvType.CV_8UC1);
                for (GuessRecord guess : guesses) {
                    Rect roiRect = new Rect(
                            (int) guess.xpos,
                            (int) guess.ypos,
                            guess.matchedImage.width(),
                            guess.matchedImage.height());
                    Mat roi = new Mat(guessesVisualized, roiRect);
                    if (guess.type.equals(TileType.EMPTY)) {
                        Mat grey = new Mat(roiRect.height, roiRect.width, CvType.CV_8UC1, new Scalar(127));
                        Core.addWeighted(roi, 0.8, grey, 0.8, 0, roi);
                    } else {
                        Core.addWeighted(roi, 0.8, guess.matchedImage, 0.8, 0, roi);
                    }
                }

                for (int colIndex = 0; colIndex <= cols; ++colIndex) {
                    double xCol = gridInfo.originX + colIndex * gridInfo.colWidth;
                    Imgproc.line(
                            guessesVisualized,
                            new Point(xCol, 0),
                            new Point(xCol, guessesVisualized.height() - 1),
                            new Scalar(255));
                }
                for (int rowIndex = 0; rowIndex <= rows; ++rowIndex) {
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
            for (int colIndex = 0; colIndex < cols; ++colIndex) {
                for (int rowIndex = 0; rowIndex < rows; ++rowIndex) {
                    gridState[colIndex][rowIndex] = TileState.EMPTY;
                }
            }

            double[][] gridCertainties = new double[cols][rows];

            for (GuessRecord guess : guesses) {
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
            double reverseScaleFactor = 1 / lowResScaleFactor;
            gridInfo.originX *= reverseScaleFactor;
            gridInfo.originY *= reverseScaleFactor;
            gridInfo.colWidth *= reverseScaleFactor;
            gridInfo.rowHeight *= reverseScaleFactor;


            if (DEBUG) {
                Mat debugImage = new Mat(unalignedBitmap.getHeight(), unalignedBitmap.getWidth(), CvType.CV_8UC1);
                for (int colIndex = 0; colIndex < cols; ++colIndex) {
                    for (int rowIndex = 0; rowIndex < rows; ++rowIndex) {
                        TileState t = gridState[colIndex][rowIndex];
                        if (t.type != TileType.EMPTY) {
                            final Mat tileImage = mTileImages.get(t.type).precomputedImages.get(derivedScale).get(t.orientation).get(0);
                            final Mat resizedTileImage = new Mat();
                            Imgproc.resize(tileImage, resizedTileImage, new Size(gridInfo.colWidth, gridInfo.rowHeight));
                            Rect roiRect = new Rect(
                                    (int) (gridInfo.originX + colIndex * gridInfo.colWidth),
                                    (int) (gridInfo.originY + rowIndex * gridInfo.rowHeight),
                                    resizedTileImage.width(),
                                    resizedTileImage.height());
                            //Log.d(TAG, roiRect.toString());
                            Mat roi = new Mat(debugImage, roiRect);
                            Core.addWeighted(roi, 0.8, resizedTileImage, 0.8, 0, roi);
                        }
                    }
                }
                Debug.sendMatrix(debugImage);
            }

            return new GameState(gridInfo, gridState);
        } finally {
            unalignedBitmap.recycle();
            if (PROFILE) {
                android.os.Debug.stopMethodTracing();
            }
        }
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

    private double getTileScale(ArrayList<Mat> gameImageRoiPyramid) {
        // A level MUST have either Corners or End tiles in order to be topologically sound.
        // So we scan for these two types and see what scale image matches best in order to detect
        // the scale factor for images for the full sweep
        // Try end pieces for match first as they seem to be slightly more reliable than corners
        final TileType[] scaleSamplingTileTypes = {TileType.END, TileType.CORNER};
        for (TileType type : scaleSamplingTileTypes) {
            Map<Double, Double> scaleScoreMapping = new HashMap<>();
            for(Map.Entry<Double, Map<TileOrientation, List<Mat>>> tileScaleEntry : mTileImages.get(type).precomputedImages.entrySet()) {
                double bestScore = 1.0;
                for(Map.Entry<TileOrientation, List<Mat>> tileOrientationEntry : tileScaleEntry.getValue().entrySet()) {
                    List<Mat> tileImageToMatchPyr = tileOrientationEntry.getValue();
                    Mat match = fastMatchTemplate(
                            gameImageRoiPyramid,
                            tileImageToMatchPyr,
                            Imgproc.TM_SQDIFF_NORMED,
                            SQDIFF_NORMED_FAST_MATCH_CALLBACK);
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

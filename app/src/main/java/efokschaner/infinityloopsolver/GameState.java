package efokschaner.infinityloopsolver;

import com.rits.cloning.Cloner;

import java.util.ArrayList;
import java.util.List;

public class GameState {
    private static final String TAG = ImageProcessor.class.getSimpleName();
    private static final Cloner mCloner = new Cloner();
    private int mCols;
    private int mRows;

    private void doRotateTile(int col, int row, TileOrientation targetOrientation) {
        TileState t = mGridState[col][row];
        int clickPosX = (int) (mGridInfo.originX + mGridInfo.colWidth * (col + 0.5));
        int clickPosY = (int) (mGridInfo.originY + mGridInfo.rowHeight * (row + 0.5));
        while(!t.orientation.equals(targetOrientation)) {
            t.orientation = t.orientation.rotate();
            mActionsTaken.add(new ClickAction(clickPosX, clickPosY));
        }
    }

    private static boolean isValidOrientation(
            TileType type,
            TileOrientation candidate,
            TileState up,
            TileState right,
            TileState down,
            TileState left) {
        int directionsNeeded = Direction.applyOrientation(candidate, type.getConnectionDirections());
        {
            int upCanConnectDown = up.canConnect(Direction.DOWN);
            if (upCanConnectDown == TileState.CANNOT_CONNECT)
                if ((Direction.UP & directionsNeeded) != 0)
                    return false;

            if (upCanConnectDown == TileState.MUST_CONNECT)
                if ((Direction.UP & directionsNeeded) == 0)
                    return false;
        }
        {
            int rightCanConnectLeft = right.canConnect(Direction.LEFT);
            if (rightCanConnectLeft == TileState.CANNOT_CONNECT)
                if ((Direction.RIGHT & directionsNeeded) != 0)
                    return false;

            if (rightCanConnectLeft == TileState.MUST_CONNECT)
                if ((Direction.RIGHT & directionsNeeded) == 0)
                    return false;
        }
        {
            int downCanConnectUp = down.canConnect(Direction.UP);
            if (downCanConnectUp == TileState.CANNOT_CONNECT)
                if ((Direction.DOWN & directionsNeeded) != 0)
                    return false;

            if (downCanConnectUp == TileState.MUST_CONNECT)
                if ((Direction.DOWN & directionsNeeded) == 0)
                    return false;
        }
        {
            int leftCanConnectRight = left.canConnect(Direction.RIGHT);
            if (leftCanConnectRight == TileState.CANNOT_CONNECT)
                if ((Direction.LEFT & directionsNeeded) != 0)
                    return false;

            if (leftCanConnectRight == TileState.MUST_CONNECT)
                if ((Direction.LEFT & directionsNeeded) == 0)
                    return false;
        }
        return true;
    }

    public List<ClickAction> getSolution() throws UnsolvableError {
        boolean foundMove = false;
        boolean solved = true;
        do {
            foundMove = false;
            solved = true;
            for(int colIndex = 0; colIndex < mCols; ++colIndex) {
                for(int rowIndex = 0; rowIndex < mRows; ++rowIndex) {
                    TileState t = mGridState[colIndex][rowIndex];
                    if(!t.isOrientationSolved) {
                        ArrayList<TileOrientation> validOrientations = getValidTileOrientations(colIndex, rowIndex);
                        if(validOrientations.size() == 1) {
                            // Rotate the piece from its current orientation to the validOrientation
                            doRotateTile(colIndex, rowIndex, validOrientations.get(0));
                            t.isOrientationSolved = true;
                            foundMove = true;
                        } else {
                            solved = false;
                        }
                        if(validOrientations.size() == 0) {
                            throw new UnsolvableError();
                        }
                    }
                }
            }
        } while (foundMove);

        if(solved) {
            return mActionsTaken;
        } else {
            // for all the valid moves on all the tiles
            for (int colIndex = 0; colIndex < mCols; ++colIndex) {
                for (int rowIndex = 0; rowIndex < mRows; ++rowIndex) {
                    TileState t = mGridState[colIndex][rowIndex];
                    if (!t.isOrientationSolved) {
                        ArrayList<TileOrientation> validOrientations = getValidTileOrientations(colIndex, rowIndex);
                        for(TileOrientation validOrientation : validOrientations) {
                            GameState hypotheticalState = new GameState(mGridInfo, mCloner.deepClone(mGridState));
                            hypotheticalState.doRotateTile(colIndex, rowIndex, validOrientation);
                            hypotheticalState.mGridState[colIndex][rowIndex].isOrientationSolved = true;
                            try{
                                final List<ClickAction> solution = hypotheticalState.getSolution();
                                List<ClickAction> actionsToReturn = new ArrayList<>();
                                actionsToReturn.addAll(mActionsTaken);
                                actionsToReturn.addAll(solution);
                                return actionsToReturn;
                            } catch (UnsolvableError e) {
                                // ignore
                            }
                        }
                    }
                }
            }
            // If we get to the end, we were not able to find a solution...
            throw new UnsolvableError();
        }
    }

    private ArrayList<TileOrientation> getValidTileOrientations(int colIndex, int rowIndex) {
        TileState t = mGridState[colIndex][rowIndex];
        // check left right up and down squares
        TileState up = rowIndex > 0 ? mGridState[colIndex][rowIndex - 1]  : TileState.EMPTY;
        TileState right = colIndex < mCols - 1 ? mGridState[colIndex + 1][rowIndex]  : TileState.EMPTY;
        TileState down = rowIndex < mRows - 1 ? mGridState[colIndex][rowIndex + 1]  : TileState.EMPTY;
        TileState left = colIndex > 0 ? mGridState[colIndex - 1][rowIndex] : TileState.EMPTY;
        // find orientations that are valid with the adjacent squares
        ArrayList<TileOrientation> validOrientations = new ArrayList<>();
        TileOrientation[] candidateOrientations = t.type.getPossibleOrientations();
        for(TileOrientation candidate : candidateOrientations) {
            if(isValidOrientation(t.type, candidate, up, right, down, left)) {
                validOrientations.add(candidate);
            }
        }
        return validOrientations;
    }

    private GridInfo mGridInfo;
    private TileState[][] mGridState;
    private ArrayList<ClickAction> mActionsTaken = new ArrayList<>();

    public GameState(GridInfo gridInfo, TileState[][] gridstate) {
        mGridInfo = gridInfo;
        mGridState = gridstate;
        mCols = mGridState.length;
        mRows = mGridState[0].length;
    }

    public static class UnsolvableError extends Throwable {
    }
}

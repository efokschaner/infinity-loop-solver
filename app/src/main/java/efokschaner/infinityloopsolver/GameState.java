package efokschaner.infinityloopsolver;


public class GameState {
    public static class Direction {
        public static final int NONE = 0;
        public static final int UP = 1;
        public static final int RIGHT = 1 << 1;
        public static final int DOWN = 1 << 2;
        public static final int LEFT = 1 << 3;

        public static final int[] ALL = {UP, RIGHT, DOWN, LEFT};
    }

    // Represents offset from the canonical orientations
    public static class TileOrientation {
        public static final TileOrientation ZERO = new TileOrientation(0);
        public static final TileOrientation QUARTER = new TileOrientation(1);
        public static final TileOrientation HALF = new TileOrientation(2);
        public static final TileOrientation THREE_QUARTERS = new TileOrientation(3);

        private int mValue;

        TileOrientation(int value) {
            mValue = value;
        }

        public TileOrientation rotate(int num) {
            return new TileOrientation((mValue + num) % 3);
        }

        public TileOrientation rotate() {
            return new TileOrientation(0);
        }
    }

    public enum TileType {
        EMPTY(Direction.NONE, new TileOrientation[]{
                TileOrientation.ZERO
        }),
        END(Direction.UP, new TileOrientation[]{
                TileOrientation.ZERO,
                TileOrientation.QUARTER,
                TileOrientation.HALF,
                TileOrientation.THREE_QUARTERS
        }),
        LINE(Direction.UP| Direction.DOWN, new TileOrientation[]{
                TileOrientation.ZERO,
                TileOrientation.QUARTER
        }),
        CORNER(Direction.UP| Direction.RIGHT, new TileOrientation[]{
                TileOrientation.ZERO,
                TileOrientation.QUARTER,
                TileOrientation.HALF,
                TileOrientation.THREE_QUARTERS
        }),
        TEE(Direction.UP| Direction.RIGHT| Direction.DOWN, new TileOrientation[]{
                TileOrientation.ZERO,
                TileOrientation.QUARTER,
                TileOrientation.HALF,
                TileOrientation.THREE_QUARTERS
        }),
        CROSS(Direction.UP| Direction.RIGHT| Direction.DOWN| Direction.LEFT, new TileOrientation[]{
                TileOrientation.ZERO
        });


        private final int mConnectionDirections;
        private final TileOrientation[] mPossibleOrientations;

        // connectionDirections are which directions the tile connects to in its default orientation
        // possibleOrientations allows us to cull orientations that are the same for the purposes of the game
        TileType(int connectionDirections, TileOrientation[] possibleOrientations) {
            mConnectionDirections = connectionDirections;
            mPossibleOrientations = possibleOrientations;
        }
    }

    public static class TileState {
        TileType type;
        TileOrientation orientation;
        boolean isOrientationSolved = false;
    }

    private TileState[][] mGrid;

    public GameState(TileState[][] grid) {
        mGrid = grid;
    }


}

package efokschaner.infinityloopsolver;

public enum TileType {
    EMPTY(
            Direction.NONE,
            new TileOrientation[]{
                    TileOrientation.ZERO
            },
            "Empty.png"
    ),
    END(
            Direction.UP,
            new TileOrientation[]{
                    TileOrientation.ZERO,
                    TileOrientation.QUARTER,
                    TileOrientation.HALF,
                    TileOrientation.THREE_QUARTERS
            },
            "End.png"
    ),
    LINE(
            Direction.UP | Direction.DOWN,
            new TileOrientation[]{
                    TileOrientation.ZERO,
                    TileOrientation.QUARTER
            },
            "Line.png"
    ),
    CORNER(
            Direction.UP | Direction.RIGHT,
            new TileOrientation[]{
                    TileOrientation.ZERO,
                    TileOrientation.QUARTER,
                    TileOrientation.HALF,
                    TileOrientation.THREE_QUARTERS
            },
            "Corner.png"
    ),
    TEE(
            Direction.UP | Direction.RIGHT | Direction.DOWN,
            new TileOrientation[]{
                    TileOrientation.ZERO,
                    TileOrientation.QUARTER,
                    TileOrientation.HALF,
                    TileOrientation.THREE_QUARTERS
            },
            "Tee.png"),
    CROSS(
            Direction.UP | Direction.RIGHT | Direction.DOWN | Direction.LEFT,
            new TileOrientation[]{
                    TileOrientation.ZERO
            },
            "Cross.png"
    );


    public int getConnectionDirections() {
        return mConnectionDirections;
    }

    private final int mConnectionDirections;

    public TileOrientation[] getPossibleOrientations() {
        return mPossibleOrientations;
    }

    private final TileOrientation[] mPossibleOrientations;
    private final String mImageFileName;

    // connectionDirections are which directions the tile connects to in its default orientation
    // possibleOrientations allows us to cull orientations that are the same for the purposes of the game
    TileType(
            int connectionDirections,
            TileOrientation[] possibleOrientations,
            String imageFileName) {
        mConnectionDirections = connectionDirections;
        mPossibleOrientations = possibleOrientations;
        mImageFileName = imageFileName;
    }

    public String getImageFileName() {
        return mImageFileName;
    }
}

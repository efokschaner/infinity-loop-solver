package efokschaner.infinityloopsolver;

public class TileState {
    public static TileState EMPTY = new TileState(true);

    public TileState() {
    }

    public TileState(boolean isOrientationSolved) {
        this.isOrientationSolved = isOrientationSolved;
    }

    TileType type = TileType.EMPTY;
    TileOrientation orientation = TileOrientation.ZERO;
    boolean isOrientationSolved = false;

    public static final int CANNOT_CONNECT = 0;
    public static final int CAN_CONNECT = 1;
    public static final int MUST_CONNECT = 2;

    public int canConnect(int direction) {
        if (isOrientationSolved) {
            if (needsConnect(direction)) {
                return MUST_CONNECT;
            } else {
                return CANNOT_CONNECT;
            }
        } else {
            return CAN_CONNECT;
        }
    }

    public boolean needsConnect(int direction) {
        if (isOrientationSolved) {
            int neededDirections = Direction.applyOrientation(orientation, type.getConnectionDirections());
            return (direction & neededDirections) != 0;
        } else {
            return false;
        }
    }
}

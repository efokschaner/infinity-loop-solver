package efokschaner.infinityloopsolver;


public class Direction {
    public static final int NONE = 0;
    public static final int UP = 1;
    public static final int RIGHT = 1 << 1;
    public static final int DOWN = 1 << 2;
    public static final int LEFT = 1 << 3;

    public static final int[] ALL = {UP, RIGHT, DOWN, LEFT};

    public static int applyOrientation(TileOrientation o, int directions) {
        int rotated = directions << o.getValue();
        return (rotated | (rotated << 4)) >> 4;
    }
}

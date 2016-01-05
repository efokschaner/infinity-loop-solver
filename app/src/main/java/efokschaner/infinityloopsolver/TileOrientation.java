package efokschaner.infinityloopsolver;

// Represents offset from the canonical orientations
public class TileOrientation {
    public static final TileOrientation ZERO = new TileOrientation(0);
    public static final TileOrientation QUARTER = new TileOrientation(1);
    public static final TileOrientation HALF = new TileOrientation(2);
    public static final TileOrientation THREE_QUARTERS = new TileOrientation(3);

    private int mValue;

    TileOrientation(int value) {
        mValue = value;
    }

    public TileOrientation rotate(int num) {
        return new TileOrientation((mValue + num) % 4);
    }

    public TileOrientation rotate() {
        return rotate(1);
    }

    public double getAngle() {
        return mValue * 90;
    }

    @Override
    public boolean equals(Object aThat) {
        //check for self-comparison
        if ( this == aThat ) return true;
        if ( !(aThat instanceof TileOrientation) ) return false;
        //cast to native object is now safe
        TileOrientation that = (TileOrientation)aThat;

        //now a proper field-by-field evaluation can be made
        return that.mValue == this.mValue;
    }

    @Override
    public int hashCode() {
        return this.mValue;
    }

    public int getValue() {
        return mValue;
    }
}

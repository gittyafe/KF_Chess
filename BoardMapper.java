public class BoardMapper {
    
    private static final int PIXELS_PER_CELL = 100;
    
    public static int pixelToCell(int pixel) {
        return pixel / PIXELS_PER_CELL;
    }

}

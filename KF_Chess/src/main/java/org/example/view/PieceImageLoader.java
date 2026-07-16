package org.example.view;

import org.example.Img;
import java.awt.Dimension;
import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * טוענת ומטמינה (cache) את אנימציות הכלים.
 *
 * שינוי מרכזי בריפקטור: גודל תמונות הכלים נגזר מ-BoardGeometry (שהוא גם
 * מה ש-ImgRenderer משתמש בו לטעינת תמונת הלוח), במקום מ-cellSize מקומי
 * שחושב בנפרד (cellSize - 20). כך מובטח שתמונות הכלים תמיד יתאימו בדיוק
 * לגודל המשבצת בפועל בלוח - גם אם מקטינים/מגדילים את הלוח.
 */
public class PieceImageLoader {
    private static final char[] PIECE_TYPES = {'P', 'R', 'N', 'B', 'Q', 'K'};
    private static final char[] COLORS = {'w', 'b'};
    private static final String[] STATE_FOLDERS = {"idle", "jump", "move", "long_rest", "short_rest"};
    private static final String PIECES_ROOT = "src/main/resources/pieces";

    private final Map<String, AnimationConfig> piecesCache = new HashMap<>();
    private final BoardGeometry geometry;

    public PieceImageLoader(BoardGeometry geometry) {
        this.geometry = geometry;
    }

    public void preload() {
        Dimension targetSize = geometry.getPieceTargetSize();

        for (char color : COLORS) {
            char colorUpper = Character.toUpperCase(color);
            for (char type : PIECE_TYPES) {
                String pieceDirName = "" + Character.toUpperCase(type) + colorUpper;
                for (String stateFolder : STATE_FOLDERS) {
                    preloadState(pieceDirName, color, type, stateFolder, targetSize);
                }
            }
        }
    }

    private void preloadState(String pieceDirName, char color, char type, String stateFolder, Dimension targetSize) {
        AnimationConfig config = new AnimationConfig();

        String stateDir = PIECES_ROOT + "/" + pieceDirName + "/states/" + stateFolder;
        ConfigParser.parseJson(stateDir + "/config.json", config);

        int frameIndex = 1;
        while (true) {
            String imagePath = stateDir + "/sprites/" + frameIndex + ".png";
            if (!new File(imagePath).exists()) {
                break;
            }
            Img img = loadFrame(imagePath, targetSize);
            if (img == null) {
                break;
            }
            config.frames.add(img);
            frameIndex++;
        }

        if (!config.frames.isEmpty()) {
            piecesCache.put(cacheKey(color, type, stateFolder), config);
        }
    }

    private Img loadFrame(String imagePath, Dimension targetSize) {
        try {
            return new Img().read(imagePath, targetSize, true, null);
        } catch (Exception e) {
            System.err.println("Failed to load piece sprite: " + imagePath + " (" + e.getMessage() + ")");
            return null;
        }
    }

    public AnimationConfig getAnimation(char color, char type, String stateFolder) {
        AnimationConfig config = piecesCache.get(cacheKey(color, type, stateFolder));

        if (config == null || config.frames.isEmpty()) {
            config = piecesCache.get(cacheKey(color, type, "idle"));
        }
        return config;
    }

    private String cacheKey(char color, char type, String stateFolder) {
        return Character.toLowerCase(color) + "_" + Character.toUpperCase(type) + "_" + stateFolder;
    }
}

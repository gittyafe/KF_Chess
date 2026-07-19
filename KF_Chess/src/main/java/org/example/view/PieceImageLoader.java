package org.example.view;

import java.awt.Dimension;
import java.io.File;
import java.util.HashMap;
import java.util.Map;

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
            for (char type : PIECE_TYPES) {
                String pieceDirName = "" + Character.toUpperCase(type) + Character.toLowerCase(color);
                for (String stateFolder : STATE_FOLDERS) {
                    preloadState(pieceDirName, color, type, stateFolder, targetSize);
                }
            }
        }
    }

    private void preloadState(String pieceDirName, char color, char type, String stateFolder, Dimension targetSize) {
        AnimationConfig config = new AnimationConfig();
        String stateDir = PIECES_ROOT + "/" + pieceDirName + "/states/" + stateFolder;

        if (!new File(stateDir).exists()) return;

        ConfigParser.parseJson(stateDir + "/config.json", config);

        int frameIndex = 1;
        while (true) {
            String imagePath = stateDir + "/sprites/" + frameIndex + ".png";
            if (!new File(imagePath).exists()) break;

            Img img = new Img().read(imagePath, null, false, null);
            if (img == null) break;

            config.getFrames().add(img);
            frameIndex++;
        }

        if (!config.getFrames().isEmpty()) {
            piecesCache.put(cacheKey(color, type, stateFolder), config);
        }
    }

    public AnimationConfig getAnimation(char color, char type, String stateFolder) {
        String key = cacheKey(color, type, stateFolder);
        AnimationConfig cachedConfig = piecesCache.get(key);

        if (cachedConfig == null || cachedConfig.getFrames().isEmpty()) {
            cachedConfig = piecesCache.get(cacheKey(color, type, "idle"));
        }

        return cachedConfig;
    }

    private String cacheKey(char color, char type, String stateFolder) {
        return Character.toLowerCase(color) + "_" + Character.toUpperCase(type) + "_" + stateFolder.toLowerCase();
    }
}

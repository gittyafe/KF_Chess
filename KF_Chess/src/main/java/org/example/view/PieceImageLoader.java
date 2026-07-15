package org.example.view;

import org.example.Img;
import java.awt.Dimension;
import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class PieceImageLoader {
    private final Map<String, AnimationConfig> piecesCache = new HashMap<>();
    private final int cellSize;

    public PieceImageLoader(int cellSize) {
        this.cellSize = cellSize;
    }

    public void preload() {
        Dimension targetSize = new Dimension(cellSize - 20, cellSize - 20);
        char[] types = {'P', 'R', 'N', 'B', 'Q', 'K'};
        char[] colors = {'w', 'b'};

        for (char color : colors) {
            char colorUpper = Character.toUpperCase(color);
            for (char type : types) {
                String pieceDirName = "" + Character.toUpperCase(type) + colorUpper;
                preloadState(pieceDirName, color, type, "idle", targetSize);
                preloadState(pieceDirName, color, type, "jump", targetSize);
                preloadState(pieceDirName, color, type, "move", targetSize);
                preloadState(pieceDirName, color, type, "long_rest", targetSize);
                preloadState(pieceDirName, color, type, "short_rest", targetSize);

            }
        }
    }

    private void preloadState(String pieceDirName, char color, char type, String stateFolder, Dimension targetSize) {
        String cacheKey = color + "_" + Character.toUpperCase(type) + "_" + stateFolder;
        AnimationConfig config = new AnimationConfig(); //

        String configPath = "src/main/resources/pieces/" + pieceDirName + "/states/" + stateFolder + "/config.json";
        ConfigParser.parseJson(configPath, config); // עיבוד ה-JSON החדש

        int frameIndex = 1;
        while (true) {
            String imagePath = "src/main/resources/pieces/" + pieceDirName + "/states/" + stateFolder + "/sprites/" + frameIndex + ".png";
            if (!new File(imagePath).exists()) {
                break;
            }
            try {
                Img img = new Img().read(imagePath, targetSize, true, null); //
                if (img != null) {
                    config.frames.add(img);
                    frameIndex++;
                } else {
                    break;
                }
            } catch (Exception e) {
                break;
            }
        }

        if (!config.frames.isEmpty()) {
            piecesCache.put(cacheKey, config);
        }
    }

    public AnimationConfig getAnimation(char color, char type, String stateFolder) {
        String cacheKey = color + "_" + Character.toUpperCase(type) + "_" + stateFolder;
        AnimationConfig config = piecesCache.get(cacheKey);
        // Fallback למצב ברירת מחדל idle אם האנימציה המבוקשת לא נמצאה
        if (config == null || config.frames.isEmpty()) {
            config = piecesCache.get(color + "_" + Character.toUpperCase(type) + "_idle");
        }
        return config;
    }
}
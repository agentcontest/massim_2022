package massim.game.environment;

import massim.game.environment.zones.ZoneType;
import massim.protocol.data.Position;
import massim.util.Log;
import massim.util.RNG;
import org.json.JSONArray;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Map;

public abstract class GridBuilder {

    private static final Map<Integer, String> bitmapColors =
            Map.of(-16777216, "obstacle", -1, "empty", -65536, "goal");

    public static void fromBitmap(String path, Grid grid) {
        if (!path.isBlank()){
            var mapFile = new File(path);
            if (mapFile.exists()) {
                try {
                    BufferedImage img = ImageIO.read(mapFile);
                    var width = Math.min(grid.getDimX(), img.getWidth());
                    var height = Math.min(grid.getDimY(), img.getHeight());
                    for (int x = 0; x < width; x++) { for (int y = 0; y < height; y++) {
                        switch(bitmapColors.getOrDefault(img.getRGB(x, y), "empty")) {
                            case "obstacle" -> grid.obstacles().create(Position.of(x, y));
                            case "goal" -> grid.addZone(ZoneType.GOAL, Position.of(x, y), 1);
                            default -> Log.log(Log.Level.ERROR, "Unknown bitmap color: " + img.getRGB(x, y));
                        }
                    }}
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            else Log.log(Log.Level.ERROR, "File " + mapFile.getAbsolutePath() + " not found.");
        }
    }

    public static void addObstaclesFromConfig(JSONArray instructions, Grid grid) {
        var dimX = grid.getDimX();
        var dimY = grid.getDimY();
        boolean[][] obstacles = new boolean[dimX][dimY];
        for (var i = 0; i < instructions.length(); i++) {
            var instruction = instructions.optJSONArray(i);
            if (instruction == null) continue;
            switch (instruction.getString(0)) {
                case "line-border" -> {
                    var width = instruction.getInt(1);
                    for (var j = 0; j < width; j++) createLineBorder(obstacles, j);
                }
                case "ragged-border" -> {
                    var width = instruction.getInt(1);
                    createRaggedBorder(obstacles, width);
                }
                case "cave" -> {
                    var chanceAlive = instruction.getDouble(1);
                    for (int x = 0; x < dimX; x++) {
                        for (int y = 0; y < dimY; y++) {
                            if (RNG.nextDouble() < chanceAlive) obstacles[x][y] = true;
                        }
                    }
                    var iterations = instruction.getInt(2);
                    var createLimit = instruction.getInt(3);
                    var destroyLimit = instruction.getInt(4);
                    for (var it = 0; it < iterations; it++) {
                        obstacles = doCaveIteration(obstacles, createLimit, destroyLimit);
                    }
                }
            }
        }
        for (int y = 0; y < dimY; y++) for (int x = 0; x < dimX; x++)
            if (obstacles[x][y]) grid.obstacles().create(Position.of(x, y));
    }

    private static boolean[][] doCaveIteration(boolean[][] obstacles, int createLimit, int destroyLimit) {
        int dimX = obstacles.length;
        int dimY = obstacles[0].length;
        var newTerrain = new boolean[dimX][dimY];
        for (var x = 0; x < dimX; x++) { for (var y = 0; y < dimY; y++) {
            var n = countObstacleNeighbours(obstacles, x,y);
            if (obstacles[x][y]) {
                newTerrain[x][y] = (n >= destroyLimit);
            }
            else if (!obstacles[x][y]) {
                newTerrain[x][y] = (n > createLimit);
            }
            else {
                newTerrain[x][y] = obstacles[x][y];
            }
        }}
        return newTerrain;
    }

    private static int countObstacleNeighbours(boolean[][] obstacles, int cx, int cy) {
        var count = 0;
        for (var x = cx - 1; x <= cx + 1; x++) { for (var y = cy - 1; y <= cy + 1; y++) {
            if (x != cx || y != cy) {
                var pos = Position.wrapped(x, y);
                if (obstacles[pos.x][pos.y]) count++;
            }
        }}
        return count;
    }

    /**
     * @param offset distance to the outer map boundaries
     */
    private static void createLineBorder(boolean[][] obstacles, int offset) {
        int dimX = obstacles.length;
        int dimY = obstacles[0].length;
        for (int x = offset; x < dimX - offset; x++) {
            obstacles[x][offset] = true;
            obstacles[x][dimY - (offset + 1)] = true;
        }
        for (int y = offset; y < dimY - offset; y++) {
            obstacles[offset][y] = true;
            obstacles[dimX - (offset + 1)][y] = true;
        }
    }

    private static void createRaggedBorder(boolean[][] obstacles, int width) {
        int dimX = obstacles.length;
        int dimY = obstacles[0].length;
        var currentWidth = width;
        for (var x = 0; x < dimX; x++) {
            currentWidth = Math.max(currentWidth - 1 + RNG.nextInt(3), 1);
            for (var i = 0; i < currentWidth; i++) obstacles[x][i] = true;
        }
        currentWidth = width;
        for (var x = 0; x < dimX; x++) {
            currentWidth = Math.max(currentWidth - 1 + RNG.nextInt(3), 1);
            for (var i = 0; i < currentWidth; i++) obstacles[x][dimY - (i + 1)] = true;
        }
        currentWidth = width;
        for (var y = 0; y < dimY; y++) {
            currentWidth = Math.max(currentWidth - 1 + RNG.nextInt(3), 1);
            for (var i = 0; i < currentWidth; i++) obstacles[i][y] = true;
        }
        currentWidth = width;
        for (var y = 0; y < dimY; y++) {
            currentWidth = Math.max(currentWidth - 1 + RNG.nextInt(3), 1);
            for (var i = 0; i < currentWidth; i++) obstacles[dimX - (i + 1)][y] = true;
        }
    }
}

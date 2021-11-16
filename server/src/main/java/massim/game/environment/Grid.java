package massim.game.environment;

import massim.game.environment.positionable.Entity;
import massim.game.environment.positionable.*;
import massim.game.environment.zones.Zone;
import massim.game.environment.zones.ZoneList;
import massim.game.environment.zones.ZoneType;
import massim.protocol.data.Position;
import massim.util.Log;
import massim.util.RNG;
import org.json.JSONArray;
import org.json.JSONObject;


import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.*;

public class Grid {

    public static final Set<String> DIRECTIONS = Set.of("n", "s", "e", "w");
    public static final Set<String> ROTATION_DIRECTIONS = Set.of("cw", "ccw");
    private static final Map<Integer, String> bitmapColors =
            Map.of(-16777216, "obstacle", -1, "empty", -65536, "goal");

    private final int dimX;
    private final int dimY;
    private final int attachLimit;
    private final double moveProbability;

    private final AttachableHub attachables = new AttachableHub();
    private final EntityHub entities = new EntityHub();
    private final BlockHub blocks = new BlockHub();
    private final ObstacleHub obstacles = new ObstacleHub();
    private final MarkerHub markers = new MarkerHub();
    private final DispenserHub dispensers = new DispenserHub();

    private final ZoneList goalZones = new ZoneList();
    private final ZoneList roleZones = new ZoneList();

    public Grid(JSONObject gridConf, int attachLimit) {

        Entity.setObservers(List.of(attachables, entities));
        Block.setObservers(List.of(attachables, blocks));
        Obstacle.setObservers(List.of(attachables, obstacles));
        Marker.setObservers(List.of(markers));
        Dispenser.setObservers(List.of(dispensers));

        this.attachLimit = attachLimit;
        this.dimX = gridConf.getInt("width");
        this.dimY = gridConf.getInt("height");
        Position.setGridDimensions(dimX, dimY);

        // terrain from bitmap
        String mapFilePath = gridConf.optString("file");
        if (!mapFilePath.isBlank()){
            var mapFile = new File(mapFilePath);
            if (mapFile.exists()) {
                try {
                    BufferedImage img = ImageIO.read(mapFile);
                    var width = Math.min(dimX, img.getWidth());
                    var height = Math.min(dimY, img.getHeight());
                    for (int x = 0; x < width; x++) { for (int y = 0; y < height; y++) {
                        switch(bitmapColors.getOrDefault(img.getRGB(x, y), "empty")) {
                            case "obstacle" -> this.obstacles.create(Position.of(x, y));
                            case "goal" -> addZone(ZoneType.GOAL, Position.of(x, y), 1);
                            default -> Log.log(Log.Level.ERROR, "Unknown bitmap color: " + img.getRGB(x, y));
                        }
                    }}
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            else Log.log(Log.Level.ERROR, "File " + mapFile.getAbsolutePath() + " not found.");
        }

        this.addObstaclesFromConfig(gridConf.getJSONArray("instructions"));

        var goalConf = gridConf.getJSONObject("goals");
        this.moveProbability = goalConf.getDouble("moveProbability");
        this.addZonesFromConfig(ZoneType.GOAL, goalConf);

        this.addZonesFromConfig(ZoneType.ROLE, gridConf.getJSONObject("roleZones"));
    }

    private void addObstaclesFromConfig(JSONArray instructions) {
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
                            if (RNG.nextDouble() < chanceAlive) this.obstacles.create(Position.of(x, y));
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
            if (obstacles[x][y]) this.obstacles.create(Position.of(x, y));
    }

    private void addZonesFromConfig(ZoneType type, JSONObject zoneConf) {
        var zoneCount = zoneConf.getInt("number");
        var sizeBounds = zoneConf.getJSONArray("size");
        var sizeMin = sizeBounds.getInt(0);
        var sizeMax = sizeBounds.getInt(1);
        for (var i = 0; i < zoneCount; i++) {
            var centerPos = findRandomFreePosition();
            var size = RNG.betweenClosed(sizeMin, sizeMax);
            this.addZone(type, centerPos, size);
        }
    }

    private ZoneList getZoneList(ZoneType type) {
        return switch (type) {
            case GOAL -> this.goalZones;
            case ROLE -> this.roleZones;
        };
    }

    public void addZone(ZoneType type, Position xy, int radius) {
        this.getZoneList(type).add(xy, radius);
    }

    public void removeZone(ZoneType type, Position pos) {
        this.getZoneList(type).remove(pos);
    }

    public boolean isNotInZone(ZoneType type, Position pos) {
        return !this.getZoneList(type).isInZone(pos);
    }

    public List<Zone> getZones(ZoneType type) {
        return getZoneList(type).getZones();
    }

    /**
     * @return distance to the nearest role zone's center or null if there is no such role zone
     */
    public Integer getDistanceToNextZone(ZoneType type, Position pos) {
        return switch(type) {
            case GOAL -> this.goalZones.getClosest(pos).position().distanceTo(pos);
            case ROLE -> this.roleZones.getClosest(pos).position().distanceTo(pos);
        };
    }

    private boolean[][] doCaveIteration(boolean[][] obstacles, int createLimit, int destroyLimit) {
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

    private int countObstacleNeighbours(boolean[][] obstacles, int cx, int cy) {
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
    private void createLineBorder(boolean[][] obstacles, int offset) {
        for (int x = offset; x < dimX - offset; x++) {
            obstacles[x][offset] = true;
            obstacles[x][dimY - (offset + 1)] = true;
        }
        for (int y = offset; y < dimY - offset; y++) {
            obstacles[offset][y] = true;
            obstacles[dimX - (offset + 1)][y] = true;
        }
    }

    private void createRaggedBorder(boolean[][] obstacles, int width) {
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

    public int getDimX() {
        return dimX;
    }

    public int getDimY() {
        return dimY;
    }

    private void moveMany(Set<Positionable> things, Map<Positionable, Position> newPositions) {
        for (Positionable thing : things)
            thing.moveTo(newPositions.get(thing));
    }

    /**
     * Moves an Attachable to a given position.
     * Only works if target is free and attachable has nothing attached.
     */
    public void moveWithoutAttachments(Attachable a, Position pos) {
        if(isUnblocked(pos) && a.getAttachments().isEmpty())
            a.moveTo(pos);
    }

    public boolean attach(Attachable a1, Attachable a2) {
        if (a1 == null || a2 == null) return false;
        if (a1.getPosition().distanceTo(a2.getPosition()) != 1) return false;

        var attachments = a1.collectAllAttachments();
        attachments.addAll(a2.collectAllAttachments());
        if (attachments.size() > attachLimit) return false;

        a1.attach(a2);
        return true;
    }

    public boolean detachNeighbors(Attachable a1, Attachable a2) {
        if (a1 == null || a2 ==  null) return false;
        if (a1.getPosition().distanceTo(a2.getPosition()) != 1) return false;
        if (!a1.getAttachments().contains(a2)) return false;
        a1.detach(a2);
        return true;
    }

    /**
     * @return whether the movement succeeded
     */
    public boolean moveWithAttached(Attachable anchor, String direction, int distance) {
        var things = new HashSet<Positionable>(anchor.collectAllAttachments());
        var newPositions = canMove(things, direction, distance);
        if (newPositions == null) return false;
        this.moveMany(things, newPositions);
        return true;
    }

    /**
     * @return whether the rotation succeeded
     */
    public boolean rotateWithAttached(Attachable anchor, boolean clockwise) {
        var newPositions = this.canRotate(anchor, clockwise);
        if (newPositions == null) return false;
        this.moveMany(newPositions.keySet(), newPositions);
        return true;
    }

    /**
     * Checks if the anchor element and all attachments can rotate 90deg in the given direction.
     * Intermediate positions (the "diagonals") are also checked for all attachments.
     * @return a map from the element and all attachments to their new positions after rotation or null if anything is blocked
     */
    private Map<Positionable, Position> canRotate(Attachable anchor, boolean clockwise) {
        var attachments = new HashSet<Positionable>(anchor.collectAllAttachments());
        if(attachments.stream().anyMatch(a -> a != anchor && a instanceof Entity)) return null;
        var newPositions = new HashMap<Positionable, Position>();
        for (var a : attachments) {
            var rotatedPos = a.getPosition().rotated90(anchor.getPosition(), clockwise);
            if(!isUnblocked(rotatedPos, attachments)) return null;
            newPositions.put(a, rotatedPos);
        }
        return newPositions;
    }

    private Map<Positionable, Position> canMove(Set<Positionable> things, String direction, int distance) {
        var newPositions = new HashMap<Positionable, Position>();
        for (var thing : things) {
            for (int i = 1; i <= distance; i++) {
                var newPos = thing.getPosition().moved(direction, i);
                if(!isUnblocked(newPos, things)) return null;
            }
            newPositions.put(thing, thing.getPosition().moved(direction, distance));
        }
        return newPositions;
    }

    public Position findRandomFreePosition() {
        int x = RNG.nextInt(this.dimX);
        int y = RNG.nextInt(this.dimY);
        final int startX = x;
        final int startY = y;
        while (isBlocked(Position.of(x,y))) {
            if (++x >= dimX) {
                x = 0;
                if (++y >= dimY) y = 0;
            }
            if (x == startX && y == startY) {
                Log.log(Log.Level.ERROR, "No free position");
                return null;
            }
        }
        return Position.of(x, y);
    }
    
    public ArrayList<Position> findRandomFreeClusterPosition(int clusterSize) {
        ArrayList<Position> cluster = new ArrayList<>();
        int x = RNG.nextInt(dimX);
        int y = RNG.nextInt(dimY);
        final int radius = (int) (Math.log(clusterSize)/Math.log(2)); 
        final int startX = x;
        final int startY = y;
        
        while (isBlocked(Position.of(x,y)) || !hasEnoughFreeSpots(Position.of(x,y), radius, clusterSize)) {
            if (++x >= dimX) {
                x = 0;
                if (++y >= dimY) y = 0;
            }
            if (x == startX && y == startY) {
                Log.log(Log.Level.ERROR, "No free position");
                return null;
            }
        }

        Position.of(x, y).spanArea(radius).forEach((p) -> {
            if(cluster.size() == clusterSize) return;
            if(isUnblocked(p)) cluster.add(p);
        });

        return cluster;
    }

    /**
     * Opposite of isUnblocked()
     */
    public boolean isBlocked(Position pos) {
        return !isUnblocked(pos);
    }

    private boolean hasEnoughFreeSpots(Position origin, int radius, int numberPositionNeeded){
        int freeSpots = 0;
        for (Position p : origin.spanArea(radius)) 
            if (isUnblocked(p))
                freeSpots++;
        return freeSpots >= numberPositionNeeded;
    }

    public Position findRandomFreePosition(Position center, int maxDistance) {
        for (var i = 0; i < 50; i++) {
            int x = center.x;
            int y = center.y;
            int dx = RNG.nextInt(maxDistance + 1);
            int dy = RNG.nextInt(maxDistance + 1);
            x += RNG.nextDouble() < .5? dx : -dx;
            y += RNG.nextDouble() < .5? dy : -dy;
            var target = Position.of(x, y).wrapped();
            if (this.isUnblocked(target)) return target;
        }
        return null;
    }

    /**
     * @return true if there is no attachable (i.e. an entity, a block, an obstacle, ...) in the cell
     */
    public boolean isUnblocked(Position xy) {
        return isUnblocked(xy, Collections.emptySet());
    }

    private boolean isUnblocked(Position xy, Set<Positionable> excludedObjects) {
        return !this.attachables.isTaken(xy.wrapped(), excludedObjects);
    }

    public void deleteMarkers() {
        markers.clear();
    }

    public Position getRandomPosition() {
        return Position.of(RNG.nextInt(dimX), RNG.nextInt(dimY));
    }

    /**
     * Moves the goal zone (a random one if multiple zones overlap) at the current position to a random location
     * according to moveProbability.
     * @param position the position at which to look for a goal zone
     */
    public void moveGoalZone(Position position) {
        if (RNG.nextDouble() > this.moveProbability) return;

        var possibleZone = goalZones.findOneZoneAt(position);
        if (possibleZone.isEmpty()) return;

        var zone = possibleZone.get();
        var newPos = zone.position();
        while (goalZones.contains(newPos)) {
            newPos = getRandomPosition();
        }

        this.removeZone(ZoneType.GOAL, zone.position());
        this.addZone(ZoneType.GOAL, newPos, zone.radius());
        Log.log(Log.Level.NORMAL, "Goal moved from " + zone.position() + " to " + newPos);
    }

    public ObstacleHub obstacles() {
        return this.obstacles;
    }

    public EntityHub entities() {
        return this.entities;
    }
    public BlockHub blocks() {
        return this.blocks;
    }

    public MarkerHub markers() {
        return this.markers;
    }

    public DispenserHub dispensers() {
        return this.dispensers;
    }

    public AttachableHub attachables() {
        return this.attachables;
    }

    public Attachable getUniqueAttachable(Position pos) {
        var things = attachables.lookup(pos);
        if (things.size() != 1) {
            Log.log(Log.Level.ERROR, "No unique attachable: " + things);
            return null;
        }
        return things.iterator().next();
    }

    public Collection<Attachable> getThingsDestroyedByClear(Position pos) {
        var result = new ArrayList<Attachable>();
        var o = this.obstacles.lookup(pos);
        if (o != null) result.add(o);
        var b = this.blocks.lookup(pos);
        if (b != null) result.add(b);
        return result;
    }

    /**
     * @return everything that can be found at the given position (entities, blocks, obstacles, markers, dispensers)
     */
    public List<Positionable> getEverythingAt(Position pos) {
        var result = new ArrayList<Positionable>(this.attachables.lookup(pos));
        result.addAll(markers.lookup(pos));
        if (dispensers.isTaken(pos))
            result.add(dispensers.lookup(pos));
        return result;
    }
}
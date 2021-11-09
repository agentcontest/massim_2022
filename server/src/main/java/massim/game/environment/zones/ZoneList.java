package massim.game.environment.zones;

import massim.protocol.data.Position;

import java.util.*;

public class ZoneList {

    private final Map<Position, Zone> zones = new HashMap<>();
    private final Map<Position, Integer> cellPresence = new HashMap<>();

    public void add(Position xy, int radius) {
        this.zones.put(xy, new Zone(xy, radius));
        for (Position pos : xy.spanArea(radius))
            this.cellPresence.merge(pos, 1, Integer::sum);
    }

    public void remove(Position zonePosition) {
        Zone z = this.zones.remove(zonePosition);
        if (z == null) return;
        for (Position pos : zonePosition.spanArea(z.radius()))
            cellPresence.merge(pos, -1, Integer::sum);
    }

    public Zone getClosest(Position pos) {
        var closestZone =
                this.zones.values().stream().min(Comparator.comparing(zone -> zone.position().distanceTo(pos)));
        return closestZone.orElse(null);
    }

    public boolean isInZone(Position pos) {
        return this.cellPresence.getOrDefault(pos, 0) > 0;
    }

    public List<Zone> getZones() {
        return new ArrayList<>(zones.values());
    }

    public Optional<Zone> findOneZoneAt(Position pos) {
        return zones.values().stream()
                .filter(zone -> zone.position().distanceTo(pos) <= zone.radius())
                .findAny();
    }

    /**
     * @return whether there is a zone with the given center
     */
    public boolean contains(Position pos) {
        return this.zones.containsKey(pos);
    }
}
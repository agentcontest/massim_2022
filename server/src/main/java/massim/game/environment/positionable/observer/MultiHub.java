package massim.game.environment.positionable.observer;

import massim.game.environment.positionable.Positionable;
import massim.protocol.data.Position;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Tracks positions of Positionables. Each position can have multiple positionables.
 */
public abstract class MultiHub<T extends Positionable> implements PositionObserver {

    private final Map<Position, Set<T>> positionToItems = new HashMap<>();
    private final Map<Integer, T> idToItem = new HashMap<>();

    public Set<T> lookup(Position pos) {
        return new HashSet<>(this.getEntry(pos));
    }

    public Set<T> getAll() {
        return new HashSet<>(idToItem.values());
    }

    protected T add(T item) {
        this.idToItem.put(item.getID(), item);
        this.getEntry(item.getPosition()).add(item);
        return item;
    }

    @Override
    public void notifyCreate(Positionable p) {}

    @Override
    public void notifyDestroy(Positionable positionable) {
        var thing = idToItem.get(positionable.getID());
        if (thing != null)
            this.getEntry(thing.getPosition()).remove(thing);
        idToItem.remove(positionable.getID());
    }

    @Override
    public void notifyMove(Positionable p, Position oldPosition, Position newPosition) {
        var item = this.idToItem.get(p.getID());
        if (item == null) return;
        this.getEntry(oldPosition).remove(item);
        this.getEntry(newPosition).add(item);
    }

    public boolean isTaken(Position pos) {
        return this.getEntry(pos).size() > 0;
    }

    public boolean isTaken(Position pos, Set<Positionable> excludedObjects) {
        var things = this.lookup(pos);
        things.removeAll(excludedObjects.stream()
                                        .map(o -> this.idToItem.get(o.getID()))
                                        .collect(Collectors.toSet()));
        return things.size() > 0;
    }

    public Collection<Positionable> addThingsAt(Position position, Collection<Positionable> toThisCollection) {
        toThisCollection.addAll(this.getEntry(position));
        return toThisCollection;
    }

    /**
     * Destroys everything tracked by this hub.
     */
    public void clear() {
        for (T t : this.getAll())
            t.destroy();
    }

    public Set<T> getEntry(Position pos) {
        return this.positionToItems.computeIfAbsent(pos, key -> new HashSet<>());
    }
}
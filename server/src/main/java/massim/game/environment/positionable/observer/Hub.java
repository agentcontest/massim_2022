package massim.game.environment.positionable.observer;

import massim.game.environment.positionable.Positionable;
import massim.protocol.data.Position;
import massim.util.Log;

import java.util.*;

/**
 * Tracks positions of Positionables.
 */
public class Hub<T extends Positionable> implements PositionObserver {

    private final Map<Position, T> positionToItem = new HashMap<>();
    private final Map<Integer, T> idToItem = new HashMap<>();

    public T lookup(Position pos) {
        return positionToItem.get(pos);
    }

    public Set<T> getAll() {
        return new HashSet<>(positionToItem.values());
    }

    protected T add(T item) {
        this.idToItem.put(item.getID(), item);
        this.positionToItem.put(item.getPosition(), item);
        return item;
    }

    @Override
    public void notifyCreate(Positionable p) {
        var previous = this.positionToItem.get(p.getPosition());
        if (previous != null && previous != p)
            Log.log(Log.Level.ERROR, "Created item in the same position as another: " + p.toJSON()
                    + " blocked by " + previous.toJSON());
    }

    @Override
    public void notifyDestroy(Positionable positionable) {
        this.removeItemAtPosition(positionable, positionable.getPosition());
    }

    @Override
    public void notifyMove(Positionable p, Position oldPosition, Position newPosition) {
        this.removeItemAtPosition(p, oldPosition);
        this.positionToItem.put(newPosition, idToItem.get(p.getID()));
    }

    private void removeItemAtPosition(Positionable p, Position pos) {
        T item = idToItem.get(p.getID());
        this.positionToItem.remove(pos, item);
    }

    public boolean isTaken(Position pos) {
        return this.positionToItem.get(pos) != null;
    }

    public boolean isTaken(Position pos, Set<Positionable> excludedObjects) {
        var thing = positionToItem.get(pos);
        if (thing == null) return false;
        return !excludedObjects.contains(thing);
    }

    public Collection<Positionable> addThingAt(Position position, Collection<Positionable> toThisCollection) {
        var thing = this.lookup(position);
        if (thing != null)
            toThisCollection.add(thing);
        return toThisCollection;
    }
}

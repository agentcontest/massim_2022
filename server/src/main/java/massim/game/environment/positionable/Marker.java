package massim.game.environment.positionable;

import massim.game.environment.positionable.observer.MultiHub;
import massim.game.environment.positionable.observer.PositionObserver;
import massim.protocol.data.Position;
import massim.protocol.data.Thing;

import java.util.ArrayList;
import java.util.List;

/**
 * A simple marker marking a position.
 */
public class Marker extends Positionable {

    private static List<PositionObserver> observers = new ArrayList<>();

    public static void setObservers(List<PositionObserver> observers) {
        Marker.observers = observers;
    }


    private final Type type;

    Marker(Position pos, Type type) {
        super(pos);
        this.type = type;
    }

    public Type getType() {
        return type;
    }

    @Override
    public Thing toPercept(Position relativeTo) {
        var pos = getPosition().relativeTo(relativeTo);
        return new Thing(pos.x, pos.y, Thing.TYPE_MARKER, type.name);
    }

    @Override
    public String toString() {
        return "Marker("  + getPosition()+"," + type+")";
    }

    @Override
    protected void onDestroyed() {}

    public enum Type {
        CLEAR("clear"),
        CLEAR_PERIMETER("cp"),
        CLEAR_IMMEDIATE("ci");

        public final String name;

        Type(String name) {
            this.name = name;
        }
    }

    @Override
    public List<PositionObserver> getObservers() {
        return Marker.observers;
    }
}

package massim.game.environment.positionable;

import massim.game.environment.positionable.observer.PositionObserver;
import massim.protocol.data.Position;
import massim.protocol.data.Thing;

import java.util.ArrayList;
import java.util.List;

public class Obstacle extends Attachable{

    private static List<PositionObserver> observers = new ArrayList<>();

    public static void setObservers(List<PositionObserver> observers) {
        Obstacle.observers = observers;
    }


    Obstacle(Position position) {
        super(position);
    }

    @Override
    public Thing toPercept(Position entityPosition) {
        var relativePosition = getPosition().relativeTo(entityPosition);
        return new Thing(relativePosition.x, relativePosition.y, Thing.TYPE_OBSTACLE, "");
    }

    @Override
    public List<PositionObserver> getObservers() {
        return Obstacle.observers;
    }
}
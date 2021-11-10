package massim.game.environment;

import massim.protocol.data.Position;
import massim.protocol.data.Thing;

public class Obstacle extends Attachable{

    public Obstacle(Position position) {
        super(position);
    }

    @Override
    public Thing toPercept(Position entityPosition) {
        var relativePosition = getPosition().relativeTo(entityPosition);
        return new Thing(relativePosition.x, relativePosition.y, Thing.TYPE_OBSTACLE, "");
    }
}
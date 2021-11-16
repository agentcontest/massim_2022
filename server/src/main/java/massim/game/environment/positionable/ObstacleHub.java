package massim.game.environment.positionable;

import massim.game.environment.positionable.observer.Hub;
import massim.protocol.data.Position;

public class ObstacleHub extends Hub<Obstacle>  {

    public Obstacle create(Position pos) {
        if (isTaken(pos)) return null;
        var obstacle = new Obstacle(pos);
        obstacle.init();
        return this.add(obstacle);
    }
}
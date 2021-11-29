package massim.game.environment.positionable;

import massim.game.environment.GameObject;
import massim.game.environment.positionable.observer.PositionObserver;
import massim.protocol.data.Position;
import org.json.JSONObject;

import java.util.List;

public abstract class Positionable extends GameObject {

    private Position position;

    public Positionable(Position position) {
        this.position = position;
    }

    public void init() {
        for (PositionObserver observer : this.getObservers())
            observer.notifyCreate(this);
    }

    public Position getPosition() {
        return this.position;
    }

    public void moveTo(Position newPosition){
        if (newPosition == null) return;
        for (PositionObserver o : this.getObservers())
            o.notifyMove(this, this.position, newPosition);
        this.position = newPosition;
    }

    public void destroy() {
        for (PositionObserver observer : this.getObservers())
            observer.notifyDestroy(this);
        this.onDestroyed();
    }

    protected abstract void onDestroyed();

    /**
     * @return List of all observers to be notified of changes. Ideally the same list for each object.
     */
    public abstract List<PositionObserver> getObservers();

    public JSONObject toJSON() {
        return new JSONObject()
                .put("id", this.getID())
                .put("pos", this.position.toJSON());
    }
}

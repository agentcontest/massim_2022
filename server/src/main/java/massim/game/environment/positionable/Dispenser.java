package massim.game.environment.positionable;

import massim.game.environment.positionable.observer.PositionObserver;
import massim.protocol.data.Position;
import massim.protocol.data.Thing;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class Dispenser extends Positionable {

    private static List<PositionObserver> observers = new ArrayList<>();

    public static void setObservers(List<PositionObserver> observers) {
        Dispenser.observers = observers;
    }


    private final String blockType;

    Dispenser(Position position, String blockType) {
        super(position);
        this.blockType = blockType;
    }

    public String getBlockType() {
        return blockType;
    }

    @Override
    public Thing toPercept(Position entityPosition) {
        Position local = getPosition().relativeTo(entityPosition);
        return new Thing(local.x, local.y, Thing.TYPE_DISPENSER, blockType);
    }

    @Override
    public String toString() {
        return "dispenser(" + getPosition().x + "," + getPosition().y + "," + blockType + ")";
    }

    @Override
    protected void onDestroyed() {}

    @Override
    public List<PositionObserver> getObservers() {
        return Dispenser.observers;
    }

    @Override
    public JSONObject toJSON() {
        return super.toJSON().put("type", this.blockType);
    }
}
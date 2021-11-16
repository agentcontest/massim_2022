package massim.game.environment.positionable;

import massim.game.environment.positionable.observer.PositionObserver;
import massim.protocol.data.Position;
import massim.protocol.data.Thing;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class Block extends Attachable {

    private static List<PositionObserver> observers = new ArrayList<>();

    public static void setObservers(List<PositionObserver> observers) {
        Block.observers = observers;
    }


    private final String blockType;

    Block(Position xy, String blockType) {
        super(xy);
        this.blockType = blockType;
    }

    public String getBlockType(){
        return this.blockType;
    }

    @Override
    public Thing toPercept(Position entityPosition) {
        Position relativePosition = getPosition().relativeTo(entityPosition);
        return new Thing(relativePosition.x, relativePosition.y, Thing.TYPE_BLOCK, blockType);
    }

    @Override
    public List<PositionObserver> getObservers() {
        return Block.observers;
    }

    @Override
    public JSONObject toJSON() {
        return super.toJSON().put("type", this.blockType);
    }
}
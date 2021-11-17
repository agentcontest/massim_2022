package massim.game.environment.positionable;

import massim.game.environment.positionable.observer.Hub;
import massim.protocol.data.Position;

public class DispenserHub extends Hub<Dispenser>  {

    public Dispenser create(Position pos, String blockType) {
        if (this.isTaken(pos)) return null;
        var d = new Dispenser(pos, blockType);
        d.init();
        return this.add(d);
    }
}

package massim.game.environment.positionable;

import massim.game.environment.positionable.observer.MultiHub;
import massim.protocol.data.Position;

public class MarkerHub extends MultiHub<Marker> {

    public Marker create(Position pos, Marker.Type type) {
        var marker = new Marker(pos, type);
        marker.init();
        return this.add(marker);
    }
}

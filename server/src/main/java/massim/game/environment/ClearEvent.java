package massim.game.environment;

import massim.protocol.data.Position;
import org.json.JSONObject;

public record ClearEvent(Position position, int step, int radius) {

    public JSONObject toJSON() {
        return new JSONObject()
                .put("pos", this.position.toJSON())
                .put("r", this.radius);
    }
}
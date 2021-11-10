package massim.game.environment.zones;

import massim.protocol.data.Position;
import org.json.JSONObject;

public record Zone(Position position, int radius) {
    public JSONObject toJSON() {
        return new JSONObject()
                .put("pos", position.toJSON())
                .put("r", radius);
    }
}
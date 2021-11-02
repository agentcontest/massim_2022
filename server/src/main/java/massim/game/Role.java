package massim.game;

import java.util.Set;

public record Role(String name, int vision, Set<String> actions, int[] speed) {
    public int maxSpeed(int attachments) {
        return speed[Math.min(attachments, speed.length - 1)];
    }
}
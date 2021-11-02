package massim.game;

import java.util.Set;

public record Role(String name, int vision, Set<String> actions, int[] speed) {

    public boolean canPerform(String action) {
        return this.actions.contains(action);
    }

    public int maxSpeed(int attachments) {
        return speed[Math.min(attachments, speed.length - 1)];
    }
}
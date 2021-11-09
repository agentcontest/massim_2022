package massim.game.environment.zones;

public enum ZoneType {
    GOAL("goal"),
    ROLE("role");

    private String name;

    ZoneType(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return this.name;
    }
}
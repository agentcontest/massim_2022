package massim.game;

import massim.config.TeamConfig;
import massim.game.environment.zones.Zone;
import massim.game.environment.zones.ZoneType;
import massim.helper.ConfigBuilder;
import massim.protocol.data.Position;
import massim.util.RNG;
import org.json.JSONObject;

import java.util.Set;

public class RolesTest {

    private JSONObject config;
    private Set<TeamConfig> teams;

    @org.junit.Before
    public void setUp() {
        RNG.initialize(17);
        int agents = 2;
        this.config = ConfigBuilder.buildGameStateConfig(agents);
        this.teams = ConfigBuilder.buildTeams(agents);
    }

    @org.junit.Test
    public void testRoleZones() {
        var number = 5;
        var size = 3;
        ConfigBuilder.setRoleZones(config, number, size);
        var state = new GameState(config, teams);

        var zones = state.getGrid().getZones(ZoneType.ROLE);
        assert zones.size() == number;

        for (Zone zone : zones) {
            assert zone.radius() == size;
            assert zone.position() != null;
            assert zone.toJSON() != null;
        }
    }
}
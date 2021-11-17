package massim.game;

import massim.config.TeamConfig;
import massim.helper.ConfigBuilder;
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
        var state = new GameState(config, teams);
    }
}

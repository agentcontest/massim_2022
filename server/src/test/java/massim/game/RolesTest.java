package massim.game;

import massim.config.TeamConfig;
import massim.game.environment.positionable.Entity;
import massim.game.environment.zones.Zone;
import massim.game.environment.zones.ZoneType;
import massim.helper.ConfigBuilder;
import massim.protocol.data.Position;
import massim.protocol.data.Role;
import massim.protocol.messages.scenario.ActionResults;
import massim.util.RNG;
import org.json.JSONObject;

import java.util.List;
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
    public void testRoles() {
        var number = 5;
        var size = 3;
        ConfigBuilder.setRoleZones(config, number, size);
        ConfigBuilder.addRole(config, new Role("testRole", 5, Set.of("adopt"), new int[]{3,2,1}));
        var state = new GameState(config, teams);

        var zones = state.getGrid().getZones(ZoneType.ROLE);
        assert zones.size() == number;

        for (Zone zone : zones) {
            assert zone.radius() == size;
            assert zone.position() != null;
            assert zone.toJSON() != null;
        }

        Zone testZone = zones.get(0);
        var zoneCenter = testZone.position();
        Entity a1 = state.getGrid().entities().getByName("A1");

        assert state.handleAdoptAction(a1, "unknownRole").equalsIgnoreCase(ActionResults.FAILED_PARAMETER);

        assert state.teleport("A1", zoneCenter.moved("e", size + 1));
        assert state.handleAdoptAction(a1, "testRole").equalsIgnoreCase(ActionResults.FAILED_LOCATION);

        assert state.teleport("A1", zoneCenter);
        assert state.handleAdoptAction(a1, "testRole").equalsIgnoreCase(ActionResults.SUCCESS);

        var position = a1.getPosition();
        assert state.handleMoveAction(a1, List.of("e", "e", "e")).equals(ActionResults.SUCCESS);
        assert a1.getPosition().equals(position.moved("e", 3));

        position = a1.getPosition();

    }
}
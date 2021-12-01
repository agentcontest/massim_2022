package massim.game;

import massim.config.TeamConfig;
import massim.game.environment.positionable.Entity;
import massim.game.environment.zones.Zone;
import massim.game.environment.zones.ZoneType;
import massim.helper.ConfigBuilder;
import massim.protocol.data.Role;
import massim.protocol.data.Thing;
import massim.protocol.messages.ActionMessage;
import massim.protocol.messages.scenario.ActionResults;
import massim.protocol.messages.scenario.StepPercept;
import massim.util.RNG;
import org.json.JSONObject;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

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
        var vision = 2;
        ConfigBuilder.addRole(config,
                new Role("testRole", vision, Set.of("adopt"), new int[]{3,2,1}, 1, 1));
        var state = new GameState(config, teams);

        var zones = state.grid().getZones(ZoneType.ROLE);
        assert zones.size() == number;

        for (Zone zone : zones) {
            assert zone.radius() == size;
            assert zone.position() != null;
            assert zone.toJSON() != null;
        }

        Zone testZone = zones.get(0);
        var zoneCenter = testZone.position();
        Entity a1 = state.grid().entities().getByName("A1");

        assert state.handleAdoptAction(a1, "unknownRole").equalsIgnoreCase(ActionResults.FAILED_PARAMETER);

        assert state.teleport("A1", zoneCenter.moved("e", size + 1));
        assert state.handleAdoptAction(a1, "testRole").equalsIgnoreCase(ActionResults.FAILED_LOCATION);

        assert state.teleport("A1", zoneCenter);
        assert state.handleAdoptAction(a1, "testRole").equalsIgnoreCase(ActionResults.SUCCESS);

        var position = a1.getPosition();
        assert state.handleMoveAction(a1, List.of("e", "e", "e")).equals(ActionResults.SUCCESS);
        assert a1.getPosition().equals(position.moved("e", 3));

        position = a1.getPosition();
        var obstacle = state.grid().obstacles().create(position.moved("e", 2));
        assert obstacle != null;
        assert state.handleMoveAction(a1, List.of("e", "e", "e")).equals(ActionResults.PARTIAL_SUCCESS);
        assert a1.getPosition().equals(position.moved("e", 1));

        position = a1.getPosition();
        assert state.grid().attach(a1, obstacle);
        assert state.handleMoveAction(a1, List.of("e", "e", "e")).equals(ActionResults.PARTIAL_SUCCESS);
        assert a1.getPosition().equals(position.moved("e", 2));

        position = a1.getPosition();
        var o2 = state.grid().obstacles().create(a1.getPosition().moved("w", 1));
        assert state.grid().attach(a1, o2);
        assert state.handleMoveAction(a1, List.of("e", "e", "e")).equals(ActionResults.PARTIAL_SUCCESS);
        assert a1.getPosition().equals(position.moved("e", 1));

        position = a1.getPosition();
        var o3 = state.grid().obstacles().create(a1.getPosition().moved("n", 1));
        assert state.grid().attach(a1, o3);
        assert state.handleMoveAction(a1, List.of("e", "e", "e")).equals(ActionResults.PARTIAL_SUCCESS);
        assert a1.getPosition().equals(position.moved("e", 1));

        position = a1.getPosition();
        var b1 = state.grid().blocks().create(position.moved("s", vision), "b1");
        var b2 = state.grid().blocks().create(position.moved("s", vision + 1), "b1");
        assert b1 != null && b2 != null;
        var percept = new StepPercept(state.getStepPercepts().get(a1.getAgentName()).toJson().getJSONObject("content"));
        assert perceptContainsThing(percept, b1.toPercept(a1.getPosition()));
        assert !perceptContainsThing(percept, b2.toPercept(a1.getPosition()));

        var sim = new Simulation();
        sim.init(1000, config, teams);
        sim.preStep(0);
        sim.step(0, Map.of("A1", new ActionMessage("survey", 0, List.of("goal"))));
        a1 = sim.getState().grid().entities().getByName("A1");
        assert a1.getLastActionResult().equals(ActionResults.FAILED_ROLE);
    }

    private static boolean perceptContainsThing(StepPercept percept, Thing thing) {
        var filteredList = percept.things.stream()
                .filter(t -> t.x == thing.x && t.y == thing.y)
                .filter(t -> t.type.equals(thing.type) && t.details.equals(thing.details)).toList();
        return filteredList.size() == 1;
    }
}
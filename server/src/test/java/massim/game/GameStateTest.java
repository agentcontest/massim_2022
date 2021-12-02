package massim.game;

import massim.game.environment.positionable.Block;
import massim.game.environment.positionable.Entity;
import massim.game.environment.zones.ZoneType;
import massim.helper.ConfigBuilder;
import massim.protocol.data.Position;
import massim.protocol.data.Role;
import massim.protocol.messages.scenario.ActionResults;
import massim.protocol.messages.scenario.StepPercept;
import massim.util.RNG;

import java.util.*;
import java.util.List;

public class GameStateTest {

    private GameState state;
    private final int agents = 2;

    @org.junit.Before
    public void setUp() {
        RNG.initialize(17);
        state = ConfigBuilder.buildGameState(this.agents);
    }

    @org.junit.Test
    public void handleRequestAction() {
        var blockTypes = state.grid().blocks().getTypes();
        var dispenserPos = Position.of(3, 3);
        Entity a1 = state.grid().entities().getByName("A1");
        assert a1 != null;
        assert state.grid().dispensers().create(dispenserPos, blockTypes.iterator().next()) != null;
        assert state.teleport("A1", dispenserPos.moved("s", 2));

        // too far away -> fail
        assert state.handleRequestAction(a1, "n").equals(ActionResults.FAILED_TARGET);
        //move closer
        assert state.handleMoveAction(a1, List.of("n")).equals(ActionResults.SUCCESS);
        // wrong param -> fail
        assert state.handleRequestAction(a1, "w").equals(ActionResults.FAILED_TARGET);
        // everything correct -> success
        assert state.handleRequestAction(a1, "n").equals(ActionResults.SUCCESS);
        // repeat -> fail
        assert state.handleRequestAction(a1, "n").equals(ActionResults.FAILED_BLOCKED);
        // another try
        assert state.grid().dispensers().create(a1.getPosition().moved("e", 1),
                blockTypes.iterator().next()) != null;
        assert state.handleRequestAction(a1, "e").equals(ActionResults.SUCCESS);
    }

    @org.junit.Test
    public void taskSubmissionWorks() {
        var a1 = state.grid().entities().getByName("A1");
        var a2 = state.grid().entities().getByName("A2");
        state.grid().addZone(ZoneType.GOAL, Position.of(15, 15), 1);
        assert state.teleport(a1.getAgentName(), Position.of(15,15));
        assert state.teleport(a2.getAgentName(), Position.of(15, 18));
        String blockType = state.grid().blocks().getTypes().iterator().next();
        Block b1 = state.grid().blocks().create(Position.of(15,16), blockType);
        assert b1 != null;
        Block b2 =  state.grid().blocks().create(Position.of(15,17), blockType);
        assert b2 != null;
        assert state.createTask("testTask1", 10, 1,
                Map.of(Position.of(0, 1), blockType, Position.of(0, 2), blockType)) != null;
        assert state.handleAttachAction(a1, "s").equals(ActionResults.SUCCESS);
        assert state.handleAttachAction(a2, "n").equals(ActionResults.SUCCESS);
        assert state.handleConnectAction(a1, Position.of(0, 1), a2, Position.of(0, -1)).equals(ActionResults.SUCCESS);
        assert state.handleSubmitAction(a1, "testTask1").equals(ActionResults.SUCCESS);
    }

    @org.junit.Test
    public void getStepPercepts() {
        this.moveAgentsToStandardPositions();
        var a1 = state.grid().entities().getByName("A1");
        var a2 = state.grid().entities().getByName("A2");
        assert state.teleport("A1", Position.of(20, 2));
        assert state.teleport("A2", Position.of(20, 3));
        assert(a1.getPosition().equals(Position.of(20, 2)));
        assert(a2.getPosition().equals(Position.of(20, 3)));

        var block = state.grid().blocks().create(Position.of(20, 4), "b1");
        assert(block != null);

        assert state.handleAttachAction(a1, "s").equals(ActionResults.SUCCESS);
        assert state.handleAttachAction(a2, "s").equals(ActionResults.SUCCESS);

        var percept = new StepPercept(state.getStepPercepts().get(a1.getAgentName()).toJson().getJSONObject("content"));
        assert(percept.attachedThings.contains(a2.getPosition().relativeTo(a1.getPosition())));
        assert(percept.attachedThings.contains(block.getPosition().relativeTo(a1.getPosition())));
    }

    @org.junit.Test
    public void clearArea() {
        var a1 = state.grid().entities().getByName("A1");
        state.teleport("A1", Position.of(10, 10));
        var block1 = state.grid().blocks().create(Position.of(10,11), "b1");
        var block2 = state.grid().blocks().create(Position.of(10,12), "b1");
        state.grid().obstacles().create(Position.of(11, 10));

        var result = state.clearArea(Position.of(10,10), 1, 1000, true);

        assert(a1.isDeactivated());
        assert(state.grid().attachables().lookup(block1.getPosition()).size() == 0);
        assert(state.grid().attachables().lookup(block2.getPosition()).size() == 1);
        assert state.grid().isUnblocked(Position.of(11, 10));
        assert(result == 2);
    }

    @org.junit.Test
    public void handleClearAction() {
        var clearRole = getClearRole("testClearRole", 1, 5);
        state.grid().entities().addRole(clearRole);
        var a1 = state.grid().entities().getByName("A1");
        a1.setRole(clearRole);
        var a2 = state.grid().entities().getByName("A2");
        var posA2 = Position.of(21, 20);
        state.teleport(a1.getAgentName(), Position.of(20, 20));
        state.teleport(a2.getAgentName(), posA2);
        var block = state.grid().blocks().create(Position.of(22, 20), "b1");
        assert block != null;

        int step = -1;

        state.prepareStep(step++);
        var result = state.handleClearAction(a1, Position.of(2, 0));
        assert(result.equals(ActionResults.SUCCESS));
        assert(!state.grid().attachables().lookup(block.getPosition()).contains(block));
        assert(state.grid().blocks().lookup(block.getPosition()) != block);

        assert(!a2.isDeactivated());
        for (int i = 0; i < 7; i++) {
            state.prepareStep(step++);
            assert(!a2.isDeactivated());
            var energy = a2.getEnergy();
            result = state.handleClearAction(a1, Position.of(1, 0));
            assert(result.equals(ActionResults.SUCCESS));
            assert(a2.getEnergy() < energy);
        }

        for (var j = 0; j < Entity.deactivatedDuration + 1; j++) {
            assert(a2.isDeactivated());
            state.prepareStep(step + j);
        }
        assert(!a2.isDeactivated());
    }

    @org.junit.Test
    public void handleRegularClearAction() {
        var clearRole = getClearRole("testClearRole", 1, 1);
        state.grid().entities().addRole(clearRole);
        var a1 = state.grid().entities().getByName("A1");
        a1.setRole(clearRole);
        var a2 = state.grid().entities().getByName("A2");
        state.teleport(a1.getAgentName(), Position.of(20, 20));
        state.teleport(a2.getAgentName(), a1.getPosition().east());

        state.prepareStep(0);
        int energy = a2.getEnergy();
        var result = state.handleClearAction(a1, Position.of(0, 0).east());
        assert result.equals(ActionResults.FAILED_TARGET);
        assert a2.getEnergy() == energy;

        var block = state.grid().blocks().create(a1.getPosition().west(), "b1");
        assert block != null;
        result = state.handleClearAction(a1, Position.of(0, 0).west());
        assert result.equals(ActionResults.SUCCESS);
        assert state.grid().blocks().lookup(block.getPosition()) != block;
    }

    @org.junit.Test
    public void handleDisconnectAction() {
        var a1 = state.grid().entities().getByName("A1");
        var a2 = state.grid().entities().getByName("A2");
        state.teleport(a1.getAgentName(), Position.of(10,10));
        state.teleport(a2.getAgentName(), Position.of(10,14));
        var b1 = state.grid().blocks().create(Position.of(10, 11), "b1");
        var b2 = state.grid().blocks().create(Position.of(10, 12), "b1");
        var b3 = state.grid().blocks().create(Position.of(10, 13), "b1");
        assert state.grid().attach(a1, b1);
        assert state.grid().attach(b1, b2);
        assert state.grid().attach(b2, b3);
        assert state.grid().attach(b3, a2);

        assert b2.collectAllAttachments(false).contains(b3);
        assert b3.collectAllAttachments(false).contains(b2);

        state.handleDisconnectAction(a1,
                b2.getPosition().relativeTo(a1.getPosition()), b3.getPosition().relativeTo(a1.getPosition()));

        assert !b2.collectAllAttachments(false).contains(b3);
        assert !b3.collectAllAttachments(false).contains(b2);
    }

    @org.junit.Test
    public void handleSurveyAction() {
        var a1 = state.grid().entities().getByName("A1");
        assert a1 != null;

        state.grid().addZone(ZoneType.ROLE, a1.getPosition().moved("n", 10), 2);
        assert state.grid().getZones(ZoneType.ROLE).size() == 1;
        assert state.handleSurveyZoneAction(a1, ZoneType.ROLE).equals(ActionResults.SUCCESS);
        var percept = this.getPercept("A1");
        assert percept.stepEvents.length() == 1;
        assert percept.stepEvents.getJSONObject(0).getInt("distance") == 10;

        state.grid().addZone(ZoneType.GOAL, a1.getPosition().moved("n", 7), 2);
        assert state.grid().getZones(ZoneType.GOAL).size() == 1;
        assert state.handleSurveyZoneAction(a1, ZoneType.GOAL).equals(ActionResults.SUCCESS);
        percept = this.getPercept("A1");
        assert percept.stepEvents.length() == 1;
        assert percept.stepEvents.getJSONObject(0).getInt("distance") == 7;

        var pos = Position.of(10, 10);
        assert state.teleport("A1", pos);
        assert state.teleport("A2", pos.moved("e", a1.getVision()));
        assert state.teleport("B1", pos.moved("e", a1.getVision() + 1));
        var a2 = state.grid().entities().getByName("A2");
        var b1 = state.grid().entities().getByName("B1");
        assert state.handleSurveyTargetAction(a1, a2.getPosition()).equals(ActionResults.SUCCESS);
        assert state.handleSurveyTargetAction(a1, b1.getPosition()).equals(ActionResults.FAILED_LOCATION);
        percept = this.getPercept("A1");
        assert percept.stepEvents.length() == 1;
        assert percept.stepEvents.getJSONObject(0).getString("name").equals("A2");

        state.grid().dispensers().create(pos.moved("s", 14), "b1");
        assert state.handleSurveyDispenserAction(a1).equals(ActionResults.SUCCESS);
        percept = this.getPercept("A1");
        assert percept.stepEvents.length() == 1;
        assert percept.stepEvents.getJSONObject(0).getInt("distance") == 14;
    }

    @org.junit.Test
    public void testArea() {
        var area = Position.of(10, 10).spanArea(2);
        assert(area.size() == 13);
        assert(area.contains(Position.of(10, 10)));
        assert(area.contains(Position.of(10, 11)));
        assert(area.contains(Position.of(10, 12)));
        assert(area.contains(Position.of(10, 9)));
        assert(area.contains(Position.of(10, 8)));
        assert(area.contains(Position.of(11, 10)));
        assert(area.contains(Position.of(12, 10)));
        assert(area.contains(Position.of(9, 10)));
        assert(area.contains(Position.of(8, 10)));
        assert(area.contains(Position.of(9, 9)));
        assert(area.contains(Position.of(9, 11)));
        assert(area.contains(Position.of(11, 11)));
        assert(area.contains(Position.of(11, 9)));

        assert(Position.of(0,0).spanArea(3).size() == 25);
        assert(Position.of(0,0).spanArea(1).size() == 5);
        assert(Position.of(0,0).spanArea(0).size() == 1);
    }

    @org.junit.Test
    public void testMapLooping() {
        var grid = state.grid();

        //test basics
        var pos1 = Position.wrapped(-1, -1);
        assert(pos1.equals(Position.of(grid.getDimX() - 1, grid.getDimY() - 1)));

        var area = Position.of(0,0).spanArea(1);
        assert area.contains(Position.of(0,0));
        assert area.contains(Position.of(1,0));
        assert area.contains(Position.of(0,1));
        assert area.contains(Position.of(0,grid.getDimY() - 1));
        assert area.contains(Position.of(grid.getDimX() - 1,0));

        // test moving
        var a1 = state.grid().entities().getByName("A1");
        state.teleport("A1", Position.of(0, 0));
        state.handleMoveAction(a1, List.of("w"));
        assert(a1.getPosition().equals(Position.of(grid.getDimX() - 1, 0)));
        state.handleMoveAction(a1, List.of("n"));
        assert(a1.getPosition().equals(Position.of(grid.getDimX() - 1, grid.getDimY() - 1)));

        // test clear across boundaries
        state.handleMoveAction(a1, List.of("s"));
        state.handleMoveAction(a1, List.of("e"));
        state.handleMoveAction(a1, List.of("e"));
        assert(a1.getPosition().equals(Position.of(1, 0)));
        state.grid().obstacles().create(Position.of(1, 1));
        assert state.grid().isBlocked(Position.of(1, 1));
        state.prepareStep(0);
        a1.setRole(getClearRole("testClear", 1, 5));
        var result = state.handleClearAction(a1, Position.of(0, 1));
        assert result.equals(ActionResults.SUCCESS);
        assert state.grid().isUnblocked(Position.of(0, 0));

        state.handleMoveAction(a1, List.of("w"));
        state.handleMoveAction(a1, List.of("w"));
        assert a1.getPosition().equals(Position.of(grid.getDimX() - 1, 0));

        // rotate some blocks across the map boundaries
        var blockType = state.grid().blocks().getTypes().iterator().next();
        var block = state.grid().blocks().create(Position.of(0, 0), blockType);
        var b2 = state.grid().blocks().create(Position.of(0, grid.getDimY() - 1), blockType);
        var b3 = state.grid().blocks().create(Position.of(grid.getDimX() - 1, grid.getDimY() - 1), blockType);
        var b4 = state.grid().blocks().create(Position.of(0, grid.getDimY() - 2), blockType);
        assert state.handleAttachAction(a1, "e").equals(ActionResults.SUCCESS);
        assert state.grid().attach(block, b2);
        assert state.grid().attach(b2, b3);
        assert state.grid().attach(b2, b4);

        assert state.handleRotateAction(a1, false).equals(ActionResults.SUCCESS);
        assert block.getPosition().equals(Position.of(grid.getDimX() - 1, grid.getDimY() - 1));

        var blocks = Arrays.asList(block, b2, b3, b4);
        var positions = new HashMap<Block, Position>();
        for (var b: blocks) positions.put(b, b.getPosition());

        for (var i = 0; i < 3; i++) {
            assert state.handleRotateAction(a1, true).equals(ActionResults.SUCCESS);
            for (var b: blocks) assert !b.getPosition().equals(positions.get(b));
        }
        assert state.handleRotateAction(a1, true).equals(ActionResults.SUCCESS);
        for (var b: blocks) assert b.getPosition().equals(positions.get(b));
    }

    @org.junit.Test
    public void testSnapshot() {
        this.moveAgentsToStandardPositions();
        var grid = state.grid();
        for (int i = 0; i < 10; i++) {
            grid.obstacles().create(Position.of(17, i));
        }

        var snapshot = state.takeSnapshot();

        var entities = snapshot.getJSONArray("entities");
        assert entities.length() == 2 * agents;
        var jsonEntity = entities.getJSONObject(0);
        var entity = state.grid().entities().getByName(jsonEntity.getString("name"));
        assert entity != null;

        var obstacles = snapshot.getJSONArray("obstacles");
        assert obstacles.length() == 10;
    }

    private void moveAgentsToStandardPositions() {
        state.teleport("A1", Position.of(0, 0));
        state.teleport("A2", Position.of(1, 0));
        state.teleport("B1", Position.of(2, 0));
        state.teleport("B1", Position.of(3, 0));
    }

    private StepPercept getPercept(String agent) {
        return new StepPercept(state.getStepPerceptsAndCleanUp().get(agent).toJson().getJSONObject("content"));
    }

    private Role getClearRole(String name, double chance, int maxDistance) {
        return new Role(name, maxDistance, Set.of("clear"), new int[]{1}, chance, maxDistance);
    }
}
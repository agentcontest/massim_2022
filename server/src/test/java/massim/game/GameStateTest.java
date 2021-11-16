package massim.game;

import massim.config.TeamConfig;
import massim.game.environment.positionable.Block;
import massim.game.environment.positionable.Entity;
import massim.game.environment.zones.ZoneType;
import massim.protocol.data.Position;
import massim.protocol.messages.scenario.ActionResults;
import massim.protocol.messages.scenario.StepPercept;
import massim.util.RNG;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.*;
import java.util.List;

public class GameStateTest {

    private GameState state;

    @org.junit.Before
    public void setUp() {
        RNG.initialize(17);

        var agents = 2;

        JSONObject config = new JSONObject()
                .put("steps", 500)
                .put("randomFail", 1)
                .put("entities", new JSONObject().put("standard", agents))
                .put("clusterBounds", new JSONArray().put(1).put(3))
                .put("clearEnergyCost", 2)
                .put("clearDamage", new JSONArray("[32, 16, 8, 4, 2, 1]"))
                .put("deactivatedDuration", 4)
                .put("stepRecharge", 1)
                .put("refreshEnergy", 50)
                .put("maxEnergy", 100)
                .put("attachLimit", 10)
                .put("grid", new JSONObject()
                        .put("height", 100)
                        .put("width", 100)
                        .put("instructions", new JSONArray())
                        .put("goals", new JSONObject()
                                .put("number", 0)
                                .put("size", new JSONArray().put(1).put(2))
                                .put("moveProbability", .1)
                        )
                        .put("roleZones", new JSONObject()
                                .put("number", 0)
                                .put("size", new JSONArray().put(1).put(2))
                        )
                )
                .put("blockTypes", new JSONArray().put(3).put(3))
                .put("dispensers", new JSONArray().put(0).put(0))
                .put("tasks", new JSONObject()
                        .put("size", new JSONArray().put(2).put(4))
                        .put("maxDuration", new JSONArray().put(100).put(200))
                        .put("iterations", new JSONArray().put(5).put(10))
                        .put("concurrent", 2)
                )
                .put("events", new JSONObject()
                        .put("chance", 0)
                        .put("radius", new JSONArray().put(3).put(5))
                        .put("warning", 5)
                        .put("create", new JSONArray().put(-3).put(1))
                        .put("perimeter", 2)
                )
                .put("roles", new JSONArray()
                        .put(new JSONObject()
                                .put("name", "worker")
                                .put("vision", 5)
                                .put("actions", new JSONArray(List.of("skip", "move", "rotate", "adopt", "request", "attach", "detach", "connect", "disconnect", "submit")))
                                .put("speed", new JSONArray(List.of(1, 1, 0)))
                        )
                        .put(new JSONObject()
                                .put("name", "constructor")
                                .put("vision", 5)
                                .put("actions", new JSONArray(List.of("skip", "move", "rotate", "adopt", "request", "attach", "detach", "connect", "disconnect", "submit")))
                                .put("speed", new JSONArray(List.of(1)))
                        )
                        .put(new JSONObject()
                                .put("name", "explorer")
                                .put("vision", 7)
                                .put("actions", new JSONArray(List.of("skip", "move", "rotate", "adopt", "attach", "detach", "survey")))
                                .put("speed", new JSONArray(List.of(2, 0)))
                        )
                        .put(new JSONObject()
                                .put("name", "digger")
                                .put("vision", 5)
                                .put("actions", new JSONArray(List.of("skip", "move", "rotate", "adopt", "detach", "clear")))
                                .put("speed", new JSONArray(List.of(1, 0)))
                        )
                )
                .put("regulation", new JSONObject()
                        .put("simultaneous", 0)
                        .put("chance", 0)
                        .put("subjects", new JSONArray())
                );

        var teams = new HashSet<TeamConfig>();
        List.of("A", "B").forEach(teamName -> {
            var team = new TeamConfig(teamName);
            for (var i = 1; i <= agents; i++)
                team.addAgent(teamName + i, "1");
            teams.add(team);
        });

        state = new GameState(config, teams);
    }

    @org.junit.Test
    public void handleRequestAction() {
        var blockTypes = state.getGrid().blocks().getTypes();
        var dispenserPos = Position.of(3, 3);
        Entity a1 = state.getGrid().entities().getByName("A1");
        assert a1 != null;
        assert state.createDispenser(dispenserPos, blockTypes.iterator().next());
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
        assert state.createDispenser(a1.getPosition().moved("e", 1), blockTypes.iterator().next());
        assert state.handleRequestAction(a1, "e").equals(ActionResults.SUCCESS);
    }

    @org.junit.Test
    public void taskSubmissionWorks() {
        var a1 = state.getGrid().entities().getByName("A1");
        var a2 = state.getGrid().entities().getByName("A2");
        state.getGrid().addZone(ZoneType.GOAL, Position.of(15, 15), 1);
        assert state.teleport(a1.getAgentName(), Position.of(15,15));
        assert state.teleport(a2.getAgentName(), Position.of(15, 18));
        String blockType = state.getGrid().blocks().getTypes().iterator().next();
        Block b1 = state.getGrid().blocks().create(Position.of(15,16), blockType);
        assert b1 != null;
        Block b2 =  state.getGrid().blocks().create(Position.of(15,17), blockType);
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
        var a1 = state.getGrid().entities().getByName("A1");
        var a2 = state.getGrid().entities().getByName("A2");
        assert state.teleport("A1", Position.of(20, 2));
        assert state.teleport("A2", Position.of(20, 3));
        assert(a1.getPosition().equals(Position.of(20, 2)));
        assert(a2.getPosition().equals(Position.of(20, 3)));

        var block = state.getGrid().blocks().create(Position.of(20, 4), "b1");
        assert(block != null);

        assert state.handleAttachAction(a1, "s").equals(ActionResults.SUCCESS);
        assert state.handleAttachAction(a2, "s").equals(ActionResults.SUCCESS);

        var percept = new StepPercept(state.getStepPercepts().get(a1.getAgentName()).toJson().getJSONObject("content"));
        assert(percept.attachedThings.contains(a2.getPosition().relativeTo(a1.getPosition())));
        assert(percept.attachedThings.contains(block.getPosition().relativeTo(a1.getPosition())));
    }

    @org.junit.Test
    public void clearArea() {
        var a1 = state.getGrid().entities().getByName("A1");
        state.teleport("A1", Position.of(10, 10));
        var block1 = state.getGrid().blocks().create(Position.of(10,11), "b1");
        var block2 = state.getGrid().blocks().create(Position.of(10,12), "b1");
        state.getGrid().obstacles().create(Position.of(11, 10));

        var result = state.clearArea(Position.of(10,10), 1, 1000, true);

        assert(a1.isDeactivated());
        assert(state.getGrid().attachables().lookup(block1.getPosition()).size() == 0);
        assert(state.getGrid().attachables().lookup(block2.getPosition()).size() == 1);
        assert state.getGrid().isUnblocked(Position.of(11, 10));
        assert(result == 2);
    }

    @org.junit.Test
    public void handleClearAction() {
        var a1 = state.getGrid().entities().getByName("A1");
        var a2 = state.getGrid().entities().getByName("A2");
        var posA2 = Position.of(21, 20);
        state.teleport(a1.getAgentName(), Position.of(20, 20));
        state.teleport(a2.getAgentName(), posA2);
        var block = state.getGrid().blocks().create(Position.of(22, 20), "b1");
        assert block != null;

        int step = -1;

        state.prepareStep(step++);
        var result = state.handleClearAction(a1, Position.of(2, 0));
        assert(result.equals(ActionResults.SUCCESS));
        assert(!state.getGrid().attachables().lookup(block.getPosition()).contains(block));
        assert(state.getGrid().blocks().lookup(block.getPosition()) != block);

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
    public void handleDisconnectAction() {
        var a1 = state.getGrid().entities().getByName("A1");
        var a2 = state.getGrid().entities().getByName("A2");
        state.teleport(a1.getAgentName(), Position.of(10,10));
        state.teleport(a2.getAgentName(), Position.of(10,14));
        var b1 = state.getGrid().blocks().create(Position.of(10, 11), "b1");
        var b2 = state.getGrid().blocks().create(Position.of(10, 12), "b1");
        var b3 = state.getGrid().blocks().create(Position.of(10, 13), "b1");
        assert state.getGrid().attach(a1, b1);
        assert state.getGrid().attach(b1, b2);
        assert state.getGrid().attach(b2, b3);
        assert state.getGrid().attach(b3, a2);

        assert b2.collectAllAttachments().contains(b3);
        assert b3.collectAllAttachments().contains(b2);

        state.handleDisconnectAction(a1,
                b2.getPosition().relativeTo(a1.getPosition()), b3.getPosition().relativeTo(a1.getPosition()));

        assert !b2.collectAllAttachments().contains(b3);
        assert !b3.collectAllAttachments().contains(b2);
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
        var grid = state.getGrid();

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
        var a1 = state.getGrid().entities().getByName("A1");
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
        state.getGrid().obstacles().create(Position.of(1, 1));
        assert state.getGrid().isBlocked(Position.of(1, 1));
        state.prepareStep(0);
        var result = state.handleClearAction(a1, Position.of(0, 1));
        System.out.println(result);
        assert result.equals(ActionResults.SUCCESS);
        assert state.getGrid().isUnblocked(Position.of(0, 0));

        state.handleMoveAction(a1, List.of("w"));
        state.handleMoveAction(a1, List.of("w"));
        assert a1.getPosition().equals(Position.of(grid.getDimX() - 1, 0));

        // rotate some blocks across the map boundaries
        var blockType = state.getGrid().blocks().getTypes().iterator().next();
        var block = state.getGrid().blocks().create(Position.of(0, 0), blockType);
        var b2 = state.getGrid().blocks().create(Position.of(0, grid.getDimY() - 1), blockType);
        var b3 = state.getGrid().blocks().create(Position.of(grid.getDimX() - 1, grid.getDimY() - 1), blockType);
        var b4 = state.getGrid().blocks().create(Position.of(0, grid.getDimY() - 2), blockType);
        assert state.handleAttachAction(a1, "e").equals(ActionResults.SUCCESS);
        assert state.getGrid().attach(block, b2);
        assert state.getGrid().attach(b2, b3);
        assert state.getGrid().attach(b2, b4);

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

    private void moveAgentsToStandardPositions() {
        state.teleport("A1", Position.of(0, 0));
        state.teleport("A2", Position.of(1, 0));
        state.teleport("B1", Position.of(2, 0));
        state.teleport("B1", Position.of(3, 0));
    }

    @org.junit.Test
    public void snapshotComplete() {
        this.moveAgentsToStandardPositions();
        var grid = state.getGrid();
        grid.obstacles().create(Position.of(17,17));

        var snapshot = state.takeSnapshot();
        var entities = snapshot.getJSONArray("entities");
        assert entities.length() == 4;
    }
}
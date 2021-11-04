package massim.game;

import massim.config.TeamConfig;
import massim.game.environment.Block;
import massim.game.environment.Terrain;
import massim.protocol.data.Position;
import massim.protocol.data.Thing;
import massim.protocol.messages.scenario.ActionResults;
import massim.protocol.messages.scenario.StepPercept;
import massim.util.RNG;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.*;

public class GameStateTest {

    private GameState state;

    @org.junit.Before
    public void setUp() {
        RNG.initialize(17);

        JSONObject config = new JSONObject()
                .put("steps", 500)
                .put("randomFail", 1)
                .put("entities", new JSONObject().put("standard", 10))
                .put("clusterBounds", new JSONArray().put(1).put(3))
                .put("clearSteps", 3)
                .put("clearEnergyCost", 30)
                .put("disableDuration", 4)
                .put("maxEnergy", 300)
                .put("attachLimit", 10)
                .put("grid", new JSONObject()
                        .put("height", 100)
                        .put("width", 100)
                        .put("instructions", new JSONArray())
                        .put("goals", new JSONObject()
                                .put("number", 0)
                                .put("size", new JSONArray().put(1).put(2))
                        )
                )
                .put("blockTypes", new JSONArray().put(3).put(3))
                .put("dispensers", new JSONArray().put(5).put(10))
                .put("tasks", new JSONObject()
                        .put("size", new JSONArray().put(2).put(4))
                        .put("duration", new JSONArray().put(100).put(200))
                        .put("probability", 0.05)
                        .put("taskboards", 5)
                        .put("rewardDecay", new JSONArray().put(1).put(5))
                        .put("lowerRewardLimit", 10)
                        .put("distanceToTaskboards", 10)
                )
                .put("events", new JSONObject()
                        .put("chance", 15)
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
                ;

        var team = new TeamConfig("A");
        for (var i = 1; i <= 10; i++) team.addAgent("A" + i, "1");
        state = new GameState(config, Set.of(team));
    }

    @org.junit.Test
    public void handleRequestAction() {
        var blockTypes = state.getBlockTypes();
        var dispenserPos = Position.of(3, 3);
        Entity a1 = state.getEntityByName("A1");
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
        var a1 = state.getEntityByName("A1");
        state.setTerrain(Position.of(15,15), Terrain.GOAL);
        assert state.teleport("A1", Position.of(15,15));
        String blockType = state.getBlockTypes().iterator().next();
        assert state.createBlock(Position.of(15,16), blockType) != null;
        assert state.createBlock(Position.of(14,16), blockType) != null;
        assert state.createTask("testTask1", 10,
                Map.of(Position.of(0, 1), blockType, Position.of(-1, 1), blockType)) != null;
        assert state.attach(Position.of(15,15), Position.of(15,16));
        assert state.attach(Position.of(15,16), Position.of(14,16));
        assert state.handleSubmitAction(a1, "testTask1").equals(ActionResults.SUCCESS);
    }

    @org.junit.Test
    public void getStepPercepts() {
        var a1 = state.getEntityByName("A1");
        var a2 = state.getEntityByName("A2");
        state.teleport("A1", Position.of(3, 2));
        state.teleport("A2", Position.of(3, 3));
        var block = state.createBlock(Position.of(3, 4), "b1");
        assert(a1.getPosition().equals(Position.of(3, 2)));
        assert(a2.getPosition().equals(Position.of(3, 3)));
        assert(block != null);
        state.attach(a1.getPosition(), a2.getPosition());
        state.attach(a2.getPosition(), block.getPosition());

        var percept = new StepPercept(state.getStepPercepts().get("A1").toJson().getJSONObject("content"));
        assert(percept.attachedThings.contains(a2.getPosition().relativeTo(a1.getPosition())));
        assert(percept.attachedThings.contains(block.getPosition().relativeTo(a1.getPosition())));
    }

    @org.junit.Test
    public void clearArea() {
        var a1 = state.getEntityByName("A1");
        state.teleport("A1", Position.of(10, 10));
        var block1 = state.createBlock(Position.of(10,11), "b1");
        var block2 = state.createBlock(Position.of(10,12), "b1");
        state.setTerrain(Position.of(11,10), Terrain.OBSTACLE);

        var result = state.clearArea(Position.of(10,10), 1);

        assert(a1.isDisabled());
        assert(state.getThingsAt(block1.getPosition()).size() == 0);
        assert(state.getThingsAt(block2.getPosition()).size() == 1);
        assert(state.getTerrain(Position.of(11, 10)) == Terrain.EMPTY);
        assert(result == 2);
    }

    @org.junit.Test
    public void handleClearAction() {
        var a1 = state.getEntityByName("A1");
        var a2 = state.getEntityByName("A2");
        var posA2 = Position.of(22, 20);
        state.teleport(a1.getAgentName(), Position.of(20, 20));
        state.teleport("A2", posA2);
        var block = state.createBlock(Position.of(21, 20), "b1");
        assert block != null;

        assert(!a2.isDisabled());
        int i;
        for (i = 0; i < state.clearSteps - 1; i++) {
            state.prepareStep(i);
            if (i!=0) {
                var percept = (StepPercept) state.getStepPercepts().get("A1");
                assert (containsThing(percept.things, Thing.TYPE_MARKER, Position.of(2, 0)));
            }
            assert(state.handleClearAction(a1, Position.of(2, 0)).equals(ActionResults.SUCCESS));
        }
        state.prepareStep(i++);
        assert(state.handleClearAction(a1, Position.of(2, 0)).equals(ActionResults.SUCCESS));
        assert(!state.getThingsAt(block.getPosition()).contains(block));
        assert(a2.isDisabled());
        for (var j = 0; j < Entity.disableDuration; j++) {
            assert(a2.isDisabled());
            state.prepareStep(i + j);
        }
        assert(!a2.isDisabled());
    }

    @org.junit.Test
    public void handleDisconnectAction() {
        var a1 = state.getEntityByName("A1");
        var a2 = state.getEntityByName("A2");
        state.teleport(a1.getAgentName(), Position.of(10,10));
        state.teleport(a2.getAgentName(), Position.of(10,14));
        var b1 = state.createBlock(Position.of(10, 11), "b1");
        var b2 = state.createBlock(Position.of(10, 12), "b1");
        var b3 = state.createBlock(Position.of(10, 13), "b1");
        assert state.attach(a1.getPosition(), b1.getPosition());
        assert state.attach(b1.getPosition(), b2.getPosition());
        assert state.attach(b2.getPosition(), b3.getPosition());
        assert state.attach(b3.getPosition(), a2.getPosition());

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
        var a1 = state.getEntityByName("A1");
        state.teleport("A1", Position.of(0, 0));
        state.handleMoveAction(a1, List.of("w"));
        assert(a1.getPosition().equals(Position.of(grid.getDimX() - 1, 0)));
        state.handleMoveAction(a1, List.of("n"));
        assert(a1.getPosition().equals(Position.of(grid.getDimX() - 1, grid.getDimY() - 1)));

        // test clear across boundaries
        state.setTerrain(Position.of(0, 0), Terrain.OBSTACLE);
        assert state.getTerrain(Position.of(0, 0)) == Terrain.OBSTACLE;
        for (var i = 0; i < state.clearSteps; i++) {
            state.prepareStep(i);
            assert state.handleClearAction(a1, Position.of(1, 1)).equals(ActionResults.SUCCESS);
        }
        assert state.getTerrain(Position.of(0, 0)) == Terrain.EMPTY;

        state.handleMoveAction(a1, List.of("s"));
        assert a1.getPosition().equals(Position.of(grid.getDimX() - 1, 0));

        // rotate some blocks across the map boundaries
        var blockType = state.getBlockTypes().iterator().next();
        var block = state.createBlock(Position.of(0, 0), blockType);
        var b2 = state.createBlock(Position.of(0, grid.getDimY() - 1), blockType);
        var b3 = state.createBlock(Position.of(grid.getDimX() - 1, grid.getDimY() - 1), blockType);
        var b4 = state.createBlock(Position.of(0, grid.getDimY() - 2), blockType);
        assert state.handleAttachAction(a1, "e").equals(ActionResults.SUCCESS);
        assert state.attach(block.getPosition(), b2.getPosition());
        assert state.attach(b2.getPosition(), b3.getPosition());
        assert state.attach(b2.getPosition(), b4.getPosition());

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

    private static boolean containsThing(Collection<Thing> things, String type, Position pos) {
        return things.stream().anyMatch(t -> t.type.equals(type) && t.x == pos.x && t.y == pos.y);
    }
}
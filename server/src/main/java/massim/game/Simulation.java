package massim.game;

import massim.config.TeamConfig;
import massim.game.environment.Positionable;
import massim.game.environment.zones.ZoneType;
import massim.protocol.data.Position;
import massim.protocol.messages.ActionMessage;
import massim.protocol.messages.RequestActionMessage;
import massim.protocol.messages.SimEndMessage;
import massim.protocol.messages.SimStartMessage;
import massim.game.environment.Grid;
import massim.util.RNG;
import massim.util.Util;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static massim.protocol.messages.scenario.Actions.*;
import static massim.protocol.messages.scenario.ActionResults.*;

public class Simulation {

    private String name;
    private GameState state;
    private int steps;

    public Map<String, SimStartMessage> init(int steps, JSONObject config, Set<TeamConfig> matchTeams) {
        this.steps = steps;
        this.state = new GameState(config, matchTeams);
        this.name = System.currentTimeMillis() + "_" + matchTeams.stream()
                .map(TeamConfig::getName)
                .collect(Collectors.joining("_"));
        return state.getInitialPercepts(steps);
    }

    public Map<String, RequestActionMessage> preStep(int step) {
        return state.prepareStep(step);
    }

    public void step(int stepNo, Map<String, ActionMessage> actionMap) {
        handleActions(actionMap);
    }

    public Map<String, SimEndMessage> finish() {
        return state.getFinalPercepts();
    }

    public JSONObject getResult() {
        return state.getResult();
    }

    public String getName() {
        return name;
    }

    public JSONObject getSnapshot() {
        return state.takeSnapshot();
    }

    public JSONObject getStatusSnapshot() {
        JSONObject snapshot = state.takeStatusSnapshot();
        snapshot.put("sim", name);
        snapshot.put("steps", steps);
        return snapshot;
    }

    public JSONObject getStaticData() {
        var grid = new JSONObject();
        grid.put("width", this.state.getGrid().getDimX());
        grid.put("height", this.state.getGrid().getDimY());

        var teams = new JSONObject();
        for (var entry: this.state.getTeams().entrySet()) {
            var team = new JSONObject();
            team.put("name", entry.getValue().getName());
            teams.put(entry.getKey(), team);
        }

        var blockTypes = new JSONArray();
        for (var type: this.state.getBlockTypes()) {
            blockTypes.put(type);
        }

        var world = new JSONObject();
        world.put("sim", name);
        world.put("grid", grid);
        world.put("teams", teams);
        world.put("blockTypes", this.state.getBlockTypes());
        world.put("steps", steps);
        return world;
    }

    public void handleCommand(String[] command) {}

    /**
     * Executes all actions in random order.
     */
    private void handleActions(Map<String, ActionMessage> actions) {
        var entities = actions.keySet().stream().map(ag -> state.getEntityByName(ag)).collect(Collectors.toList());
        RNG.shuffle(entities);
        for (Entity entity : entities) {
            var action = actions.get(entity.getAgentName());
            entity.setNewAction(action);
            if (entity.isDisabled()) {
                entity.setLastActionResult(FAILED_STATUS);
            }
            else if (RNG.nextInt(100) < state.getRandomFail()) {
                entity.setLastActionResult(FAILED_RANDOM);
            }
        }

        var previousPositions = entities.stream().collect(
                Collectors.toMap(Positionable::getPosition, e -> e));

        for (Entity entity : entities) {
            if (!entity.getLastActionResult().equals(UNPROCESSED)) continue;
            var params = entity.getLastActionParams();
            var action = entity.getLastAction();

            if (!entity.isActionAvailable(action)) {
                entity.setLastActionResult(FAILED_ROLE);
                continue;
            }

            switch(action) {
                case NO_ACTION:
                case SKIP:
                    entity.setLastActionResult(SUCCESS);
                    continue;

                case MOVE:
                    if (params.isEmpty())
                        entity.setLastActionResult(FAILED_PARAMETER);
                    else
                        entity.setLastActionResult(state.handleMoveAction(entity, params));
                    continue;

                case ATTACH:
                    var direction = getStringParam(params, 0);
                    if (!Grid.DIRECTIONS.contains(direction)) {
                        entity.setLastActionResult(FAILED_PARAMETER);
                    } else {
                        entity.setLastActionResult(state.handleAttachAction(entity, direction));
                    }
                    continue;

                case DETACH:
                    direction = getStringParam(params, 0);
                    if (!Grid.DIRECTIONS.contains(direction)) {
                        entity.setLastActionResult(FAILED_PARAMETER);
                    } else {
                        entity.setLastActionResult(state.handleDetachAction(entity, direction));
                    }
                    continue;

                case ROTATE:
                    direction = getStringParam(params, 0);
                    if (!Grid.ROTATION_DIRECTIONS.contains(direction)) {
                        entity.setLastActionResult(FAILED_PARAMETER);
                        continue;
                    }
                    entity.setLastActionResult(state.handleRotateAction(entity, "cw".equals(direction)));
                    continue;

                case CONNECT:
                    var partnerEntityName = getStringParam(params, 0);
                    var partnerEntity = state.getEntityByName(partnerEntityName);
                    var x = getIntParam(params, 1);
                    var y = getIntParam(params, 2);
                    if (partnerEntity == null || x == null || y == null) {
                        entity.setLastActionResult(FAILED_PARAMETER);
                        continue;
                    }
                    var partnerAction = actions.get(partnerEntityName);
                    if (partnerAction == null) {
                        entity.setLastActionResult(FAILED_PARTNER);
                        continue;
                    }
                    var partnerParams = partnerAction.getParams();
                    var px = getIntParam(partnerParams, 1);
                    var py = getIntParam(partnerParams, 2);
                    if (!partnerEntity.getLastAction().equals(CONNECT)
                            || !partnerEntity.getLastActionResult().equals(UNPROCESSED)
                            || !entity.getAgentName().equals(getStringParam(partnerParams, 0))) {
                        entity.setLastActionResult(FAILED_PARTNER);
                        continue;
                    }
                    if (px == null || py == null) {
                        entity.setLastActionResult(FAILED_PARTNER);
                        partnerEntity.setLastActionResult(FAILED_PARAMETER);
                        continue;
                    }
                    var result = state.handleConnectAction(entity, Position.of(x, y), partnerEntity, Position.of(px, py));
                    entity.setLastActionResult(result);
                    partnerEntity.setLastActionResult(result);
                    continue;

                case REQUEST:
                    direction = getStringParam(params, 0);
                    if (!Grid.DIRECTIONS.contains(direction)) {
                        entity.setLastActionResult(FAILED_PARAMETER);
                    } else {
                        entity.setLastActionResult(state.handleRequestAction(entity, direction));
                    }
                    continue;

                case SUBMIT:
                    var taskName = getStringParam(params, 0);
                    entity.setLastActionResult(state.handleSubmitAction(entity, taskName));
                    continue;

                case CLEAR:
                    x = getIntParam(params, 0);
                    y = getIntParam(params, 1);
                    if (x == null || y == null) entity.setLastActionResult(FAILED_PARAMETER);
                    else {
                        entity.setLastActionResult(state.handleClearAction(entity, Position.of(x, y)));
                    }
                    continue;

                case DISCONNECT:
                    var x1 = getIntParam(params, 0);
                    var y1 = getIntParam(params, 1);
                    var x2 = getIntParam(params, 2);
                    var y2 = getIntParam(params, 3);
                    if (x1 == null || y1 == null || x2 == null || y2 == null) {
                        entity.setLastActionResult(FAILED_PARAMETER);
                        continue;
                    }
                    entity.setLastActionResult(
                            state.handleDisconnectAction(entity, Position.of(x1, y1), Position.of(x2, y2)));
                    continue;

                case SURVEY:
                    if (params.size() == 1) {
                        var searchTarget = getStringParam(params, 0);
                        if (searchTarget == null)
                            entity.setLastActionResult(FAILED_PARAMETER);
                        else {
                            switch (searchTarget) {
                                case "dispenser" -> entity.setLastActionResult(state.handleSurveyDispenserAction(entity));
                                case "goal" -> entity.setLastActionResult(state.handleSurveyZoneAction(entity, ZoneType.GOAL));
                                case "role" -> entity.setLastActionResult(state.handleSurveyZoneAction(entity, ZoneType.ROLE));
                                default -> entity.setLastActionResult(FAILED_PARAMETER);
                            }
                        }
                    }
                    else if (params.size() == 2) {
                        x = getIntParam(params, 0);
                        y = getIntParam(params, 1);
                        if (x == null || y == null)
                            entity.setLastActionResult(FAILED_PARAMETER);
                        else {
                            var targetEntity = previousPositions.get(Position.of(x, y));
                            if (targetEntity == null) {
                                entity.setLastActionResult(FAILED_TARGET);
                            }
                            else {
                                entity.setLastActionResult(state.handleSurveyTargetAction(entity, targetEntity));
                            }
                        }
                    }
                    else
                        entity.setLastActionResult(FAILED_PARAMETER);
                    continue;

                case ADOPT:
                    var roleName = getStringParam(params, 0);
                    entity.setLastActionResult(state.handleAdoptAction(entity, roleName));
                    continue;

                default:
                    entity.setLastActionResult(UNKNOWN_ACTION);
            }
        }
    }

    /**
     * @return the integer parameter at the given index or null if there is no such parameter
     */
    static Integer getIntParam(List<String> params, int index) {
        if (index >= params.size()) return null;
        return Util.tryParseInt(params.get(index));
    }

    /**
     * @return the string parameter at the given index or null if there is no such parameter
     */
    static String getStringParam(List<String> params, int index) {
        if (index >= params.size()) return null;
        try {
            return params.get(index);
        } catch (IndexOutOfBoundsException e) {
            return null;
        }
    }
}

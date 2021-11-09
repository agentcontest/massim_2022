package massim.game;

import massim.config.TeamConfig;
import massim.game.environment.*;
import massim.game.environment.zones.ZoneType;
import massim.protocol.data.Position;
import massim.protocol.data.Role;
import massim.protocol.data.Thing;
import massim.protocol.messages.RequestActionMessage;
import massim.protocol.messages.SimEndMessage;
import massim.protocol.messages.SimStartMessage;
import massim.protocol.messages.scenario.ActionResults;
import massim.protocol.messages.scenario.Actions;
import massim.protocol.messages.scenario.InitialPercept;
import massim.protocol.messages.scenario.StepPercept;
import massim.protocol.util.JSONUtil;
import massim.util.*;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static massim.protocol.messages.scenario.ActionResults.*;


/**
 * State of the game.
 */
class GameState {

    private final Map<String, Team> teams = new HashMap<>();
    private final Map<String, Entity> agentToEntity = new HashMap<>();
    private final Map<Entity, String> entityToAgent = new HashMap<>();

    private int step = -1;
    private final int teamSize;

    // static env. things
    private final Grid grid;
    private final Map<Position, Dispenser> dispensers = new HashMap<>();
    private final Set<String> blockTypes = new TreeSet<>();
    private final Map<String, Role> roles = new HashMap<>();

    // dynamic env. things
    private final Map<Integer, GameObject> gameObjects = new HashMap<>();
    private final Map<String, Task> tasks = new HashMap<>();
    private final Set<ClearEvent> clearEvents = new HashSet<>();

    // config parameters
    private final int randomFail;
    private final double pNewTask;
    private final Bounds taskDurationBounds;
    private final Bounds taskSizeBounds;
    private final Bounds eventRadiusBounds;
    private final Bounds eventCreateBounds;
    private final int eventChance;
    private final int eventWarning;
    private final int eventCreatePerimeter;
    private final int[] clearDamage;

    private final Map<String, JSONArray> stepEvents = new HashMap<>();

    private JSONArray logEvents = new JSONArray();

    GameState(JSONObject config, Set<TeamConfig> matchTeams) {
        this.randomFail = ConfigUtil.getInt(config, "randomFail");
        int attachLimit = ConfigUtil.getInt(config, "attachLimit");
        var clusterSizes = ConfigUtil.getBounds(config, "clusterBounds");

        Entity.clearEnergyCost = ConfigUtil.getInt(config, "clearEnergyCost");
        Entity.deactivatedDuration = ConfigUtil.getInt(config, "deactivatedDuration");
        Entity.maxEnergy = ConfigUtil.getInt(config, "maxEnergy");
        Entity.stepRecharge = ConfigUtil.getInt(config, "stepRecharge");

        var blockTypeBounds = ConfigUtil.getBounds(config, "blockTypes");
        var numberOfBlockTypes = RNG.betweenClosed(blockTypeBounds.lower(), blockTypeBounds.upper());
        for (int i = 0; i < numberOfBlockTypes; i++) blockTypes.add("b" + i);

        var dispenserBounds = ConfigUtil.getBounds(config, "dispensers");

        clearDamage = JSONUtil.getIntArray(config, "clearDamage");

        var taskConfig = config.getJSONObject("tasks");
        this.taskDurationBounds = ConfigUtil.getBounds(taskConfig, "duration");
        this.taskSizeBounds = ConfigUtil.getBounds(taskConfig, "size");
        this.pNewTask = ConfigUtil.getDouble(taskConfig, "probability");

        var eventConfig = config.getJSONObject("events");
        this.eventChance = ConfigUtil.getInt(eventConfig, "chance");
        this.eventRadiusBounds = ConfigUtil.getBounds(eventConfig, "radius");
        this.eventWarning = ConfigUtil.getInt(eventConfig, "warning");
        this.eventCreateBounds = ConfigUtil.getBounds(eventConfig, "create");
        this.eventCreatePerimeter = ConfigUtil.getInt(eventConfig, "perimeter");

        // create teams
        matchTeams.forEach(team -> teams.put(team.getName(), new Team(team.getName())));

        // create grid environment
        grid = new Grid(config.getJSONObject("grid"), attachLimit);

        var defaultRole = parseRoles(config);

        // create entities
        var entities = config.getJSONObject("entities");
        var it = entities.keys();
        int agentCounter = 0;
        while (it.hasNext()) {
            var numberOfAgents = entities.getInt(it.next());
            List<Integer> agentsRange = IntStream.rangeClosed(0, numberOfAgents-1).boxed().collect(Collectors.toList());
            while (!agentsRange.isEmpty()) {
                int clusterSize = Math.min(RNG.betweenClosed(clusterSizes), agentsRange.size());
                ArrayList<Position> cluster = grid.findRandomFreeClusterPosition(clusterSize);
                for (Position p : cluster) {
                    int index = agentsRange.remove(RNG.nextInt(agentsRange.size()));
                    for (TeamConfig team: matchTeams) {
                        createEntity(p, team.getAgentNames().get(index), team.getName(), defaultRole);
                    }
                    agentCounter++;
                    if (agentCounter == numberOfAgents) break;
                }
            }
        }
        teamSize = agentCounter;

        // create env. things
        for (var block : blockTypes) {
            var numberOfDispensers = RNG.betweenClosed(dispenserBounds);
            for (var i = 0; i < numberOfDispensers; i++) {
                createDispenser(grid.findRandomFreePosition(), block);
            }
        }

        // check for setup file
        var setupFilePath = config.optString("setup");
        if (!setupFilePath.equals("")){
            Log.log(Log.Level.NORMAL, "Running setup actions");
            try (var b = new BufferedReader(new FileReader(setupFilePath))){
                var line = "";
                while ((line = b.readLine()) != null) {
                    if (line.startsWith("#") || line.isEmpty()) continue;
                    if (line.startsWith("stop")) break;
                    handleCommand(line.split(" "));
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private Role parseRoles(JSONObject config) {
        Role defaultRole = null;
        JSONArray rolesData = config.getJSONArray("roles");
        for (int i = 0; i < rolesData.length(); i++) {
            var roleData = rolesData.getJSONObject(i);
            var role = new Role(
                    roleData.getString("name"),
                    roleData.getInt("vision"),
                    JSONUtil.arrayToStringSet(roleData.getJSONArray("actions")),
                    JSONUtil.arrayToIntArray(roleData.getJSONArray("speed"))
                    );
            this.roles.put(role.name(), role);
            Log.log(Log.Level.NORMAL, "Role " + role.name() + " added.");
            if (i == 0) {
                defaultRole = role;
            }
        }
        return defaultRole;
    }

    Map<String, Team> getTeams() {
        return this.teams;
    }

    public Set<String> getBlockTypes() {
        return this.blockTypes;
    }

    private void handleCommand(String[] command) {
        if (command.length == 0) return;

        switch (command[0]) {
            case "move":
                if (command.length != 4) break;
                var x = Util.tryParseInt(command[1]);
                var y = Util.tryParseInt(command[2]);
                var entity = agentToEntity.get(command[3]);

                if (entity == null || x == null || y == null) break;
                Log.log(Log.Level.NORMAL, "Setup: Try to move " + command[3] + " to (" + x +", " + y + ")");
                grid.moveWithoutAttachments(entity, Position.of(x, y));
                break;

            case "add":
                if (command.length < 4 || command.length > 5) break;
                x = Util.tryParseInt(command[1]);
                y = Util.tryParseInt(command[2]);
                if (x == null || y == null) break;
                switch (command[3]) {
                    case "block":
                        var blockType = command[4];
                        createBlock(Position.of(x, y), blockType);
                        break;
                    case "dispenser":
                        blockType = command[4];
                        createDispenser(Position.of(x, y), blockType);
                        break;
                    default:
                        Log.log(Log.Level.ERROR, "Cannot add " + command[3]);
                }
                break;

            case "create":
                if (command.length != 5) break;
                if (command[1].equals("task")) {
                    var name = command[2];
                    var duration = Util.tryParseInt(command[3]);
                    var requirements = command[4].split(";");
                    if (duration == null) break;
                    var requireMap = new HashMap<Position, String>();
                    Arrays.stream(requirements).map(req -> req.split(",")).forEach(req -> {
                        var bx = Util.tryParseInt(req[0]);
                        var by = Util.tryParseInt(req[1]);
                        var blockType = req[2];
                        if (bx != null && by != null) {
                            requireMap.put(Position.of(bx, by), blockType);
                        }
                    });
                    createTask(name, duration, requireMap);
                }
                break;

            case "attach":
                if (command.length != 5) break;
                var x1 = Util.tryParseInt(command[1]);
                var y1 = Util.tryParseInt(command[2]);
                var x2 = Util.tryParseInt(command[3]);
                var y2 = Util.tryParseInt(command[4]);
                if (x1 == null || x2 == null || y1 == null || y2 == null) break;
                Attachable a1 = getUniqueAttachable(Position.of(x1, y1));
                Attachable a2 = getUniqueAttachable(Position.of(x2, y2));
                if (a1 == null || a2 == null) break;
                grid.attach(a1, a2);
                break;

            case "terrain":
                if (command.length != 4) break;
                x = Util.tryParseInt(command[1]);
                y = Util.tryParseInt(command[2]);
                var type = command[3];
                if (x == null || y == null || type.isEmpty()) break;
                if (type.equalsIgnoreCase("obstacle")) grid.addObstacle(Position.of(x, y));
                else if (type.equalsIgnoreCase("goal")) grid.addZone(ZoneType.GOAL, Position.of(x, y), 1);
                break;

            default:
                Log.log(Log.Level.ERROR, "Cannot handle command " + Arrays.toString(command));
        }
    }

    int getRandomFail() {
        return this.randomFail;
    }

    public Grid getGrid() {
        return grid;
    }

    Entity getEntityByName(String agentName) {
        return agentToEntity.get(agentName);
    }

    Map<String, SimStartMessage> getInitialPercepts(int steps) {
        Map<String, SimStartMessage> result = new HashMap<>();
        for (Entity e: entityToAgent.keySet()) {
            result.put(e.getAgentName(),
                    new InitialPercept(e.getAgentName(), e.getTeamName(), teamSize, steps, roles.values()));
        }
        return result;
    }

    Map<String, RequestActionMessage> prepareStep(int step) {
        this.step = step;

        logEvents = new JSONArray();

        grid.deleteMarkers();

        //handle tasks
        if (RNG.nextDouble() < pNewTask) {
            createTask(RNG.betweenClosed(taskDurationBounds), RNG.betweenClosed(taskSizeBounds));
        }

        //handle entities
        agentToEntity.values().forEach(Entity::preStep);

        //handle (map) events
        if (RNG.nextInt(100) < eventChance) {
            clearEvents.add(new ClearEvent(grid.getRandomPosition(), step + eventWarning,
                    RNG.betweenClosed(eventRadiusBounds)));
        }
        var processedEvents = new HashSet<ClearEvent>();
        for (var event: clearEvents) {
            if (event.getStep() == step) {
                processEvent(event);
                processedEvents.add(event);
            }
            else {
                var type = event.getStep() - step <= 2? Marker.Type.CLEAR_IMMEDIATE : Marker.Type.CLEAR;
                var clearArea = event.getPosition().spanArea(event.getRadius());
                var clearPerimeter = event.getPosition().spanArea(event.getRadius() + eventCreatePerimeter);
                clearPerimeter.removeAll(clearArea);
                for (Position pos: clearArea) grid.createMarker(pos, type);
                for (Position pos: clearPerimeter) grid.createMarker(pos, Marker.Type.CLEAR_PERIMETER);
            }
        }
        clearEvents.removeAll(processedEvents);

        return this.getStepPerceptsAndCleanUp();
    }

    Map<String, RequestActionMessage> getStepPerceptsAndCleanUp() {
        var result = this.getStepPercepts();
        this.stepEvents.clear();
        return result;
    }

    private void processEvent(ClearEvent event) {
        var removed = clearArea(event.getPosition(), event.getRadius(), 1000, true);
        var distributeNew = RNG.betweenClosed(eventCreateBounds) + removed;

        for (var i = 0; i < distributeNew; i++) {
            var pos = grid.findRandomFreePosition(event.getPosition(),eventCreatePerimeter + event.getRadius());
            if(pos != null && dispensers.get(pos) == null) {
                grid.addObstacle(pos);
            }
        }
    }

    Map<String, RequestActionMessage> getStepPercepts(){
        Map<String, RequestActionMessage> result = new HashMap<>();
        var allTasks = tasks.values().stream()
                .filter(t -> !t.isCompleted())
                .map(Task::toPercept)
                .collect(Collectors.toSet());
        for (Entity entity : entityToAgent.keySet()) {
            var pos = entity.getPosition();
            var visibleThings = new HashSet<Thing>();
            Set<Position> attachedThings = new HashSet<>();
            for (Position currentPos: pos.spanArea(entity.getVision())){
                getThingsAt(currentPos).forEach(go -> {
                    visibleThings.add(go.toPercept(pos));
                    if (go != entity && go instanceof Attachable && ((Attachable)go).isAttachedToAnotherEntity()){
                        attachedThings.add(go.getPosition().relativeTo(pos));
                    }
                });
                var d = dispensers.get(currentPos);
                if (d != null) visibleThings.add(d.toPercept(pos));
            }
            var percept = new StepPercept(step,
                    teams.get(entity.getTeamName()).getScore(),
                    visibleThings, allTasks,
                    entity.getLastAction(), entity.getLastActionParams(),
                    entity.getLastActionResult(), attachedThings,
                    stepEvents.get(entity.getAgentName()),
                    entity.getRole().name(),
                    entity.getEnergy(),
                    entity.isDeactivated());
            result.put(entity.getAgentName(), percept);
        }

        return result;
    }

    Map<String, SimEndMessage> getFinalPercepts() {
        var result = new HashMap<String, SimEndMessage>();
        var teamsSorted = new ArrayList<>(teams.values());
        teamsSorted.sort((t1, t2) -> (int) (t2.getScore() - t1.getScore()));
        var rankings = new HashMap<Team, Integer>();
        for (int i = 0; i < teamsSorted.size(); i++) {
            rankings.put(teamsSorted.get(i), i + 1);
        }
        for (Entity e: entityToAgent.keySet()) {
            var team = teams.get(e.getTeamName());
            result.put(e.getAgentName(), new SimEndMessage(team.getScore(), rankings.get(team)));
        }
        return result;
    }

    String handleMoveAction(Entity entity, List<String> params) {
        var directions = new ArrayList<String>();
        for (int i = 0; i < params.size(); i++) {
            var direction = Simulation.getStringParam(params, i);
            if (!Grid.DIRECTIONS.contains(direction))
                return FAILED_PARAMETER;
            directions.add(direction);
        }

        var movesTaken = 0;
        var possibleMoves = entity.getCurrentSpeed();
        for (var direction : directions) {
            if (grid.moveWithAttached(entity, direction, 1)){
                movesTaken++;
                if (movesTaken >= possibleMoves) break;
            }
            else
                break;
        }

        if (movesTaken == 0) return ActionResults.FAILED_PATH;
        else if (movesTaken < directions.size()) return PARTIAL_SUCCESS;
        else return SUCCESS;
    }

    String handleRotateAction(Entity entity, boolean clockwise) {
        if (grid.rotateWithAttached(entity, clockwise)) {
            return ActionResults.SUCCESS;
        }
        return ActionResults.FAILED;
    }

    String handleAttachAction(Entity entity, String direction) {
        Position target = entity.getPosition().moved(direction, 1);
        Attachable a = getUniqueAttachable(target);
        if (a == null) return ActionResults.FAILED_TARGET;
        if (a instanceof Entity && ofDifferentTeams(entity, (Entity) a)) {
            return ActionResults.FAILED_TARGET;
        }
        if(!attachedToOpponent(a, entity) && grid.attach(entity, a)) {
            return ActionResults.SUCCESS;
        }
        return ActionResults.FAILED;
    }

    String handleDetachAction(Entity entity, String direction) {
        Position target = entity.getPosition().moved(direction, 1);
        Attachable a = getUniqueAttachable(target);
        if (a == null) return ActionResults.FAILED_TARGET;
        if (a instanceof Entity && ofDifferentTeams(entity, (Entity) a)) {
            return ActionResults.FAILED_TARGET;
        }
        if (grid.detachNeighbors(entity, a)){
            return ActionResults.SUCCESS;
        }
        return ActionResults.FAILED;
    }

    String handleDisconnectAction(Entity entity, Position attPos1, Position attPos2) {
        var attachable1 = getUniqueAttachable(attPos1.translate(entity.getPosition()));
        var attachable2 = getUniqueAttachable(attPos2.translate(entity.getPosition()));
        if (attachable1 == null || attachable2 == null) return ActionResults.FAILED_TARGET;
        var allAttachments = entity.collectAllAttachments();
        if (!allAttachments.contains(attachable1) || !allAttachments.contains(attachable2))
            return ActionResults.FAILED_TARGET;
        if (grid.detachNeighbors(attachable1, attachable2)) return ActionResults.SUCCESS;
        return ActionResults.FAILED_TARGET;
    }

    String handleConnectAction(Entity entity, Position blockPos, Entity partnerEntity, Position partnerBlockPos) {
        Attachable block1 = getUniqueAttachable(blockPos.translate(entity.getPosition()));
        Attachable block2 = getUniqueAttachable(partnerBlockPos.translate(partnerEntity.getPosition()));

        if(!(block1 instanceof Block) || !(block2 instanceof Block)) return ActionResults.FAILED_TARGET;

        Set<Attachable> attachables = entity.collectAllAttachments();
        if (attachables.contains(partnerEntity)) return ActionResults.FAILED;
        if (!attachables.contains(block1)) return ActionResults.FAILED_TARGET;
        if (attachables.contains(block2)) return ActionResults.FAILED_TARGET;

        Set<Attachable> partnerAttachables = partnerEntity.collectAllAttachments();
        if (!partnerAttachables.contains(block2)) return ActionResults.FAILED_TARGET;
        if (partnerAttachables.contains(block1)) return ActionResults.FAILED_TARGET;

        if(grid.attach(block1, block2)){
            return ActionResults.SUCCESS;
        }
        return ActionResults.FAILED;
    }

    String handleRequestAction(Entity entity, String direction) {
        var requestPosition = entity.getPosition().moved(direction, 1);
        var dispenser = dispensers.get(requestPosition);
        if (dispenser == null) return ActionResults.FAILED_TARGET;
        if (!grid.isUnblocked(requestPosition)) return ActionResults.FAILED_BLOCKED;
        createBlock(requestPosition, dispenser.getBlockType());
        return ActionResults.SUCCESS;
    }

    String handleSubmitAction(Entity e, String taskName) {
        Task task = tasks.get(taskName);
        if (task == null || task.isCompleted() || step > task.getDeadline())
            return ActionResults.FAILED_TARGET;
        Position ePos = e.getPosition();
        if (!grid.isInZone(ZoneType.GOAL, ePos)) return ActionResults.FAILED;
        Set<Attachable> attachedBlocks = e.collectAllAttachments();
        for (Map.Entry<Position, String> entry : task.getRequirements().entrySet()) {
            var pos = entry.getKey();
            var reqType = entry.getValue();
            var checkPos = Position.wrapped(pos.x + ePos.x, pos.y + ePos.y);
            var actualBlock = getUniqueAttachable(checkPos);
            if (actualBlock instanceof Block
                && ((Block) actualBlock).getBlockType().equals(reqType)
                && attachedBlocks.contains(actualBlock)) {
                continue;
            }
            return ActionResults.FAILED;
        }
        task.getRequirements().keySet().forEach(pos -> {
            Attachable a = getUniqueAttachable(pos.translate(e.getPosition()));
            removeObjectFromGame(a);
        });
        teams.get(e.getTeamName()).addScore(task.getReward());
        task.complete();

        this.grid.moveGoalZone(e.getPosition());

        var result = new JSONObject();
        result.put("type", "task completed");
        result.put("task", task.getName());
        result.put("team", e.getTeamName());
        if (logEvents != null) logEvents.put(result);

        return ActionResults.SUCCESS;
    }

    /**
     * @param entity entity executing the action
     * @param xy target position in entity local system
     * @return action result
     */
    String handleClearAction(Entity entity, Position xy) {
        var target = xy.translate(entity.getPosition());
        var distance = entity.getPosition().distanceTo(target);
        if (distance > entity.getVision()) return FAILED_LOCATION;
        if (entity.getEnergy() < Entity.clearEnergyCost) return FAILED_RESOURCES;

        entity.consumeClearEnergy();

        var removed = clearArea(target, 0, 0, false);

        var targetEntity = getThingsAt(target).stream()
                .filter(Entity.class::isInstance)
                .map(Entity.class::cast)
                .findAny();
        if (targetEntity.isPresent()) {
            var damage = getClearDamage(distance);
            targetEntity.get().decreaseEnergy(damage);
            addEventPercept(targetEntity.get(), new JSONObject()
                    .put("type", "hit")
                    .put("origin", entity.getPosition().relativeTo(targetEntity.get().getPosition()).toJSON())
                    .put("damage", damage)
            );
        }

        if (removed > 0 || targetEntity.isPresent())
            return SUCCESS;
        else
            return FAILED_TARGET;
    }

    private int getClearDamage(int distance) {
        if (distance > clearDamage.length)
            return clearDamage[clearDamage.length - 1];
        return clearDamage[distance];
    }

    int clearArea(Position center, int radius, int damage, boolean destroyAttachments) {
        var removed = 0;
        for (var position : center.spanArea(radius)) {
            for (var go : getThingsAt(position)) {
                if (go instanceof Entity) {
                    ((Entity)go).decreaseEnergy(damage);
                }
                if (go instanceof Block block) {
                    if (destroyAttachments || !block.isAttachedToAnotherEntity()) {
                        removed++;
                        grid.destroyThing(go);
                        gameObjects.remove(go.getID());
                    }
                } else if (go instanceof Obstacle obs) {
                    if (destroyAttachments || !obs.isAttachedToAnotherEntity()) {
                        removed++;
                        grid.destroyThing(go);
                        gameObjects.remove(go.getID());
                    }
                }
            }
        }
        return removed;
    }

    Task createTask(int duration, int size) {
        if (size < 1) return null;
        var name = "task" + tasks.values().size();
        var requirements = new HashMap<Position, String>();
        var blockList = new ArrayList<>(blockTypes);
        Position lastPosition = Position.of(0, 1);
        requirements.put(lastPosition, blockList.get(RNG.nextInt(blockList.size())));
        for (int i = 0; i < size - 1; i++) {
            int index = RNG.nextInt(blockTypes.size());
            double direction = RNG.nextDouble();
            if (direction <= .3) {
                lastPosition = Position.of(lastPosition.x - 1, lastPosition.y);
            }
            else if (direction <= .6) {
                lastPosition = Position.of(lastPosition.x + 1, lastPosition.y);
            }
            else {
                lastPosition = Position.of(lastPosition.x, lastPosition.y + 1);
            }
            requirements.put(lastPosition, blockList.get(index));
        }
        Task t = new Task(name, step + duration, requirements);
        tasks.put(t.getName(), t);
        return t;
    }

    Task createTask(String name, int duration, Map<Position, String> requirements) {
        if (requirements.size() == 0) return null;
        Task t = new Task(name, step + duration, requirements);
        tasks.put(t.getName(), t);
        return t;
    }

    private void removeObjectFromGame(GameObject go){
        if (go == null) return;
        if (go instanceof Positionable) grid.destroyThing((Positionable) go);
        gameObjects.remove(go.getID());
    }

    private Entity createEntity(Position xy, String name, String teamName, Role role) {
        Entity e = grid.createEntity(xy, name, teamName, role);
        registerGameObject(e);
        agentToEntity.put(name, e);
        entityToAgent.put(e, name);
        return e;
    }

    Block createBlock(Position xy, String blockType) {
        if (!blockTypes.contains(blockType)) return null;
        Block b = grid.createBlock(xy, blockType);
        registerGameObject(b);
        return b;
    }

    boolean createDispenser(Position xy, String blockType) {
        if (!blockTypes.contains(blockType)) return false;
        if (!grid.isUnblocked(xy)) return false;
        if (dispensers.get(xy) != null) return false;
        Dispenser d = new Dispenser(xy, blockType);
        registerGameObject(d);
        dispensers.put(xy, d);
        Log.log(Log.Level.NORMAL, "Created " + d);
        return true;
    }

    private void registerGameObject(GameObject o) {
        if (o == null) return;
        this.gameObjects.put(o.getID(), o);
    }

    private Attachable getUniqueAttachable(Position pos) {
        var attachables = getAttachables(pos);
        if (attachables.size() != 1) return null;
        return attachables.iterator().next();
    }

    private Set<Attachable> getAttachables(Position position) {
        return getThingsAt(position).stream()
                .filter(go -> go instanceof Attachable)
                .map(go -> (Attachable)go)
                .collect(Collectors.toSet());
    }

    Set<Positionable> getThingsAt(Position pos) {
        return grid.getThings(pos);
    }

    private boolean attachedToOpponent(Attachable a, Entity entity) {
        return a.collectAllAttachments().stream().anyMatch(other -> other instanceof Entity && ofDifferentTeams((Entity) other, entity));
    }

    private boolean ofDifferentTeams(Entity e1, Entity e2) {
        return !e1.getTeamName().equals(e2.getTeamName());
    }

    JSONObject takeStatusSnapshot() {
        JSONObject snapshot = new JSONObject();
        snapshot.put("step", step);
        JSONArray entityArr = new JSONArray();
        snapshot.put("entities", entityArr);
        for (Entity o : agentToEntity.values()) {
            JSONObject obj = new JSONObject();
            obj.put("name", o.getAgentName());
            obj.put("team", o.getTeamName());
            obj.put("action", Actions.ALL_ACTIONS.contains(o.getLastAction()) ? "HIDDEN" : o.getLastAction());
            obj.put("actionResult", o.getLastActionResult());
            entityArr.put(obj);
        }
        return snapshot;
    }

    JSONObject takeSnapshot() {
        JSONObject snapshot = new JSONObject().put("step", step);
        JSONArray entities = new JSONArray();
        snapshot.put("entities", entities);
        JSONArray blocks = new JSONArray();
        snapshot.put("blocks", blocks);
        JSONArray dispensers = new JSONArray();
        snapshot.put("dispensers", dispensers);
        JSONArray taskArr = new JSONArray();
        snapshot.put("tasks", taskArr);
        JSONArray cells = new JSONArray();
        snapshot.put("cells", cells);
        JSONArray clear = new JSONArray();
        snapshot.put("clear", clear);
        JSONObject scores = new JSONObject();
        snapshot.put("scores", scores);
        grid.getObstaclePositions(); // TODO
        grid.getZones(ZoneType.GOAL); // TODO (zones may overlap)
        grid.getZones(ZoneType.ROLE); // TODO (zones may overlap)
//        for (int y = 0; y < grid.getDimY(); y++) {
//            JSONArray row = new JSONArray();
//            for (int x = 0; x < grid.getDimX(); x++) {
//                row.put(grid.getTerrain(Position.of(x, y)).id);
//            }
//            cells.put(row);
//        }
        for (GameObject o : gameObjects.values()) {
            JSONObject obj = new JSONObject();
            if (o instanceof Positionable) {
                obj.put("x", ((Positionable) o).getPosition().x);
                obj.put("y", ((Positionable) o).getPosition().y);
            }
            if (o instanceof Attachable) {
                JSONArray arr = new JSONArray();
                ((Attachable) o).collectAllAttachments().stream().filter(a -> a != o).forEach(a -> {
                    JSONObject pos = new JSONObject();
                    pos.put("x", a.getPosition().x);
                    pos.put("y", a.getPosition().y);
                    arr.put(pos);
                });
                if (!arr.isEmpty()) obj.put("attached", arr);
            }
            if (o instanceof Entity e) {
                obj.put("id", o.getID());
                obj.put("name", e.getAgentName());
                obj.put("team", e.getTeamName());
                obj.put("role", e.getRole().name());
                obj.put("energy", e.getEnergy());
                obj.put("vision", e.getVision());
                obj.put("action", e.getLastAction());
                obj.put("actionParams", e.getLastActionParams());
                obj.put("actionResult", e.getLastActionResult());
                if (e.isDeactivated()) obj.put("deactivated", true);
                entities.put(obj);
            } else if (o instanceof Block) {
                obj.put("type", ((Block) o).getBlockType());
                blocks.put(obj);
            } else if (o instanceof Dispenser) {
                obj.put("id", o.getID());
                obj.put("type", ((Dispenser) o).getBlockType());
                dispensers.put(obj);
            }
        }
        for (ClearEvent e : clearEvents) {
            JSONObject event = new JSONObject();
            event.put("x", e.getPosition().x);
            event.put("y", e.getPosition().y);
            event.put("radius", e.getRadius());
            clear.put(event);
        }
        tasks.values().stream().filter(t -> !t.isCompleted() && step <= t.getDeadline()).sorted(Comparator.comparing(Task::getDeadline)).forEach(t -> {
            JSONObject task  = new JSONObject();
            task.put("name", t.getName());
            task.put("deadline", t.getDeadline());
            task.put("reward", t.getReward());
            JSONArray requirementsArr = new JSONArray();
            task.put("requirements", requirementsArr);
            t.getRequirements().forEach((pos, type) -> {
                JSONObject requirement = new JSONObject();
                requirement.put("x", pos.x);
                requirement.put("y", pos.y);
                requirement.put("type", type);
                requirementsArr.put(requirement);
            });
            taskArr.put(task);
        });
        teams.values().forEach(t -> scores.put(t.getName(), t.getScore()));
        snapshot.put("events", logEvents);
        return snapshot;
    }

    JSONObject getResult() {
        JSONObject result =  new JSONObject();
        teams.values().forEach(t -> {
            JSONObject teamResult = new JSONObject();
            teamResult.put("score", t.getScore());
            result.put(t.getName(), teamResult);
        });
        return result;
    }

    boolean teleport(String entityName, Position targetPos) {
        Entity entity = getEntityByName(entityName);
        if (entity == null || targetPos == null) return false;
        if (grid.isUnblocked(targetPos)) {
            grid.moveWithoutAttachments(entity, targetPos);
            return true;
        }
        return false;
    }

    boolean attach(Position p1, Position p2) {
        Attachable a1 = getUniqueAttachable(p1);
        Attachable a2 = getUniqueAttachable(p2);
        if (a1 == null || a2 == null) return false;
        return grid.attach(a1, a2);
    }

    public String handleSurveyDispenserAction(Entity entity) {
        var optDispenser = dispensers.values().stream().min(
                Comparator.comparing(d -> d.getPosition().distanceTo(entity.getPosition())));
        if (optDispenser.isEmpty()) return FAILED_TARGET;

        var distance = optDispenser.get().getPosition().distanceTo(entity.getPosition());
        this.addEventPercept(entity, new JSONObject()
                .put("type", "surveyed")
                .put("target", "dispenser")
                .put("distance", distance)
        );
        return SUCCESS;
    }

    public String handleSurveyZoneAction(Entity entity, ZoneType zoneType) {
        var distance = grid.getDistanceToNextZone(zoneType, entity.getPosition());
        if (distance == null) return FAILED_TARGET;
        this.addEventPercept(entity, new JSONObject()
                .put("type", "surveyed")
                .put("target", zoneType.toString())
                .put("distance", distance)
        );
        return SUCCESS;
    }

    public String handleSurveyTargetAction(Entity entity, Entity targetEntity) {
        var distance = entity.getPosition().distanceTo(targetEntity.getPosition());
        if (distance > entity.getVision()) return FAILED_LOCATION;
        this.addEventPercept(entity, new JSONObject()
                .put("type", "surveyed")
                .put("target", "agent")
                .put("name", targetEntity.getAgentName())
                .put("role", targetEntity.getRole().name())
                .put("energy", targetEntity.getEnergy())
        );
        return SUCCESS;
    }

    public String handleAdoptAction(Entity entity, String roleName) {
        if (roleName == null) return FAILED_PARAMETER;
        var role = this.roles.get(roleName);
        if (role == null) return FAILED_PARAMETER;
        if (!grid.isInZone(ZoneType.ROLE, entity.getPosition())) return FAILED_LOCATION;
        entity.setRole(role);
        return SUCCESS;
    }

    /**
     * Adds a percept for the current step that cannot be determined at perception time.
     * These percepts survive until the next time percepts are sent out in prepareStep()
     */
    private void addEventPercept(Entity entity, JSONObject percept) {
        stepEvents.computeIfAbsent(entity.getAgentName(), k -> new JSONArray())
                .put(percept);
    }
}

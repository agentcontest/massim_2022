package massim.game;

import massim.config.TeamConfig;
import massim.game.environment.ClearEvent;
import massim.game.environment.Grid;
import massim.game.environment.Task;
import massim.game.environment.positionable.*;
import massim.game.environment.zones.Zone;
import massim.game.environment.zones.ZoneType;
import massim.game.norms.Norm;
import massim.game.norms.Officer;
import massim.game.norms.Officer.Record;
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
public class GameState {

    private int step = -1;
    private final int teamSize;

    // static env. things
    private final Grid grid;
    private final Map<String, Team> teams = new HashMap<>();
    private final Officer officer;

    // dynamic env. things
    private final Map<String, Task> tasks = new HashMap<>();
    private final Set<ClearEvent> clearEvents = new HashSet<>();

    // config parameters
    private final int randomFail;
    private final Bounds taskMaxDuration;
    private final Bounds taskSizeBounds;
    private final Bounds taskIterations;
    private final int concurrentTasks;
    private final Bounds eventRadiusBounds;
    private final Bounds eventCreateBounds;
    private final int eventChance;
    private final int eventWarning;
    private final int eventCreatePerimeter;
    private final int[] clearDamage;

    private final Map<String, JSONArray> stepEvents = new HashMap<>();

    private final JSONArray logEvents = new JSONArray();

    public GameState(JSONObject config, Set<TeamConfig> matchTeams) {
        this.randomFail = ConfigUtil.getInt(config, "randomFail");
        int attachLimit = ConfigUtil.getInt(config, "attachLimit");
        var clusterSizes = ConfigUtil.getBounds(config, "clusterBounds");

        this.grid = new Grid(config.getJSONObject("grid"), attachLimit);

        Entity.clearEnergyCost = ConfigUtil.getInt(config, "clearEnergyCost");
        Entity.deactivatedDuration = ConfigUtil.getInt(config, "deactivatedDuration");
        Entity.maxEnergy = ConfigUtil.getInt(config, "maxEnergy");
        Entity.refreshEnergy = ConfigUtil.getInt(config, "refreshEnergy");
        Entity.stepRecharge = ConfigUtil.getInt(config, "stepRecharge");

        var blockTypeBounds = ConfigUtil.getBounds(config, "blockTypes");
        var numberOfBlockTypes = RNG.betweenClosed(blockTypeBounds.lower(), blockTypeBounds.upper());
        for (int i = 0; i < numberOfBlockTypes; i++)
            this.grid.blocks().addType("b" + i);

        var dispenserBounds = ConfigUtil.getBounds(config, "dispensers");

        clearDamage = JSONUtil.getIntArray(config, "clearDamage");

        var taskConfig = config.getJSONObject("tasks");
        this.taskMaxDuration = ConfigUtil.getBounds(taskConfig, "maxDuration");
        this.concurrentTasks = ConfigUtil.getInt(taskConfig, "concurrent");
        this.taskIterations = ConfigUtil.getBounds(taskConfig, "iterations");
        this.taskSizeBounds = ConfigUtil.getBounds(taskConfig, "size");

        var eventConfig = config.getJSONObject("events");
        this.eventChance = ConfigUtil.getInt(eventConfig, "chance");
        this.eventRadiusBounds = ConfigUtil.getBounds(eventConfig, "radius");
        this.eventWarning = ConfigUtil.getInt(eventConfig, "warning");
        this.eventCreateBounds = ConfigUtil.getBounds(eventConfig, "create");
        this.eventCreatePerimeter = ConfigUtil.getInt(eventConfig, "perimeter");

        matchTeams.forEach(team -> teams.put(team.getName(), new Team(team.getName())));

        var defaultRole = this.parseRoles(config);

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
                        this.grid.entities().create(p, team.getAgentNames().get(index), team.getName(), defaultRole);
                    }
                    agentCounter++;
                    if (agentCounter == numberOfAgents) break;
                }
            }
        }
        teamSize = agentCounter;

        // create env. things
        for (var block : this.grid.blocks().getTypes()) {
            var numberOfDispensers = RNG.betweenClosed(dispenserBounds);
            for (var i = 0; i < numberOfDispensers; i++) {
                this.grid.dispensers().create(this.grid.findRandomFreePosition(), block);
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

        this.officer = new Officer(config.getJSONObject("regulation"));
    }

    private Role parseRoles(JSONObject config) {
        var rolesData = config.getJSONArray("roles");
        var defaultRoleData = rolesData.getJSONObject(0);
        var defaultRole = Role.fromJSON(defaultRoleData);
        this.grid.entities().addRole(defaultRole);

        for (int i = 1; i < rolesData.length(); i++) {
            var roleData = rolesData.getJSONObject(i);
            var role = Role.fromJSON(roleData, defaultRole);
            this.grid.entities().addRole(role);
        }
        return defaultRole;
    }

    Map<String, Team> getTeams() {
        return this.teams;
    }

    public Officer getOfficer() {
        return officer;
    }

    private void handleCommand(String[] command) {
        if (command.length == 0) return;

        switch (command[0]) {
            case "move":
                if (command.length != 4) break;
                var x = Util.tryParseInt(command[1]);
                var y = Util.tryParseInt(command[2]);
                var entity = this.grid.entities().getByName(command[3]);

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
                        this.grid.blocks().create(Position.of(x, y), blockType);
                        break;
                    case "dispenser":
                        blockType = command[4];
                        this.grid.dispensers().create(Position.of(x, y), blockType);
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
                    createTask(name, duration, 1, requireMap);
                }
                break;

            case "attach":
                if (command.length != 5) break;
                var x1 = Util.tryParseInt(command[1]);
                var y1 = Util.tryParseInt(command[2]);
                var x2 = Util.tryParseInt(command[3]);
                var y2 = Util.tryParseInt(command[4]);
                if (x1 == null || x2 == null || y1 == null || y2 == null) break;
                Attachable a1 = this.grid.getUniqueAttachable(Position.of(x1, y1));
                Attachable a2 = this.grid.getUniqueAttachable(Position.of(x2, y2));
                if (a1 == null || a2 == null) break;
                grid.attach(a1, a2);
                break;

            case "terrain":
                if (command.length != 4) break;
                x = Util.tryParseInt(command[1]);
                y = Util.tryParseInt(command[2]);
                var type = command[3];
                if (x == null || y == null || type.isEmpty()) break;
                if (type.equalsIgnoreCase("obstacle")) grid.obstacles().create(Position.of(x, y));
                else if (type.equalsIgnoreCase("goal")) grid.addZone(ZoneType.GOAL, Position.of(x, y), 1);
                break;

            default:
                Log.log(Log.Level.ERROR, "Cannot handle command " + Arrays.toString(command));
        }
    }

    int getRandomFail() {
        return this.randomFail;
    }

    public Grid grid() {
        return grid;
    }

    Map<String, SimStartMessage> getInitialPercepts(int steps) {
        Map<String, SimStartMessage> result = new HashMap<>();
        for (Entity e: this.grid.entities().getAll()) {
            result.put(e.getAgentName(),
                    new InitialPercept(e.getAgentName(), e.getTeamName(), teamSize, steps, this.grid.entities().getRoles()));
        }
        return result;
    }

    Map<String, RequestActionMessage> prepareStep(int step) {
        this.step = step;

        this.logEvents.clear();
        this.grid.deleteMarkers();

        // handle norms before everything else
        this.officer.regulateNorms(step, this.grid.entities().getAll());

        this.createNewTasks();

        this.grid.entities().getAll().forEach(Entity::preStep);

        //handle (map) events
        if (RNG.nextInt(100) < eventChance) {
            this.clearEvents.add(new ClearEvent(grid.getRandomPosition(), step + eventWarning,
                    RNG.betweenClosed(eventRadiusBounds)));
        }
        var processedEvents = new HashSet<ClearEvent>();
        for (var event: clearEvents) {
            if (event.step() == step) {
                this.processEvent(event);
                processedEvents.add(event);
            }
            else {
                var type = event.step() - step <= 2? Marker.Type.CLEAR_IMMEDIATE : Marker.Type.CLEAR;
                var clearArea = event.position().spanArea(event.radius());
                var clearPerimeter = event.position().spanArea(event.radius() + eventCreatePerimeter);
                clearPerimeter.removeAll(clearArea);
                for (Position pos: clearArea) grid.markers().create(pos, type);
                for (Position pos: clearPerimeter) grid.markers().create(pos, Marker.Type.CLEAR_PERIMETER);
            }
        }
        this.clearEvents.removeAll(processedEvents);

        // wait for the environment to get updated, then create norms
        this.officer.createNorms(step, this);

        return this.getStepPerceptsAndCleanUp();
    }

    private void createNewTasks() {
        var activeTasks = tasks.values().stream()
                .filter(t -> !t.isCompleted())
                .filter(t -> this.step <= t.getDeadline())
                .count();
        var tasksMissing = this.concurrentTasks - activeTasks;
        for (var i = 0; i < tasksMissing; i++)
            this.createRandomTask();
    }

    Map<String, RequestActionMessage> getStepPerceptsAndCleanUp() {
        var result = this.getStepPercepts();
        this.stepEvents.clear();
        return result;
    }

    private void processEvent(ClearEvent event) {
        var removed = clearArea(event.position(), event.radius(), 1000, true);
        var distributeNew = RNG.betweenClosed(eventCreateBounds) + removed;

        for (var i = 0; i < distributeNew; i++) {
            var pos = grid.findRandomFreePosition(event.position(),eventCreatePerimeter + event.radius());
            if(pos != null && !this.grid.dispensers().isTaken(pos)) {
                this.grid.obstacles().create(pos);
            }
        }
    }

    Map<String, RequestActionMessage> getStepPercepts(){
        Map<String, RequestActionMessage> result = new HashMap<>();
        var activeTasks = tasks.values().stream()
                .filter(t -> !t.isCompleted())
                .map(Task::toPercept)
                .collect(Collectors.toSet());
        var allNorms = officer.getApprovedNorms(this.step).stream()
                .filter(n -> n.toAnnounce(this.step) || n.isActive(this.step))
                .map(Norm::toPercept)
                .collect(Collectors.toSet());
        List<Record> records = officer.getArchive(this.step);

        for (var entity : this.grid.entities().getAll()) {
            var agentPos = entity.getPosition();
            var visibleThings = new HashSet<Thing>();
            var attachedThings = new ArrayList<Position>();
            var goalZones = new ArrayList<Position>();
            var roleZones = new ArrayList<Position>();
            for (var currentPos: agentPos.spanArea(entity.getVision())){
                for (var thing : this.grid.getEverythingAt(currentPos)) {
                    visibleThings.add(thing.toPercept(agentPos));
                    if (thing != entity && thing instanceof Attachable a && a.isAttachedToAnotherEntity()){
                        attachedThings.add(thing.getPosition().relativeTo(agentPos));
                    }
                }
                if (this.grid.isInZone(ZoneType.GOAL, currentPos)) goalZones.add(currentPos.relativeTo(agentPos));
                if (this.grid.isInZone(ZoneType.ROLE, currentPos)) roleZones.add(currentPos.relativeTo(agentPos));
            }
            List<String> punishment = records.stream()
                                                .filter(p -> p.entity().getAgentName().equals(entity.getAgentName()))
                                                .map(Record::norm)
                                                .collect(Collectors.toList());
            result.put(entity.getAgentName(), new StepPercept(
                    step,
                    teams.get(entity.getTeamName()).getScore(),
                    visibleThings,
                    activeTasks,
                    allNorms,
                    entity.getLastAction(),
                    entity.getLastActionParams(),
                    entity.getLastActionResult(),
                    attachedThings,
                    stepEvents.get(entity.getAgentName()),
                    entity.getRole().name(),
                    entity.getEnergy(),
                    entity.isDeactivated(),
                    punishment,
                    goalZones,
                    roleZones
            ));
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
        for (Entity e: this.grid.entities().getAll()) {
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
        Attachable a = this.grid.getUniqueAttachable(target);
        if (a == null)
            return FAILED_TARGET;
        if (a instanceof Entity && ofDifferentTeams(entity, (Entity) a))
            return FAILED_TARGET;
        if(attachedToOpponent(a, entity))
            return FAILED_BLOCKED;
        if (!grid.attach(entity, a))
            return FAILED;
        return SUCCESS;
    }

    String handleDetachAction(Entity entity, String direction) {
        Position target = entity.getPosition().moved(direction, 1);
        var a = this.grid.getUniqueAttachable(target);
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
        var attachable1 = this.grid.getUniqueAttachable(attPos1.translate(entity.getPosition()));
        var attachable2 = this.grid.getUniqueAttachable(attPos2.translate(entity.getPosition()));
        if (attachable1 == null || attachable2 == null) return ActionResults.FAILED_TARGET;
        var allAttachments = entity.collectAllAttachments(true);
        if (!allAttachments.contains(attachable1) || !allAttachments.contains(attachable2))
            return ActionResults.FAILED_TARGET;
        if (grid.detachNeighbors(attachable1, attachable2)) return ActionResults.SUCCESS;
        return ActionResults.FAILED_TARGET;
    }

    String handleConnectAction(Entity entity, Position blockPos, Entity partnerEntity, Position partnerBlockPos) {
        Attachable block1 = this.grid.getUniqueAttachable(blockPos.translate(entity.getPosition()));
        Attachable block2 = this.grid.getUniqueAttachable(partnerBlockPos.translate(partnerEntity.getPosition()));

        if(!(block1 instanceof Block) || !(block2 instanceof Block)) return ActionResults.FAILED_TARGET;

        Set<Attachable> attachables = entity.collectAllAttachments(true);
        if (attachables.contains(partnerEntity)) return ActionResults.FAILED;
        if (!attachables.contains(block1)) return ActionResults.FAILED_TARGET;
        if (attachables.contains(block2)) return ActionResults.FAILED_TARGET;

        Set<Attachable> partnerAttachables = partnerEntity.collectAllAttachments(true);
        if (!partnerAttachables.contains(block2)) return ActionResults.FAILED_TARGET;
        if (partnerAttachables.contains(block1)) return ActionResults.FAILED_TARGET;

        if(grid.attach(block1, block2)){
            return SUCCESS;
        }
        return FAILED;
    }

    String handleRequestAction(Entity entity, String direction) {
        var requestPosition = entity.getPosition().moved(direction, 1);
        var dispenser = this.grid.dispensers().lookup(requestPosition);
        if (dispenser == null) return ActionResults.FAILED_TARGET;
        if (grid.isBlocked(requestPosition)) return ActionResults.FAILED_BLOCKED;
        this.grid.blocks().create(requestPosition, dispenser.getBlockType());
        return ActionResults.SUCCESS;
    }

    String handleSubmitAction(Entity e, String taskName) {
        Task task = tasks.get(taskName);
        if (task == null || task.isCompleted() || step > task.getDeadline())
            return ActionResults.FAILED_TARGET;
        Position ePos = e.getPosition();
        if (grid.isNotInZone(ZoneType.GOAL, ePos)) return ActionResults.FAILED;
        Set<Attachable> attachedBlocks = e.collectAllAttachments(true);
        for (Map.Entry<Position, String> entry : task.getRequirements().entrySet()) {
            var pos = entry.getKey();
            var reqType = entry.getValue();
            var checkPos = Position.wrapped(pos.x + ePos.x, pos.y + ePos.y);
            var actualBlock = this.grid.getUniqueAttachable(checkPos);
            if (actualBlock instanceof Block
                && ((Block) actualBlock).getBlockType().equals(reqType)
                && attachedBlocks.contains(actualBlock)) {
                continue;
            }
            return ActionResults.FAILED;
        }
        task.getRequirements().keySet().forEach(pos -> {
            Attachable a = this.grid.getUniqueAttachable(pos.translate(e.getPosition()));
            a.destroy();
        });
        teams.get(e.getTeamName()).addScore(task.getReward());
        task.completeOnce();

        this.grid.moveGoalZone(e.getPosition());

        var result = new JSONObject();
        result.put("type", "task completed");
        result.put("task", task.getName());
        result.put("team", e.getTeamName());
        logEvents.put(result);

        return ActionResults.SUCCESS;
    }

    /**
     * @param entity entity executing the action
     * @param xy target position in entity local system
     * @return action result
     */
    String handleClearAction(Entity entity, Position xy) {
        if (RNG.nextDouble() > entity.getRole().clearChance())
            return FAILED_RANDOM;

        int maxDistance = entity.getRole().clearMaxDistance();

        var targetPosition = xy.translate(entity.getPosition());
        var distance = entity.getPosition().distanceTo(targetPosition);
        if (distance > maxDistance) return FAILED_LOCATION;
        if (entity.getEnergy() < Entity.clearEnergyCost) return FAILED_RESOURCES;

        entity.consumeClearEnergy();

        var removed = this.clearArea(targetPosition, 0, 0, false);

        var targetEntities = new ArrayList<Entity>();

        if (maxDistance > 1) {
            targetEntities.addAll(this.grid.entities().lookup(targetPosition));
            for (Entity targetEntity : targetEntities) {
                var damage = this.getClearDamage(distance);
                targetEntity.decreaseEnergy(damage);
                addEventPercept(targetEntity, new JSONObject()
                        .put("type", "hit")
                        .put("origin", entity.getPosition().relativeTo(targetEntity.getPosition()).toJSON())
                        .put("damage", damage)
                );
            }
        }

        if (removed > 0 || !targetEntities.isEmpty())
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
            for (Entity entity : this.grid.entities().lookup(position))
                entity.decreaseEnergy(damage);

            for (Attachable attachable : this.grid.getThingsDestroyedByClear(position)) {
                if (destroyAttachments || !attachable.isAttachedToAnotherEntity()) {
                    attachable.destroy();
                    removed++;
                }
            }
        }
        return removed;
    }

    void createRandomTask() {
        int duration = RNG.betweenClosed(taskMaxDuration);
        int size = RNG.betweenClosed(taskSizeBounds);
        int iterations = RNG.betweenClosed(taskIterations);
        if (size < 1) return;
        var name = "task" + tasks.values().size();
        var requirements = new HashMap<Position, String>();
        var typeList = new ArrayList<>(this.grid.blocks().getTypes());
        var lastPosition = Position.of(0, 1);
        requirements.put(lastPosition, typeList.get(RNG.nextInt(typeList.size())));
        while (requirements.size() < size) {
            double direction = RNG.nextDouble();
            if (direction <= .3)
                lastPosition = Position.of(lastPosition.x - 1, lastPosition.y);
            else if (direction <= .6)
                lastPosition = Position.of(lastPosition.x + 1, lastPosition.y);
            else
                lastPosition = Position.of(lastPosition.x, lastPosition.y + 1);
            requirements.put(lastPosition, typeList.get(RNG.nextInt(typeList.size())));
        }
        this.createTask(name, duration, iterations, requirements);
    }

    Task createTask(String name, int duration, int iterations, Map<Position, String> requirements) {
        if (requirements.size() == 0) return null;
        Task t = new Task(name, step + duration, iterations, requirements);
        this.tasks.put(t.getName(), t);
        Log.log(Log.Level.NORMAL, "Task created: " + t);
        return t;
    }

    private boolean attachedToOpponent(Attachable a, Entity entity) {
        return a.collectAllAttachments(true).stream().anyMatch(other -> other instanceof Entity e2 && ofDifferentTeams(e2, entity));
    }

    private boolean ofDifferentTeams(Entity e1, Entity e2) {
        return !e1.getTeamName().equals(e2.getTeamName());
    }

    JSONObject takeStatusSnapshot() {
        JSONObject snapshot = new JSONObject();
        snapshot.put("step", step);
        JSONArray entityArr = new JSONArray();
        snapshot.put("entities", entityArr);
        for (Entity o : this.grid.entities().getAll()) {
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
        JSONArray obstacles = new JSONArray();
        snapshot.put("obstacles", obstacles);
        JSONArray dispensers = new JSONArray();
        snapshot.put("dispensers", dispensers);
        JSONArray taskArr = new JSONArray();
        snapshot.put("tasks", taskArr);
        JSONArray normArr = new JSONArray();
        snapshot.put("norms", normArr);
        JSONArray punishmentArr = new JSONArray();
        snapshot.put("violations", punishmentArr);

        for (Entity entity : this.grid.entities().getAll()) {
            entities.put(entity.toJSON()
                               .put("events", this.stepEvents.get(entity.getAgentName())));
        }
        for (Block block : this.grid.blocks().getAll()) {
            blocks.put(block.toJSON());
        }
        for (Dispenser dispenser : this.grid.dispensers().getAll()) {
            dispensers.put(dispenser.toJSON());
        }
        for (Obstacle obstacle : this.grid.obstacles().getAll()) {
            obstacles.put(obstacle.toJSON());
        }

        this.tasks.values().stream()
                .filter(t -> !t.isCompleted())
                .filter(t -> step <= t.getDeadline())
                .sorted(Comparator.comparing(Task::getDeadline))
                .forEach(t -> taskArr.put(t.toJSON()));

        snapshot.put("goalZones", new JSONArray(grid.getZones(ZoneType.GOAL).stream()
                .map(Zone::toJSON).collect(Collectors.toList())));
        snapshot.put("roleZones", new JSONArray(grid.getZones(ZoneType.ROLE).stream()
                .map(Zone::toJSON).collect(Collectors.toList())));
        snapshot.put("clear", new JSONArray(clearEvents.stream()
                .map(ClearEvent::toJSON).collect(Collectors.toList())));

        officer.getApprovedNorms(this.step)
                .forEach(n -> normArr.put(n.toJSON()));
        officer.getArchive(this.step).forEach(
            r -> {
                var record  = new JSONObject()
                        .put("norm", r.norm())
                        .put("who", r.entity().getAgentName());
                punishmentArr.put(record);
            }
        );

        snapshot.put("scores", teams.values().stream()
                .map(t -> new JSONArray().put(t.getName()).put(t.getScore()))
                .collect(Collectors.toList()));

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
        Entity entity = this.grid.entities().getByName(entityName);
        if (entity == null || targetPos == null) return false;
        if (grid.isUnblocked(targetPos)) {
            grid.moveWithoutAttachments(entity, targetPos);
            return true;
        }
        return false;
    }

    public String handleSurveyDispenserAction(Entity entity) {
        var optDispenser = this.grid.dispensers().getAll().stream().min(
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

    public String handleSurveyTargetAction(Entity entity, Position targetPos) {
        var distance = entity.getPosition().distanceTo(targetPos);
        if (distance > entity.getVision())
            return FAILED_LOCATION;
        var targetEntities = new ArrayList<>(this.grid.entities().lookup(targetPos));
        if (targetEntities.isEmpty())
            return FAILED_TARGET;
        RNG.shuffle(targetEntities);
        var targetEntity = targetEntities.get(0);
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
        var role = this.grid.entities().getRole(roleName);
        if (role == null) return FAILED_PARAMETER;
        if (grid.isNotInZone(ZoneType.ROLE, entity.getPosition())) return FAILED_LOCATION;
        entity.setRole(role);
        return SUCCESS;
    }

    /**
     * Adds a percept for the current step that cannot be determined at perception time.
     * These percepts survive until the next time percepts are sent out in prepareStep()
     */
    private void addEventPercept(Entity entity, JSONObject percept) {
        this.stepEvents.computeIfAbsent(entity.getAgentName(), k -> new JSONArray())
                .put(percept);
    }
}

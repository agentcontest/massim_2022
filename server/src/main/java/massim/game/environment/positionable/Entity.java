package massim.game.environment.positionable;

import massim.game.environment.positionable.observer.PositionObserver;
import massim.protocol.data.Position;
import massim.protocol.data.Role;
import massim.protocol.data.Thing;
import massim.protocol.messages.ActionMessage;
import massim.protocol.messages.scenario.ActionResults;
import massim.protocol.messages.scenario.Actions;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


/**
 * A controllable entity in the simulation.
 */
public class Entity extends Attachable {

    public static int maxEnergy = 0;
    public static int clearEnergyCost = 0;
    public static int deactivatedDuration = 0;
    public static int stepRecharge = 0;
    public static int refreshEnergy = 0;

    private static List<PositionObserver> observers = new ArrayList<>();

    public static void setObservers(List<PositionObserver> observers) {
        Entity.observers = observers;
    }

    private final String agentName;
    private final String teamName;
    private Role role;

    private String lastAction = "";
    private List<String> lastActionParams = Collections.emptyList();
    private String lastActionResult = "";

    private int energy;
    private int deactivatedSteps = 0;

    Entity(Position pos, String agentName, String teamName, Role role) {
        super(pos);
        this.agentName = agentName;
        this.teamName = teamName;
        this.energy = maxEnergy;
        this.role = role;
    }

    @Override
    public Thing toPercept(Position origin) {
        Position localPosition = getPosition().relativeTo(origin);
        return new Thing(localPosition.x, localPosition.y, Thing.TYPE_ENTITY, teamName);
    }

    /**
     * recharge or repair
     */
    public void preStep() {
        if (deactivatedSteps > 0 && --deactivatedSteps == 0)
            this.energy = Entity.refreshEnergy;
        else
            energy = Math.min(this.energy + Entity.stepRecharge, Entity.maxEnergy);
    }

    public String getTeamName() {
        return teamName;
    }

    public void setLastActionResult(String result) {
        this.lastActionResult = result;
    }

    public String getAgentName() {
        return agentName;
    }

    public void setNewAction(ActionMessage action) {
        if (action == null) {
            this.lastAction = Actions.NO_ACTION;
            this.lastActionResult = ActionResults.SUCCESS;
            this.lastActionParams = Collections.emptyList();
        }
        else {
            this.lastAction = action.getActionType();
            this.lastActionResult = ActionResults.UNPROCESSED;
            this.lastActionParams = action.getParams();
        }
    }

    public String getLastAction() {
        return this.lastAction;
    }

    public List<String> getLastActionParams() {
        return lastActionParams;
    }

    public String getLastActionResult() {
        return lastActionResult;
    }

    public int getVision() {
        return this.role.vision();
    }

    /**
     * @return the entity's speed considering current attachments
     */
    public int getCurrentSpeed() {
        return this.role.maxSpeed(collectAllAttachments(false).size());
    }

    public void deactivate() {
        deactivatedSteps = Entity.deactivatedDuration + 1; //entity repaired in preStep
        detachAll();
    }

    public boolean isDeactivated() {
        return deactivatedSteps > 0;
    }

    public int getEnergy() {
        return energy;
    }

    public void consumeClearEnergy() {
        this.decreaseEnergy(Entity.clearEnergyCost);
    }

    public void decreaseEnergy(int amount) {
        this.energy = Math.max(energy - amount, 0);
        if (this.energy <= 0) {
            this.deactivate();
        }
    }

    public Role getRole() {
        return this.role;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    public boolean isActionAvailable(String action) {
        return action.equals(Actions.NO_ACTION) || this.role.actions().contains(action);
    }

    @Override
    public List<PositionObserver> getObservers() {
        return Entity.observers;
    }

    public JSONObject toJSON() {
        return super.toJSON()
                .put("name", this.agentName)
                .put("team", this.teamName)
                .put("role", this.role.name())
                .put("energy", this.energy)
                .put("vision", this.getVision())
                .put("action", this.lastAction)
                .put("actionParams", this.lastActionParams)
                .put("actionResult", this.lastActionResult)
                .put("deactivated", this.isDeactivated());
    }
}

package massim.game;

import massim.game.environment.Attachable;
import massim.protocol.data.Position;
import massim.protocol.data.Thing;
import massim.protocol.messages.ActionMessage;
import massim.protocol.messages.scenario.ActionResults;

import java.util.Collections;
import java.util.List;


/**
 * A controllable entity in the simulation.
 */
public class Entity extends Attachable {

    static int maxEnergy = 0;
    static int clearEnergyCost = 0;
    static int deactivatedDuration = 0;
    static int stepRecharge = 0;
    static int refreshEnergy = 0;

    private final String agentName;
    private final String teamName;
    private Role role;

    private String lastAction = "";
    private List<String> lastActionParams = Collections.emptyList();
    private String lastActionResult = "";

    private int energy;
    private int deactivatedSteps = 0;

    public Entity(Position xy, String agentName, String teamName, Role role) {
        super(xy);
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
    void preStep() {
        if (deactivatedSteps > 0 && --deactivatedSteps == 0)
            this.energy = Entity.refreshEnergy;
        else
            energy = Math.min(this.energy + Entity.stepRecharge, Entity.maxEnergy);
    }

    String getTeamName() {
        return teamName;
    }

    void setLastActionResult(String result) {
        this.lastActionResult = result;
    }

    String getAgentName() {
        return agentName;
    }

    void setNewAction(ActionMessage action) {
        this.lastAction = action.getActionType();
        this.lastActionResult = ActionResults.UNPROCESSED;
        this.lastActionParams = action.getParams();
    }

    String getLastAction() {
        return lastAction;
    }

    List<String> getLastActionParams() {
        return lastActionParams;
    }

    String getLastActionResult() {
        return lastActionResult;
    }

    int getVision() {
        return this.role.vision();
    }

    /**
     * @return the entity's speed considering current attachments
     */
    int getCurrentSpeed() {
        return this.role.maxSpeed(collectAllAttachments().size() - 1);
    }

    void deactivate() {
        deactivatedSteps = Entity.deactivatedDuration + 1; //entity repaired in preStep
        detachAll();
    }

    boolean isDeactivated() {
        return deactivatedSteps > 0;
    }

    int getEnergy() {
        return energy;
    }

    void consumeClearEnergy() {
        this.decreaseEnergy(Entity.clearEnergyCost);
    }

    void decreaseEnergy(int amount) {
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

    boolean isActionAvailable(String action) {
        return this.role.actions().contains(action);
    }
}

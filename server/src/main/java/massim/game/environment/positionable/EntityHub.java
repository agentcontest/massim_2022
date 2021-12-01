package massim.game.environment.positionable;

import massim.game.environment.positionable.observer.MultiHub;
import massim.protocol.data.Position;
import massim.protocol.data.Role;
import massim.util.Log;
import massim.util.RNG;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EntityHub extends MultiHub<Entity> {

    private final Map<String, Role> roles = new HashMap<>();
    private final Map<String, Entity> nameToEntity = new HashMap<>();

    public Entity create(Position pos, String agentName, String teamName, Role role) {
        var entity = new Entity(pos, agentName, teamName, role);
        entity.init();
        this.nameToEntity.put(agentName, entity);
        return this.add(entity);
    }

    @Override
    public void notifyDestroy(Positionable positionable) {
        super.notifyDestroy(positionable);
        if (positionable instanceof Entity entity) nameToEntity.remove(entity.getAgentName());
    }

    public Entity getByName(String name) {
        return this.nameToEntity.get(name);
    }

    public void addRole(Role role) {
        Log.log(Log.Level.NORMAL, "Role " + role.name() + " added.");
        this.roles.put(role.name(), role);
    }

    public Role getRole(String name) {
        return this.roles.get(name);
    }

    public List<Role> getRoles() {
        return new ArrayList<>(this.roles.values());
    }

    public Role getRandomRole() {
        var roles = getRoles();
        int index = RNG.nextInt(roles.size());
        return roles.get(index);
    }
}
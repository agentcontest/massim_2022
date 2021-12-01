package massim.game.environment.positionable;

import massim.protocol.data.Position;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

public abstract class Attachable extends Positionable {

    private final Set<Attachable> attachments = new HashSet<>();

    public Attachable(Position pos) {
        super(pos);
    }

    @Override
    public void onDestroyed() {
        this.detachAll();
    }

    public void attach(Attachable other) {
        attachments.add(other);
        other.requestAttachment(this);
    }

    public void detach(Attachable other) {
        attachments.remove(other);
        other.requestDetachment(this);
    }

    public Set<Attachable> getAttachments() {
        return new HashSet<>(attachments);
    }

    public void detachAll() {
        new ArrayList<>(attachments).forEach(this::detach);
    }

    private void requestAttachment(Attachable requester) {
        attachments.add(requester);
    }

    private void requestDetachment(Attachable requester) {
        attachments.remove(requester);
    }

    /**
     * @return a set of all attachments and attachments attached to these attachments (and so on)
     */
    public Set<Attachable> collectAllAttachments(boolean selfIncluded) {
        var attachables = new HashSet<Attachable>();
        attachables.add(this);
        Set<Attachable> newAttachables = new HashSet<>(attachables);
        while (!newAttachables.isEmpty()) {
            var tempAttachables = new HashSet<Attachable>();
            for (Attachable a : newAttachables) {
                for (Attachable a2 : a.getAttachments()) {
                    if (attachables.add(a2)) tempAttachables.add(a2);
                }
            }
            newAttachables = tempAttachables;
        }
        if(!selfIncluded) attachables.remove(this);
        return attachables;
    }

    public boolean  isAttachedToAnotherEntity() {
        return this.collectAllAttachments(false).stream().anyMatch(a -> a instanceof Entity);
    }

    @Override
    public JSONObject toJSON() {
        var result = super.toJSON();
        var positions = new JSONArray();
        this.collectAllAttachments(false).forEach(a ->
                positions.put(a.getPosition().toJSON()));
        if (!positions.isEmpty())
            result.put("attached", positions);
        return result;
    }
}

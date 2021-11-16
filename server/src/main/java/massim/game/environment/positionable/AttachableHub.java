package massim.game.environment.positionable;

import massim.game.environment.positionable.observer.MultiHub;

public class AttachableHub extends MultiHub<Attachable> {
    @Override
    public void notifyCreate(Positionable p) {
        super.notifyCreate(p);
        if (p instanceof Attachable a)
            this.add(a);
    }
}

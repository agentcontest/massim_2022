package massim.game.environment.positionable.observer;
import massim.game.environment.positionable.Positionable;
import massim.protocol.data.Position;

public interface PositionObserver {

    void notifyCreate(Positionable positionable);

    void notifyDestroy(Positionable positionable);

    void notifyMove(Positionable positionable, Position oldPosition, Position newPosition);
}
package massim.game.environment.positionable;

import massim.game.environment.positionable.observer.Hub;
import massim.protocol.data.Position;

import java.util.Set;
import java.util.TreeSet;

public class BlockHub extends Hub<Block> {

    private final Set<String> types = new TreeSet<>();

    public Block create(Position pos, String blockType) {
        if(!this.typeExists(blockType)) return null;
        if (isTaken(pos)) return null;
        var block = new Block(pos, blockType);
        block.init();
        return this.add(block);
    }

    public void addType(String name) {
        this.types.add(name);
    }

    public boolean typeExists(String name) {
        return this.types.contains(name);
    }

    public Set<String> getTypes() {
        return this.types;
    }
}

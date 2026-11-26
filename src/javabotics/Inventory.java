package javabotics;

/** A 9-slot hotbar. Each slot holds a block type, or {@link Chunk#AIR} for empty. */
final class Inventory {
    static final int SLOTS = 9;

    private final byte[] slots = new byte[SLOTS];
    private int selected;

    byte get(int slot) {
        return slots[slot];
    }

    void set(int slot, byte block) {
        slots[slot] = block;
    }

    int selected() {
        return selected;
    }

    void select(int slot) {
        selected = Math.floorMod(slot, SLOTS);
    }

    byte selectedBlock() {
        return slots[selected];
    }
}

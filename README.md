# Javabotics

A 2D grid-based platformer sandbox, the base for a physics-based robotics simulation.
Pure Java 21 (AWT/Swing), no dependencies.

## Run

```bash
java -jar dist/Javabotics.jar          # random world
java -jar dist/Javabotics.jar 42       # fixed seed (numbers or any text)
```

## Build

```bash
./build.sh                              # -> dist/Javabotics.jar
```

## Controls

| Key | Action |
| --- | --- |
| A / D | Move left / right |
| Shift (held) | Sprint (also speeds up flight) |
| W or Space | Jump |
| Space, Space (double-tap within 300 ms) | Toggle flying |
| W or Space / S | Fly up / down (while flying) |
| F3 | Debug overlay (position, chunk, biome, collision window) |
| Esc | Quit |

Touching the ground while flying ends flight, as in Minecraft.

## How it works

- **World** (`World`, `Chunk`): infinite, made of 16×16-block chunks that are generated on
  demand and dropped once they are more than 12 chunks from the camera. Generation is
  deterministic per seed, so dropped chunks come back identical. Blocks are either solid
  (black) or air (not drawn).
- **Terrain** (`TerrainGenerator`, `Biome`, `PerlinNoise`): two layers of seeded 2D Perlin noise.
  - *Biome layer*: the world is split horizontally into regions **8–16 chunks wide**
    (12-chunk grid with ±2 chunks of hashed jitter on each boundary). Biome noise is sampled in
    2D at (region, depth) and picks one of Plains, Hills, Crags, Mountains or Sky Isles.
    Biome settings blend smoothly across region boundaries and between noise values, so
    there are no seams.
  - *Terrain layer*: a 2D density field made of a noise height map plus 2D noise scaled by
    the biome's roughness (overhangs, arches, floating islands). Worm caves are carved
    underground.
- **Player** (`Player`): a 1-wide × 2-tall gray box with fixed-timestep (60 Hz) physics,
  coyote time and jump buffering.
- **Collision** (`CollisionWindow`): each step, only the tiles under the player's swept
  bounding box (usually 2×3 to 3×4 tiles) are copied into a small reusable array.
  Movement is resolved one axis at a time against that array, so a step costs the same
  however big the world gets, and fast movement can't tunnel through blocks.
- **Camera** (`Game`): edge scrolling. The view stays still until the player enters the
  8% margin at any screen edge, then it moves just enough to keep the player inside.
  Rendering merges runs of blocks in each row into single rectangles.

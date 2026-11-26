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
| Left click (hold to paint) | Place the selected slot's contents at the cursor: a block goes anywhere (even mid-air); an empty slot is air, so it removes the block |
| Right click (hold to paint) | Shortcut: always removes the block under the cursor |
| Tab | Switch editing between the foreground and background layers |
| E | Spawn a creature at the cursor |
| 1-9 / mouse wheel / click a slot | Select hotbar slot |
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
- **Layers** (`Chunk`): each chunk has an editable *foreground* (the black blocks everything
  collides with) and a visual-only *background*, a copy of the terrain as generated, drawn
  light blue wherever the foreground has been dug out. Tab switches which layer the mouse
  edits; in background mode foreground blocks are drawn dark gray and background blocks solid
  dark blue. Only Ground (or air) can be placed in the background.
- **Blocks** (`Block`): Ground (terrain), plus two walker parts placed from the hotbar (not
  generated). *Joint* will be a powered connection between two sides of a walker; *Scaffold*
  is an open frame that will never be included in a walker, used to separate blocks. All
  non-air blocks are solid for now.
- **Editing** (`Inventory`, `World.setBlock`): the block under the cursor gets a red outline
  if it isn't air. The 9-slot hotbar starts with Ground, Joint and Scaffold in slots 1-3
  (unlimited) and slots 4-9 empty (= air). Holding a mouse button keeps editing every tile the
  cursor crosses. Blocks can't be placed into an entity. Edited chunks are marked
  modified and never unloaded, so changes persist.
- **Entities** (`Entity`, `EntityList`, `Creature`, `Vision`): every dynamic body, the player included,
  shares the same world-collision code. After each step, overlapping entities are pushed apart
  on the axis of least overlap: sideways split by mass, or lifted on top, so you can stand on
  creatures. Creatures (orange 1x1) wander and hop up 1-block steps. They see in a 140° cone
  facing forward, out to 18 blocks, and foreground blocks block their sight (grid-traversal
  raycast). When one sees the player its eye turns red and it chases them, then keeps going to
  where it last saw them for 3 s. Up to 6 spawn naturally 30-50 blocks from the player and
  despawn beyond ~96 blocks. With F3 on, red lines show which creatures see you.
- **Player** (`Player`): a 1-wide × 2-tall gray box with fixed-timestep (60 Hz) physics,
  coyote time and jump buffering.
- **Collision** (`CollisionWindow`): each step, only the tiles under an entity's swept
  bounding box (usually 2×3 to 3×4 tiles) are copied into a small reusable array.
  Movement is resolved one axis at a time against that array, so a step costs the same
  however big the world gets, and fast movement can't tunnel through blocks.
- **Camera** (`Game`): edge scrolling. The view stays still until the player enters the
  35% margin at any screen edge, then it moves just enough to keep the player inside.
  Rendering merges runs of blocks in each row into single rectangles.

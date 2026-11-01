# Facets

A growing toolbox for builders using [FastAsyncWorldEdit](https://github.com/IntellectualSites/FastAsyncWorldEdit) on Paper.

## Tools

### Loft — `//sel loft`, `//loft`
Curved surfaces stretched between splines: smooth (`smooth`), straight segments (`poly`) or flat triangles (`facet`).
Left click to start a frame, right click to add a point to it, then `//loft set <pattern>`.
`//rpos1` and `//rpos2` remove the last frame or the last point.

### Fuzzy fill — `//fuzzyfill <pattern> <radius> [tolerance] [direction]`
Fills the air around you without leaking through holes smaller than the tolerance, like a cave with an opening in its wall.
Named after WorldEdit's `//fill` and `//fillr`:
- `//fuzzyfill` fills the layer of your feet, across the direction.
- `//fuzzyfillr` fills that layer and everything past it in the direction.
- `//fuzzyfills <pattern> <radius> [tolerance]` fills the whole sphere around you.

The direction is `down` by default and takes the directions of `//fill`: `up`, `down`, `north`, `south`, `east`, `west`, `me`, `forward`, `back`, `left`, `right`.

### Symmetry — `//sym`
Copies around a point, in real time, the blocks you place and break by hand, the text written on signs, and every FAWE edit (commands, brushes, pastes...).
Copies made through FAWE are undone with `//undo`.
Block orientation and shape follow the symmetry: stairs (inner/outer shapes), doors, double chests, rails, slabs, fences, signs and more.

| Command | Effect |
|---|---|
| `//sym set [type]` | Puts the symmetry point at your feet |
| `//sym type <type>` | Changes the type without moving the point |
| `//sym range <blocks \| max>` | Range around the point, capped by the configuration |
| `//sym toggle` | Pauses or resumes |
| `//sym off` | Removes the symmetry |
| `//sym info` | Shows the current symmetry |

| Type | Symmetry | Blocks per block placed |
|---|---|---|
| `simple2d` | Half turn around the vertical axis | 2 |
| `planx` / `plany` / `planz` | Mirror across one plane | 2 |
| `cross2d` | `planx` + `planz` | 4 |
| `cross3d` | All three planes | 8 |
| `star2d` | `cross2d` + 45° diagonals | 8 |
| `star3d` | `star2d` + up/down mirror | 16 |
| `megastar` | `star3d` around all three axes | 48 |

### Gravity — `//gravity`
Turns the gravity on or off for your FAWE edits: the sand, gravel, concrete powder, anvils and dragon eggs you set (`//set`, `//replace`, brushes, pastes...) fall like in vanilla, through air, water, lava, grass and the other blocks replaced when placing a block. They are placed directly where they land, so no item drops. A concrete powder landing in water becomes concrete, and a block with nothing under it falls out of the world.
Only the blocks you set fall: removing the blocks under existing sand does not make it fall. With a symmetry, the copies fall too. The gravity stays on after a restart (saved in `gravity.yml`).
Undone with `//undo`.

### Replace color — `//replacecolor [color] [new color]`
Changes the color of the blocks in the selection and keeps their state: with `red` and `lime`, a `red_bed`, a `red_shulker_box` and red stairs become lime ones, facing the same way. Shulker boxes keep their content and banners their patterns.
Without colors, a chest opens to choose the color to replace, then the new one. Blocks with no version in the new color are left as they are.
Undone with `//undo`.

### Type replace — `//typereplace [type] [new type]`
Same as the color replace for the type of the blocks. A type is a full block with the stairs, slabs, walls, fences, doors... named after it: `spruce_planks`, `polished_granite`, `stone_bricks`, `cut_copper`, `white_wool` (with its stairs and slabs since 26.3)...
With `oak_planks` and `spruce_planks`, oak stairs, slabs and doors become spruce ones; with `oak_planks` and `diorite`, oak planks, stairs and slabs become diorite ones and the doors are left as they are. Waxed copper stays waxed.
Without types, a chest opens to choose the type to replace, then the new one. It lists the planks, the stones, bricks and sandstones, the nether, end and deepslate blocks, cut copper, tuff and the wools.
Undone with `//undo`.

### Menu
Right click with a nether star to open the Facets menu: symmetry, selections and global masks (with favorites) in a few clicks.

### Variants and families — `/variant`, `/family`
With a block in hand, `/variant` shows its variants: with `oak_stairs`, every stairs.
`/family` shows its family: with `oak_stairs`, every oak block (planks, slab, fence, door, logs...).
The two buttons at the bottom of the menu switch between them.
Left click puts the variant in hand, right click adds a stack of it to the inventory.

### `#panes` mask
Every glass pane (`glass_pane` and all stained glass panes), for example `//replace #panes air`.

### `#noocclusion` and `#occluded` masks
`#noocclusion`: every block that lets the blocks behind it be seen, by its shape (stairs, slabs, fences, trapdoors...) or its transparency (glass, leaves, barriers...). Air and liquids are not included.
`#occluded`: every full opaque block with a face seen through a `#noocclusion` neighbor. A face hidden by a full side of the neighbor (the back of a stairs, the bottom of a bottom slab) does not count.

## Permissions

| Permission | Access |
|---|---|
| `facets.loft` | Loft selection, `//loft`, `//rpos1`, `//rpos2` |
| `facets.fuzzyfill` | `//fuzzyfill`, `//fuzzyfillr`, `//fuzzyfills` |
| `facets.symmetry` | `//sym` |
| `facets.gravity` | `//gravity` |
| `facets.replacecolor` | `//replacecolor` |
| `facets.typereplace` | `//typereplace` |
| `facets.menu` | The menu (right click with a nether star) |
| `facets.variants` | `/variant`, `/family` |
| `facets.reload` | `/facets reload` |

All of them default to operators.

## Configuration
`config.yml` and `messages.yml` are created on first launch, and new options are added to them on each update.
`/facets reload` reloads both files.

Other plugins may have commands with the same name (Arceon also has `//loft`, `//replacecolor`...). The Facets commands listed in `commands.priority` go first, with their aliases; the other plugin's command stays available with its prefix, for example `/arceon:/loft`.

## Requirements
- Paper 1.21+
- FastAsyncWorldEdit 2.16+
- Java 21

## Building
```bash
./gradlew build
```
The plugin jar is written to `build/libs/`.

## Versions
Versions follow [Semantic Versioning](https://semver.org): `MAJOR.MINOR.PATCH`.
- **Major**: something you have to change on your side, like a command or permission renamed or removed, a new config format, or a higher minimum Paper, FAWE or Java version.
- **Minor**: new features that break nothing.
- **Patch**: fixes and other changes that add no feature, like messages, dependency updates or a rebuild for a new Paper version.
- **Test builds** come before their version: `0.4.0-alpha.1`, `0.4.0-beta.1`, `0.4.0-rc.1`, then `0.4.0`.

The highest change of a release wins, and resets the numbers on its right. The plugin stays in `0.x` while it is a pre-release; `1.0.0` will be the first stable version.

> More tools are on the way!

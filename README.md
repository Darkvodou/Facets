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

### Menu
Right click with a nether star to open the Facets menu: symmetry, selections and global masks (with favorites) in a few clicks.

### Variants and families — `/variant`, `/family`
With a block in hand, `/variant` shows its variants: with `oak_stairs`, every stairs.
`/family` shows its family: with `oak_stairs`, every oak block (planks, slab, fence, door, logs...).
The two buttons at the bottom of the menu switch between them.
Left click puts the variant in hand, right click adds a stack of it to the inventory.

### `#panes` mask
Every glass pane (`glass_pane` and all stained glass panes), for example `//replace #panes air`.

## Permissions

| Permission | Access |
|---|---|
| `facets.loft` | Loft selection, `//loft`, `//rpos1`, `//rpos2` |
| `facets.fuzzyfill` | `//fuzzyfill`, `//fuzzyfillr`, `//fuzzyfills` |
| `facets.symmetry` | `//sym` |
| `facets.menu` | The menu (right click with a nether star) |
| `facets.variants` | `/variant`, `/family` |
| `facets.reload` | `/facets reload` |

All of them default to operators.

## Configuration
`config.yml` and `messages.yml` are created on first launch, and new options are added to them on each update.
`/facets reload` reloads both files.

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

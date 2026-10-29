# MoreTools

A growing toolbox for builders using [FastAsyncWorldEdit](https://github.com/IntellectualSites/FastAsyncWorldEdit) on Paper.

## Tools

### Loft — `//sel loft`, `//loft`
Curved surfaces stretched between splines: smooth (`smooth`), straight segments (`poly`) or flat triangles (`facet`).
Left click to start a frame, right click to add a point to it, then `//loft set <pattern>`.
`//rpos1` and `//rpos2` remove the last frame or the last point.

### Fuzzy fill — `//fuzzyfill <pattern> <radius> [tolerance]`
Fills the air around you without leaking through holes smaller than the tolerance, like a cave with an opening in its wall.
`//fuzzyfillh` only fills the layer of your feet, `//fuzzyfillr` that layer and everything below it.

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
Right click with a nether star to open the MoreTools menu: symmetry, selections and global masks (with favorites) in a few clicks.

### `#panes` mask
Every glass pane (`glass_pane` and all stained glass panes), for example `//replace #panes air`.

## Permissions

| Permission | Access |
|---|---|
| `moretools.loft` | Loft selection, `//loft`, `//rpos1`, `//rpos2` |
| `moretools.fuzzyfill` | `//fuzzyfill`, `//fuzzyfillh`, `//fuzzyfillr` |
| `moretools.symmetry` | `//sym` |
| `moretools.menu` | The menu (right click with a nether star) |
| `moretools.reload` | `/moretools reload` |

All of them default to operators.

## Configuration
`config.yml` and `messages.yml` are created on first launch, and new options are added to them on each update.
`/moretools reload` reloads both files.

## Requirements
- Paper 1.21+
- FastAsyncWorldEdit 2.16+
- Java 21

## Building
```bash
./gradlew build
```
The plugin jar is written to `build/libs/`.

> More tools are on the way!

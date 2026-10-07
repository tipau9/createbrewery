# Create Brewery

A [Create](https://modrinth.com/mod/create) addon for **Minecraft 1.21.1 / NeoForge**: brewing,
distilling and nightlife built on Create's own machines.

- **Beer chain** — steep, kiln, mill, mash, boil and ferment with Basins, Mixers, Fans and a
  passive **Fermenter** (keeps fermenting while its chunk is unloaded). Bottles and cans via Spout and Press.
- **Spirits and casks** — distilled drinks and cask ageing.
- **Intoxication** — drunkenness and other intoxicants with screen effects, poses and after-effects.
- **Club** — DJ booth (4 decks, loops, hot cues, beat FX), DMX console with fixtures and hazers,
  amp rack with zones and crossover, speakers and subwoofers, bouncer.

Optional integrations: JEI, Iris, Veil, Etched, Simple Voice Chat, playerAnimator, GeckoLib,
Distant Horizons, Tough As Nails.

## Requirements

| | Version |
|---|---|
| Minecraft | 1.21.1 |
| NeoForge | 21.1.228+ |
| Create | 6.0.10 |

## Building

```bash
./gradlew build              # jar + unit tests
./gradlew runGameTestServer  # GameTests
./gradlew runData            # regenerate src/generated
```

The jar lands in `build/libs/`. Pushing a new `mod_version` publishes a GitHub release; an existing
release is only replaced by pushing its `v*` tag.

## License

MIT — see [LICENSE](LICENSE).

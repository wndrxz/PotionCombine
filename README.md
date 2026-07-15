# PotionCombine

Shapeless potion brewing in vanilla cauldrons. Shift-click ingredients
into a water cauldron, wait out a short grace period, and if the pile
matches a recipe the cauldron brews it: bubbles, a progress bar over
the water, a spinning bottle to grab at the end. No GUIs, no NMS.

Status: 1.3.1. Live-tested on Paper 1.20.4.

## Requirements

- Paper 1.20.4+ (Adventure, MiniMessage and display entities — real
  Paper, not Spigot)
- Java 17
- PlaceholderAPI optional, the expansion registers itself if present

## How it plays

1. fill a cauldron with water
2. shift-LMB tosses the held item in, shift-RMB pulls the last one back
3. stop adding: full match brews, partial match or garbage sinks into
   sludge after the grace period
4. right-click the hovering bottle when it's done, don't let it spoil

Extras, each behind its own toggle (the bundled config ships with most
of them on, gently tuned):

- pollution: brews leave grime, a filthy cauldron refuses to work until
  scrubbed with a brush
- heat: campfire, magma or lava under the cauldron brews faster, and
  heat.area widens the search to nearby blocks
- synergy: a finished brew pours itself into a neighbouring cauldron
  that's waiting on exactly that bottle
- hopper under the cauldron pulls finished potions out
- per-world whitelist/blacklist and a per-player cooldown
- progression + /pc journal: an in-game book that fills in as you
  discover recipes
- small public api: static facade plus cancellable events for start,
  success, fail, collect and pollution change

Recipes live in recipes.yml — material or potion ingredients, or other
recipes (phoenix_crown wants a brewed phoenix_elixir). Locales: en, ru.

## Build

`./gradlew build` — the jar lands in `build/libs/`.
`./gradlew test` — five suites (matcher, heat math, synergy, player
progress, locale parity) against the real paper classpath.

## Data files

state.yml (pollution + live brews to resume) and progress.yml
(per-player discovery) are generated, don't edit them by hand.

## License

MIT.

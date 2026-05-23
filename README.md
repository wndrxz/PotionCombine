# PotionCombine

Shapeless potion brewing in vanilla cauldrons. Shift-click ingredients
into a water cauldron, wait out a short grace period, and if the pile
matches a recipe the cauldron brews it: bubbles, a progress bar over
the water, a spinning bottle to grab at the end. No GUIs, no NMS.

Status: 1.1.0.

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

Extras, each behind its own toggle in config.yml:

- pollution: brews leave grime, a filthy cauldron refuses to work until
  scrubbed with a brush
- heat: campfire, magma or lava under the cauldron brews faster
- hopper under the cauldron pulls finished potions out
- per-world whitelist/blacklist and a per-player cooldown
- small public api: static facade plus cancellable events for start,
  success, fail, collect and pollution change

Recipes live in recipes.yml — material or potion ingredients, or other
recipes (phoenix_crown wants a brewed phoenix_elixir). Locales: en, ru.

## Build

`./gradlew build` — the jar lands in `build/libs/`.
`./gradlew test` — the matcher suite runs against the real paper classpath.

## Data files

state.yml (cauldron pollution) is generated, don't edit it by hand.

## License

MIT.

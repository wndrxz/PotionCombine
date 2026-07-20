# Changelog

## 1.4 — 2026-07-20

Start of the 1.4 line.

- now targets paper 1.21+, 1.20.x support dropped. setBasePotionData
  went with it — results use setBasePotionType, which closes the
  oldest TODO in the codebase
- old PotionType spellings in recipes.yml (INSTANT_HEAL, REGEN, JUMP,
  SPEED) still work, they map onto the renamed enum
- builds with java 21 now, gradle fetches one itself if the machine
  doesn't have it
- /pc journal: the "•" line marker rendered as mojibake — the build
  had lost its utf-8 source encoding, so the bullet literal was read
  in the platform charset. put the encoding back, it renders again

## 1.3.1 — 2026-07-15

Patch after a live server pass. fixes first.

- restarting no longer duplicates ingredients: state.yml kept a copy of
  what onDisable had already dropped on the floor
- a resumed brew is pruned from state.yml as soon as it resumes, so a
  crash right after boot can't replay it into free potions
- en.yml shipped without command.help_journal (ru had it), english
  players saw the raw key in /pc help
- the player who caused a failed brew saw the message twice, direct
  plus area broadcast. now once
- grace period default 20 -> 60 ticks, room to load multi-item recipes
- the bundled config is test-friendly now: pollution, synergy,
  progression and area heat ship ON with gentle values. hopper extract
  and the cooldown stay off
- LocaleParityTest keeps en and ru key-for-key in sync

## 1.3 — 2026-06-26

The plugin stops forgetting things between restarts.

- live brews survive a shutdown: mid-brew cauldrons and uncollected
  results go to state.yml and resume on the next boot. on by default,
  restore_live_brews: false brings back the old spill-on-shutdown
- progression (off by default): per-player record of discovered recipes
  plus brew/spoil/fail counters, stored in a flat progress.yml
- /potioncombine journal opens a written book — discovery, reference
  and notes modes. with progression off it still opens as a plain
  cookbook
- api: progressionEnabled(), hasDiscovered(player, recipeId)
- PlayerProgressTest

## 1.2 — 2026-06-19

Cauldrons stop being loners.

- synergy (off by default): when a brew finishes next to a cauldron
  loaded with everything but exactly that bottle, it pours itself in
  and the neighbour starts brewing. completion-only — it never tops up
  a half-loaded cauldron
- a cauldron one ingredient short holds instead of failing while a
  neighbour is actually brewing, up to synergy.max_hold_seconds
- BrewChainEvent (cancellable) fires right before the pour
- heat.area (off by default): heat search widens to a small radius,
  strongest source wins, bonus fades per block of distance
- pollution idle particles run off one shared ticker now instead of a
  task per dirty cauldron
- HeatMathTest and SynergyFeedDecisionTest

## 1.1 — 2026-05-23

Everything around the loop.

- pollution: every brew leaves residue, failures leave more. past the
  threshold the cauldron refuses new ingredients until brushed. off by
  default
- heat sources: campfire, soul campfire, magma, lava under the cauldron
  speed the brew up, each with its own multiplier in config
- hopper auto-extract for finished potions (off by default)
- per-world whitelist/blacklist, per-player cooldown with a bypass perm
- placeholderapi expansion, registered only when papi is on the server
- public api: static facade + cancellable events (start, success, fail,
  collect, pollution change)
- pollution survives restarts in state.yml; a still-loaded cauldron
  drops its ingredients on shutdown instead of eating them
- failure messages broadcast to players within notify_radius_blocks

## 1.0 — 2026-05-14

First version that felt playable.

- shift-click ingredients into a water cauldron, shapeless matching
  against recipes.yml, grace period before the cauldron commits
- failed brews sink into alchemical sludge
- finished potion hovers and spins over the water, spoils if ignored
- four test recipes, one of them chained (crown wants the elixir)
- en and ru locales, minimessage everywhere
- ItemMatcherTest against the real paper classpath

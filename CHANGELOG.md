# Changelog

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

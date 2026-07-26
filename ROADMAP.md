# Roadmap

Where PotionCombine is headed. Same rules as next door: one theme per
release, a release when it's earned — not a patch every evening. Order
can shuffle if a live server finds something embarrassing.

## 1.4.x — small stuff, as it comes

Empty shelf right now — the 1.21 move hasn't produced fallout beyond
the mojibake bullet, and that shipped inside 1.4. New small stuff
lands here as it's found.

## 1.5 — folia

The cauldron learns to share a server with threads.

- no BukkitScheduler anywhere — it throws on folia. GraveDig already
  has the thin wrapper (region for block edits, async for disk io,
  global for repeating ticks), port it instead of inventing a second
  one
- the risky parts are the ones a wrapper doesn't solve: display
  entities want the region that owns the cauldron, synergy pours into
  a neighbour that can live in another region, and the state store
  has to save off-thread without tearing a live brew in half
- doesn't count as done without a live pass on actual folia, same way
  the gravedig 0.1.x line earned its stripes

## 1.6 — the "SQLite" item, finally

progress.yml behind its manager was always meant to be swappable —
the config comment promises a real backend can replace the file
without a config change, so it's time to make good on it. Flat-file
stays the default: paper bundles no jdbc driver and "just works on a
fresh server" is not negotiable.

## 2.0 — call it done

Nothing new. Publish on Hangar/Modrinth, a docs pass over README and
the config comments, and whatever the tracker collected by then.
Boring and predictable, same as the other plugin's 1.0 — for the
thing that decides whether your netherite went into a bottle, that's
the whole point.

## Not planned

- GUIs, brewing stand overrides, NMS. the plugin exists because a
  cauldron and shift-click are enough
- economy hooks, potion shops, all that. someone else's plugin

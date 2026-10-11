# Poster card, home and dock refinements

The dock now uses the existing selected-index animation after a page has settled. Page motion
is no longer wired directly into its drawing position. External selection skips simulated
press/release; real dock taps and drags retain their original interactions.

Tablets without explicit home shortcut choices use the first eight visible default entries.
Explicit choices remain unchanged. Feature blocks exclude shortcut IDs at resolution and rendering,
without deleting the saved feature preference, so removing a shortcut restores its feature block.

Poster swipes retain each occurrence's loaded cover using movable content. The front returns only
after the incoming card has reached composition. Pending playback selection suppresses stale
player emissions until the requested occurrence is acknowledged, including duplicate queue songs.

Folia reference used: commit f358c44d1eff99d87a9d0831a06c7cd7a0ff21da,
https://github.com/chthollyphile/folia-major/blob/f358c44d1eff99d87a9d0831a06c7cd7a0ff21da/src/components/visualizer/lumiere/text/lyricEcho.ts
and its windowGeometry.ts. Its background word fragments have finite lifetimes and follow a beam;
they are separate from the readable main line. The previous native scene displaced sung main
glyphs upward and retained a nonzero opacity indefinitely. The native implementation now keeps
the main line in place and draws independent low-contrast word echoes along a moving light field,
with a seven-second lifetime. No Pixi/TypeScript implementation is included in the application.

Regression checks cover completed page swipes without a press animation, dock release navigation,
cover ownership across forward/back swipes, duplicate-queue playback acknowledgments, tablet
preference defaults/deduplication/backup, and symmetric finite word echoes.
These checks do not establish frame rates or vendor-device behavior.

Selection controls and multi-choice panels now expose Invert selection. It flips only eligible
IDs in the current scope, preserves other scopes/disabled choices, clears stale range anchors,
and keeps selection mode open even when the result is empty. Bounded pickers keep their limit.

# Launcher reference and verification

Arlecchino Home uses independently written code and original outline glyphs. The reference app was inspected through normal UI interaction on a physical Xiaomi; its APK was not modified, decompiled, or bundled. No Niagara artwork or code is redistributed.

Reference surfaces inspected: configured Home, alphabet navigation/app list, search and recent-install suggestions, long-press app menu, and app pop-up gestures. Official free-feature descriptions were also consulted:

- https://niagaralauncher.com/
- https://help.niagaralauncher.app/article/115-pop-ups
- https://help.niagaralauncher.app/article/171-alphabet-settings

## Current matching behavior

| Area | Arlecchino implementation | Verification |
| --- | --- | --- |
| Home | One scrolling clock/date/favorites surface; no permanent brand header | Physical device, screenshots |
| Search | Persistent Home search shortcut, swipe-up search, name/package/phonetic matching, recent apps, external web search | Physical search flow and model tests; web search not submitted during testing |
| Alphabet | Both-edge access, continuous row scrubbing, frame-coalesced wave animation, stable gesture geometry, star returns Home | Physical taps/drags and frame timing |
| Ordering | Initial-letter groups and optional usage ordering based on launches made from this app | Model and device checks |
| Chinese labels | Android ICU phonetic transliteration on API 29+; fallback grouping on API 28 | Model tests and device grouping |
| Favorites | Selection, reordering, long-press actions, rename, hide, app info and uninstall intents | Device pin/remove/rename flows; uninstall not executed |
| App pop-ups | Swipe-right sheet, notification action, shortcuts when Android grants the Home role | Physical swipe pop-up; shortcut execution requires making this the default launcher |
| Folders | Basic favorite folders with selection, rename and reordering | Physical create/open/edit/remove flow |
| Notifications | Optional previews, dismiss and RemoteInput replies | Code/build verified; access not granted and no real message sent during tests |
| Icons | Original bundled Arlecchino Outline; system icons; standard installed icon-pack metadata | Bundled icons rendered on phone; third-party pack compatibility depends on its metadata |
| Theme | Warm white outline icons, crimson wave emphasis, optional dimming and label sizes | Physical screenshots |
| Wallpaper | Existing once-per-wake curtain opening and resting frame | Earlier physical wake tests plus regression tests |

This is not a claim of pixel-for-pixel or exhaustive feature parity. Default-Home selection and optional sensitive permissions remain under the user's control. Niagara Pro-only widgets/calendar/weather and proprietary artwork are not part of this free-flow reference implementation.

## Device sample: 2026-11-15

On the same Xiaomi, six 600 ms rail drags in each launcher produced these `dumpsys gfxinfo` samples:

| Metric | Niagara reference | Arlecchino Home 1.3 |
| --- | ---: | ---: |
| Rendered frames | 1,048 | 942 |
| Janky frames | 1 (0.10%) | 1 (0.11%) |
| Median frame time | 7 ms | 6 ms |
| 95th percentile | 15 ms | 7 ms |
| Missed vsync count | 0 | 0 |

This is a short diagnostic sample, not a general performance ranking: the two launchers had different app/notification contents, and perceived smoothness also depends on gesture mapping and device refresh behavior.

The device interaction checks passed for Home/search, pinning, app launching, rename/reset, swipe-right pop-ups, right-edge C navigation, returning Home, and creating/opening/editing/removing a folder. Permission screens remained opt-in. Tests removed their own temporary favorite and folder.

The persistent search shortcut was restored at the user's request. Arlecchino Outline is a bundled original icon style and does not require a separately installed pack. The alphabet uses Choreographer updates, stable gesture geometry, interpolated row offsets, and coalesced touch input. Header removal avoids leaving a cached clock-area gap in the app list.

## Video reference: 1.4

The developer's public video was reviewed as a temporal frame sequence, including enlarged frames around 3.4–5.2 seconds:

https://niagaralauncher.com/wp-content/uploads/2026/02/reveal-scene-mobile.mp4

That sequence shows a filled, pulled-out selected-letter bubble and a compact selected app group around the middle of the screen during an alphabet drag. This differs from 1.3's continuous full-list scroll from the top.

Version 1.4 now uses:

- A filled crimson letter bubble with the small alphabet letter remaining on the curved rail.
- A wider wave area so the bubble is not clipped by the view edge.
- A dimmed wallpaper layer and a compact, vertically centered selected-group preview during dragging.
- A short transition into the full scrollable list when the gesture ends, aligned with the preview's position.
- Explicit Home-state cleanup so a pending gesture cannot reopen the app list after returning Home.

A 7.7-second recording of the physical Xiaomi was reviewed as a frame sequence to compare the drag, group changes, pointer bubble, and release transition. Reference videos and private device recordings remain in ignored local work files, not the public repository or APK.

The persistent Home search box and original outline icons are retained as explicit user preferences, even where the reference video uses a different setup. This is a visual/interaction reference for these flows, not an assertion of complete parity with every Niagara feature.

## Matched physical-device comparison: 1.5

Recorded the installed reference and Arlecchino on the same Xiaomi with the same slow rail drag, fast rail drag, and app-list fling commands. Videos have approximate timing alignment because ADB command startup differs.

Corrections from this comparison:

- Use the same alphabet range on Home and all-apps, with a stable selected-group heading position. A repeated M tap in each state produced the same 861 px heading baseline on this device.
- Follow the latest finger position directly while easing the wave appearance, removing additional pointer lag.
- Preserve native app-row flings instead of interpreting them as a search gesture. Empty-space swipe-up and the persistent search shortcut remain available.
- Keep wallpaper dimming consistent across release and wait for list layout before fading the preview into the aligned list.

An OCR estimate of matched label movement during the sampled upward-fling window measured approximately 3,721 px for the reference and 3,685 px for Arlecchino. This limited, phase-dependent sample supports retaining native fling friction; it does not establish identical physics. In the matched gesture run, each app reported one janky frame and zero missed vsyncs (2,032 rendered frames for Niagara, 1,850 for Arlecchino). Contents and capture timing differed.

Local recordings are excluded from the repository and public release because they may contain personal device information.

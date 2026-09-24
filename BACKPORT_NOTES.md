# Minecraft 1.21.8 resource-pack compatibility backport

Branch: `backport/1.21.8-resourcepack-compat`

Base: upstream `origin/1.21.6` (`2.3.1`), the release line used for Minecraft 1.21.6 through 1.21.8.

## Scope

This branch backports only the Creative-inventory resource-pack compatibility rendering logic from later Smooth Scrolling development. It intentionally excludes unrelated hotbar, touchpad, inertial scrolling, chat, input, and general Creative scrolling changes.

The implementation is based on the upstream resource-pack compatibility work introduced by commit `223a5f9` and the transparent-texture correction from commit `504e62d`.

## What changed

- Added `Creative Screen > RP Compatibility Mode`, default `false`.
- Replaced the post-draw Creative background injection with a wrapped vanilla draw call, so the original texture draw parameters are available.
- In compatibility mode, the moving inventory viewport is rendered by drawing the full Creative GUI texture and clipping it to the viewport. This preserves the original quad dimensions used by resource-pack shaders.
- The static pass excludes the moving inventory viewport. This prevents translucent GUI pixels from being blended once by vanilla and then blended a second time by Smooth Scrolling.
- The smooth-scroll state update remains single-pass. No later scrolling-order or input changes were imported.

## Intended test target

Minecraft 1.21.8 on Fabric, using Smooth Scrolling's 1.21.6 maintenance source line.

## Verification before user testing

- `git diff --check` passes.
- The branch is one commit ahead of `origin/1.21.6`; no hotbar, chat, touchpad, inertia, input, or unrelated Creative files differ from that base.
- The Creative background mixin contains one scroll-decay calculation and one `scrollItems` update per wrapped background draw. This specifically avoids the duplicated update introduced by the earlier broad backport attempt.
- The resource-pack path retains the original full GUI texture draw dimensions and clips the moving inventory viewport instead of reconstructing the entire GUI with fixed dimensions.
- The transparent-texture correction omits the moving viewport from the static background pass, preventing that viewport from being alpha-blended twice.

A local `./gradlew clean build` was attempted. The wrapper could not download Gradle 8.8 because this execution environment cannot resolve `services.gradle.org`, so compilation could not start here. Run the build on a networked machine before treating the JAR as release-tested.

## Edge transparency follow-up

The first compatibility backport always repeated the animated inventory texture `+90` pixels below the first copy. The 1.21.8 renderer uses a signed scroll offset; upstream 2.3.1 repeated the second copy on the side selected by `signum(creativeScreenScrollOffset)`. The fixed backport restores that direction-aware repeat and clips each full-GUI compatibility draw to the exact intersection of its inventory tile with the viewport. This removes uncovered leading-edge rows without double-blending translucent resource-pack pixels.

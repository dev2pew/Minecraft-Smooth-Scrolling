# Minecraft 1.21.8 resource-pack compatibility backport

Branch under test: `fix/1.21.8-rpcompat-tiling`

Base: `backport/1.21.8-resourcepack-compat`, itself based on upstream `1.21.6` / Smooth Scrolling `2.3.1`.

## Scope

This branch changes only the Creative-inventory resource-pack compatibility renderer and the build/release plumbing needed to test it. Hotbar, chat, touchpad, inertial scrolling, input handling, and unrelated later-version features remain outside scope.

## Rendering approach in rpcompat.3

The previous compatibility renderer moved two full 90-pixel inventory copies. That preserved shader-dependent full-GUI draw dimensions, but a resource pack can still expose a transparent leading-edge band when the animated copy crosses the top or bottom of the viewport.

`rpcompat.3` keeps the full-GUI draw call but changes the moving background into five cyclic 18-pixel slot-row strips:

- each source row is drawn with the original full Creative GUI draw parameters;
- each row is clipped to a single 18-pixel destination band;
- when a row crosses the viewport boundary, the same row is drawn once more at the opposite edge;
- the five strips therefore tile the moving 90-pixel viewport without exposing neighbouring GUI artwork;
- the static Creative GUI is still omitted from the moving viewport, preventing translucent pixels from being composited twice.

This is a rendering-only adaptation. It does not alter scroll input, scroll timing, or item-list calculations.

## CI and releases

`.github/workflows/build.yml` now builds every push and pull request with Java 21 and uploads the normal remapped JAR as a 14-day GitHub Actions artifact.

`.github/workflows/release.yml` is intentionally manual. It only accepts versions matching `X.Y.Z-rpcompat.N`, verifies that the requested version equals `mod_version`, builds the project, and creates a GitHub prerelease with exactly one normal JAR. This avoids publishing a release for every experimental push.

## Compatibility target

The forked backport is explicitly limited to Minecraft 1.21.6 through 1.21.8 in `fabric.mod.json`. The primary runtime test target is Fabric Minecraft 1.21.8 with the GUI resource pack that reproduced the transparent-gap issue.

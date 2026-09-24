# Build and user-test guide

## Build

Requirements:

- JDK 21
- Internet access for the Gradle wrapper and Fabric/Maven dependencies on the first build

Windows PowerShell or Command Prompt:

```text
gradlew.bat clean build
```

Linux/macOS:

```text
./gradlew clean build
```

The normal output JAR is:

```text
build/libs/smoothscroll-2.3.1-rpcompat.2.jar
```

The build also creates a sources JAR. Install the normal JAR, not the sources JAR.

## Install for testing

1. Use a Fabric Minecraft 1.21.8 instance with Java 21.
2. Remove the original Smooth Scrolling JAR from that instance. Do not load two JARs with the same `smoothscroll` mod ID.
3. Put `build/libs/smoothscroll-2.3.1-rpcompat.2.jar` in the instance `mods` directory.
4. Keep the GUI resource pack that reproduced the duplicated-texture artifact available for comparison.
5. Capture both scroll directions. The leading edge of the animated inventory grid must not become more transparent at the top or bottom of the viewport.

## Test matrix

### Baseline without a custom GUI pack

- Leave `Creative Screen > RP Compatibility Mode` set to `false`.
- Open the Creative inventory and scroll up and down through a long tab.
- Check for missing slot backgrounds, seams, duplicated GUI pieces, incorrect item positions, or cursor/slot-highlight desynchronization.
- Switch tabs during and immediately after scrolling.
- Resize the window and repeat the test.

Expected result: behavior should match the original 2.3.1 branch except for the transparent-texture-safe background composition used while the Creative scroll animation is active.

### Problematic GUI resource pack

- Enable the GUI resource pack that produced the miniature/duplicated Creative GUI texture.
- First test with `RP Compatibility Mode` set to `false` and confirm the original compatibility problem is still reproducible if that pack needs the compatibility path.
- Set `RP Compatibility Mode` to `true` and save the config. Restart the screen or game if the config UI does not refresh the value immediately.
- Scroll repeatedly in both directions.

Expected result: the moving inventory viewport should use the resource pack's full GUI texture draw rather than a hard-coded cropped quad, so packs that alter GUI texture size or position through shaders should keep their intended geometry.

### Transparent and translucent GUI textures

Use a pack whose Creative inventory background contains alpha transparency. Scroll while watching the slot-grid area and its borders.

Expected result: translucent pixels in the moving inventory viewport should not darken or become more opaque from being blended once as the static GUI and a second time as the moving viewport.

### Edge cases

Also check:

- top and bottom of a scrollable Creative tab;
- rapid mouse-wheel input;
- changing Creative tabs while an animation is still settling;
- the Search tab;
- opening and closing the inventory repeatedly;
- GUI scales you normally use;
- Condensed Creative or Flow if either mod is part of the real modpack.

## Reporting a failure

For a rendering failure, keep these together:

- screenshot or short recording;
- resource-pack name and version;
- whether `RP Compatibility Mode` was `true` or `false`;
- Minecraft/Fabric Loader versions;
- other GUI-rendering mods in the instance;
- `latest.log` from the affected launch.

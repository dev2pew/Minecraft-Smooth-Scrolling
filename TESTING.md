# Build and user-test guide

## Automatic CI build

Every push to this repository runs `.github/workflows/build.yml`. Open the GitHub Actions run and download the `smooth-scroll-<commit>` artifact after the Build job succeeds.

## Local build

Requirements:

- JDK 21
- Internet access for Gradle/Fabric dependencies on the first build

Windows:

```text
gradlew.bat clean build
```

Linux/macOS:

```text
./gradlew clean build
```

Expected normal JAR:

```text
build/libs/smoothscroll-2.3.1-rpcompat.3.jar
```

Install the normal JAR, not the sources JAR.

## Resource-pack regression test

1. Use Fabric Minecraft 1.21.8 with Java 21.
2. Remove any other Smooth Scrolling JAR from the instance.
3. Install `smoothscroll-2.3.1-rpcompat.3.jar`.
4. Enable the GUI resource pack that reproduced the Creative-inventory artifact.
5. Enable `Creative Screen > RP Compatibility Mode`.
6. Scroll slowly up and down through a tab with enough rows to scroll.
7. Record the top and bottom edges of the slot grid while the animation is between rows.

Expected result: the five moving slot rows remain continuously covered. A row that exits one edge wraps to the opposite edge without a transparent band, copied GUI controls, or a dark overlap seam.

## Additional checks

Test:

- vanilla textures with compatibility mode off;
- vanilla textures with compatibility mode on;
- the affected resource pack in both scroll directions;
- Search and Building Blocks tabs;
- first and last scroll positions;
- rapid wheel input;
- tab changes while an animation is settling;
- window resizing and the GUI scales normally used;
- translucent Creative GUI textures;
- Condensed Creative or Flow if present in the real modpack.

A compiler warning about the optional Condensed Creative injection target can still appear when that dependency is only available as a compile-time compatibility target. Treat a runtime `MixinApplyError`, `InvalidInjectionException`, or `InjectionError` as a real failure.

## Manual GitHub prerelease

The Release test build workflow is deliberately manual.

1. Open Actions > Release test build.
2. Select `fix/1.21.8-rpcompat-tiling` while this branch is under test.
3. Enter `2.3.1-rpcompat.3`.
4. Run the workflow.

The workflow validates the version, builds the JAR, creates tag `v2.3.1-rpcompat.3`, and publishes a GitHub prerelease.

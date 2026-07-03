# Ten to One

Java card game (Swing/AWT GUI) with no build tool (no Maven/Gradle) — plain
`javac` compilation. JUnit jars are vendored in `lib/`, not fetched.

## JDK location

`javac`/`java` are not on PATH. The JDK lives at:

```
C:\Program Files\Eclipse Adoptium\jdk-21.0.11.10-hotspot\bin\
```

## Build

```
"/c/Program Files/Eclipse Adoptium/jdk-21.0.11.10-hotspot/bin/javac.exe" -cp "lib/*" -d build src/*.java
```

If you killed a running `java Game` process and are about to recompile and
relaunch it in quick succession, do a clean rebuild first
(`rm -rf build && mkdir build` before the `javac` above). A fast
kill/recompile/relaunch cycle has produced a stale `build/` at least once —
`javac` silently skipped regenerating one class file (a switch-over-enum
synthetic inner class), which only surfaced as a `NoClassDefFoundError` at
runtime, not a compile error. A full clean rebuild is cheap on this project's
size and avoids the failure mode entirely.

## Test

Tests are JUnit 4, run via `JUnitCore` — there's no test runner script. There
are 16 test classes (`src/Test*.java`); list them yourself with a glob rather
than trusting this count if files have been added/removed since:

```
"/c/Program Files/Eclipse Adoptium/jdk-21.0.11.10-hotspot/bin/java.exe" -cp "build;lib/*" org.junit.runner.JUnitCore TestBetStepper TestCard TestGame TestHand TestHandler TestHumanBet TestHumanIllegalReason TestIllegalPlayFeedback TestKeyInput TestNextTrickPrompt TestPlayer TestRound TestRulesView TestStartScreen TestTrick TestWindowSizing
```

## Run the game

```
"/c/Program Files/Eclipse Adoptium/jdk-21.0.11.10-hotspot/bin/java.exe" -cp "build;lib/*" Game
```

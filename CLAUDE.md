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

## Test

Tests are JUnit 4 (`TestGame.java`, `TestHand.java`), run via `JUnitCore` —
there's no test runner script.

```
"/c/Program Files/Eclipse Adoptium/jdk-21.0.11.10-hotspot/bin/java.exe" -cp "build;lib/*" org.junit.runner.JUnitCore TestGame TestHand
```

## Run the game

```
"/c/Program Files/Eclipse Adoptium/jdk-21.0.11.10-hotspot/bin/java.exe" -cp "build;lib/*" Game
```

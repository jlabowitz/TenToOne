# Ten to One

A Java trick-taking card game with a Swing/AWT GUI, played against three AI
opponents across 10 rounds of shrinking hand sizes.

## How to play

- **Objective**: score the most points across 10 rounds by winning tricks.
- **The deal**: round 1 deals 10 cards to each player; each round deals one
  fewer. A trump suit is revealed right after the deal each round.
- **Betting**: before play, bet how many tricks you'll win this round (0 to
  your hand size).
  - Guess exactly right: score the tricks you won, plus a 10-point bonus.
  - Miss your bet: score only the tricks you actually won.
- **Playing a trick**: follow the suit that was led if you're able to. Can't
  follow suit? Play any card, including trump. The highest trump played wins
  the trick; with no trump played, the highest card of the led suit wins. You
  can't lead trump until it's been "broken" earlier in the round, unless your
  whole hand is trump.
- **On-screen indicators**: a black dot beside a name marks that trick's
  current leader, a "Led: [suit]" line shows the suit led in the trick
  underway, and a gold ring marks the highest card played so far in the trick.

The full rules are also available in-game via the Rules button on the start
screen and during play.

## Requirements

- JDK 21 (developed against Eclipse Temurin 21.0.11.10)
- No build tool required — the project compiles with plain `javac`. JUnit and
  Hamcrest jars are vendored under `lib/` for running tests.

## Install

Clone the repository and check out the `overhaul` branch, where active
development happens (`main` is a legacy branch, not kept up to date):

```
git clone https://github.com/jlabowitz/TenToOne.git
cd TenToOne
git checkout overhaul
```

## Build

```
"/path/to/jdk-21/bin/javac" -cp "lib/*" -d build src/*.java
```

On Windows, using the JDK location this project was developed against:

```
"/c/Program Files/Eclipse Adoptium/jdk-21.0.11.10-hotspot/bin/javac.exe" -cp "lib/*" -d build src/*.java
```

## Run

```
"/path/to/jdk-21/bin/java" -cp "build;lib/*" Game
```

On Windows:

```
"/c/Program Files/Eclipse Adoptium/jdk-21.0.11.10-hotspot/bin/java.exe" -cp "build;lib/*" Game
```

(On macOS/Linux, use `build:lib/*` instead of `build;lib/*` as the classpath
separator.)

## Test

Tests are JUnit 4, run via `JUnitCore` — there's no test runner script:

```
"/path/to/jdk-21/bin/java" -cp "build;lib/*" org.junit.runner.JUnitCore TestBetStepper TestCard TestGame TestHand TestHandler TestHumanBet TestHumanIllegalReason TestIllegalPlayFeedback TestKeyInput TestNextTrickPrompt TestPlayer TestRound TestRulesView TestStartScreen TestTrick TestWindowSizing
```

## Project status

This is a personal, actively evolving project — not a shipping product. See
[ROADMAP.md](ROADMAP.md) for planned work and [DONE.md](DONE.md) for a
history of completed features.

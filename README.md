# Overlord's Labyrinth — CodinGame Multiplayer RPG SDK Project

Un puzzle multijoueur RPG tactique inspiré de **Wizardry IV: The Return of Werdna** (invocation sur pentagrammes) et **Conan Overlord** (allocation de gemmes d'énergie entre invocation et double activation), avec bascule dynamique PixiJS entre **Mode Sprites RPG 2D** et **Mode Formes Géométriques Debug** (`ToggleModule`).

## Commandes Rapides
* **Viewer Web Local (`http://localhost:8888/test.html`)** :
  ```bash
  cd overlords-labyrinth-sdk
  mvn test-compile exec:java -Dexec.mainClass="Main" -Dexec.classpathScope="test"
  ```
* **Simulation Batch (3 ligues)** :
  ```bash
  cd overlords-labyrinth-sdk
  mvn test-compile exec:java -Dexec.mainClass="Main" -Dexec.classpathScope="test" -Dexec.args="--simulate"
  ```

## CodinGame & GitHub Links

- **CodinGame Contribution**: https://www.codingame.com/contribute/view/1586818a88014df499301a39a4dbd615c75dfe
- **Play in CodinGame IDE**: https://www.codingame.com/ide/demo/16057779640832b6c2b793a6dd8f1b789d4447d
- **GitHub Repository**: https://github.com/codingame-team/codingame-overlords-labyrinth
- **6-Puzzle Showcase Hub (GitHub Pages)**: https://codingame-team.github.io/codingame-sdk-puzzles/

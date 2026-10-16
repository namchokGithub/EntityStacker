# AGENTS.md — EntityStacker

## Project context

EntityStacker is a Minecraft Java Edition **1.20.1** mod built with **Minecraft Forge (47.x)**, **Java 17**, and the **Gradle Wrapper from Forge MDK**. It targets both dedicated servers and singleplayer, especially heavily modded environments with 3–6 players.

- Mod ID: `entitystacker`
- Display name: `EntityStacker`
- Intended Java package: `dev.namchok.entitystacker` (confirm with the existing source before changing it)
- Development OS: Windows; prefer PowerShell-friendly commands.
- Current milestone: **Phase 1 — setup, rename the MDK example mod, and verify client/server startup.** The development client has already started successfully once. Do not assume the rename or dedicated-server validation is finished.

## Collaboration rules

1. **Work one small step at a time.** Explain what you plan to change, then make only the change requested. Do not implement later phases in advance.
2. Keep explanations concise and beginner-friendly, in **Thai**. Keep code, identifiers, filenames, and commit messages in English.
3. Before editing, inspect the actual repository structure, `gradle.properties`, `build.gradle`, `mods.toml`, source files, and any relevant existing code. Do not assume MDK filenames or package paths.
4. Confirm APIs and signatures against **Minecraft Forge 1.20.1** and the dependencies in this repo. Do not use **NeoForge**, Fabric, or APIs from other Minecraft versions. If verification is not possible, state the uncertainty instead of inventing APIs.
5. Prefer minimal changes, established Forge practices, and clear separation of responsibilities. Do not add libraries, change Forge/Gradle/Java versions, or introduce new frameworks without asking.
6. Never silently delete or replace user code, config, saves, generated worlds, or unrelated files. Ask before destructive actions or large refactors.
7. When a task is completed, report: **changed files**, **what changed**, **verification run/results**, and **the next suggested small step**. Clearly distinguish tests that were run from tests that were not run.
8. Treat security, performance, and mod compatibility as design constraints—not reasons to build speculative complexity before it is needed.

## Build and development commands

Use the repository's Gradle Wrapper (do not require a global Gradle installation):

```powershell
.\gradlew.bat --version
.\gradlew.bat runClient
.\gradlew.bat runServer
.\gradlew.bat build
```

Use **JDK 17** for Gradle and game development, even if the operating system defaults to a newer Java version. The user's JDK 17 is installed under the local AppData Eclipse Adoptium directory; do not hard-code a user-specific absolute path. If the Java runtime is wrong, explain how to configure `JAVA_HOME` for the current terminal or IDE rather than changing machine-wide settings without permission.

Only claim a command succeeded if it was actually executed and its result was checked. Never run a destructive command against an existing world. Use disposable test worlds.

## Architecture goals (not a request to implement now)

Keep two distinct systems:

- **Item stacking:** merge nearby compatible dropped items into fewer real item entities, while representing logical counts above normal per-slot limits. Preserve exact item identity, tags/NBT, damage and enchantments; ensure normal inventory pickup semantics and avoid loss or duplication.
- **Mob stacking:** initially support only explicitly allowed vanilla passive mobs (start with cow, then chicken). Adopt **Balanced mode**: merge only compatible, safe entities; keep one representative entity and logical records for the others; selectively unstack before interactions that need an individual mob.

Mob death and loot decisions:

- **One mob dies at a time**; decrement logical count by one.
- Produce **loot and XP for only the mob that actually died**, respecting normal game conditions and relevant mod hooks as far as feasible.
- Do not simulate death with a simple cancelled event and arbitrary item spawning without proving lifecycle correctness.
- Representative replacement vs. cancellation is **not finalized**: verify event order, save/load behavior, and failure cases before implementing it.
- For breeding, feeding, milking, shearing, babies, names, effects, non-full health, leashes, passengers, modded capability data, and other individual states, prefer **skip stacking or selective unstacking** over data loss.

## Data integrity and performance invariants

- The **server is authoritative** for counts, merging, unstacking, deaths, loot, and pickup. Client code only displays synchronized information.
- Never lose or duplicate items or mobs. Define and test count conservation for merge/unstack/pickup/death.
- Preserve essential state through save/reload; do not assume entity persistent data, capabilities, or `SavedData` automatically synchronize to the client or save atomically together.
- Stay on the Minecraft server thread for world/entity mutation. Do not access or force-load unloaded chunks.
- Avoid scanning all entities every tick; use scheduled, budgeted local scans and benchmark before claiming TPS improvements.
- Design storage behind an interface so `PersistentData`, Forge Capability, or other persistence mechanisms can be evaluated without entangling business logic.
- Restrict unsupported or unknown modded entity data by default rather than silently discarding it.

## Development phases

1. **Setup:** configure MDK; change example mod identity to `entitystacker`; verify `runClient`, `runServer`, and `build`.
2. **Item MVP:** compatibility rules, nearby merge, logical count, pickup, display, persistence, config.
3. **Passive mob MVP:** cow then chicken; safe merge/unstack; one-at-a-time death; loot/XP; save/load.
4. **Interactions:** feeding, breeding, milking, shearing and stateful edge cases.
5. **Optimization and compatibility:** benchmark TPS/MSPT/entity count, multiplayer, modpack tests and bug fixes.
6. **Release:** build JAR, dedicated server validation, documentation and versioning.

**Do not move to a later phase or generate a full implementation unless explicitly requested.**

## Execution & Testing Restrictions

- Do NOT run, build, compile, test, or launch the project.
- Do NOT execute Gradle commands, including `runClient`, `runServer`, `build`, or `test`.
- Do NOT start Minecraft or any development server.
- The user is responsible for all builds, tests, and runtime verification.
- Only modify or review code as explicitly requested.
- After making changes, summarize the modified files and provide suggested verification steps.
- Never claim that code has been tested or verified unless the user provides test result
- Run the narrowest relevant check first; run `build` for Java/config changes when practical.
- Test client and dedicated server independently when changes affect registration, sided code or networking.
- Test save/reload, chunk unload/reload, multiplayer, repeated interactions, and duplicate/loss scenarios before labeling a stacking feature stable.
- For performance comparisons, use repeatable scenarios and report **MSPT, TPS, entity count, and hardware/test conditions**; do not infer optimization merely from fewer rendered entities.

## Current next step

Inspect the actual source files and reconcile the Forge example mod identity: `gradle.properties` mod ID, `@Mod` annotation / constant, Java class/package/constructor, `mods.toml`, and any references. Make the smallest coordinated rename needed, then verify with `runClient` and `build`. Ask for clarification before replacing an already customized structure.

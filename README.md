# Mind-Robot

A Minecraft **1.7.10** mod built on **Forge 10.13.4.1614**.

| | |
|---|---|
| Mod ID | `mindrobot` |
| Java package | `com.github.qianwj.mindrobot` |
| Main class | `MindRobot` |

## Requirements

- Minecraft 1.7.10
- Forge 10.13.4.1614

## Building

The project uses the [GTNH](https://github.com/GTNewHorizons/ExampleMod1.7.10) build system
(Kotlin DSL Gradle scripts, `gtnhsettingsconvention`, RetroFuturaGradle). All build settings live in
`gradle.properties`; the code is compiled at Java 8 bytecode level with modern syntax enabled via Jabel.

```bash
./gradlew build        # Windows: gradlew.bat build
```

The Gradle wrapper (9.7.1) provisions its own daemon JVM, and the first build decompiles Minecraft,
so expect several minutes. Artifacts land in `build/libs/`.

> Version numbers come from Git tags. Without a repository tag the build falls back to
> `NO-GIT-TAG-SET`; tag the repository (for example `0.1.0`) before publishing a jar.

## Installing

Put the reobfuscated jar (the one **without** the `-dev` suffix) into `mods/` of a Forge 1.7.10
client or server. Use `-dev` jars only inside the development environment.

## License

See [LICENSE](LICENSE).

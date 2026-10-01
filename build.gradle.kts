import java.util.zip.ZipFile

plugins {
    id("net.fabricmc.fabric-loom-remap") version "1.18.2"
}

val minecraftVersion = providers.gradleProperty("minecraft_version").get()
val yarnMappings = providers.gradleProperty("yarn_mappings").get()
val loaderVersion = providers.gradleProperty("loader_version").get()
val fabricApiVersion = providers.gradleProperty("fabric_api_version").get()
val sparkVersion = providers.gradleProperty("spark_version").get()

version = providers.gradleProperty("mod_version").get()
group = providers.gradleProperty("maven_group").get()

base {
    archivesName = providers.gradleProperty("archives_base_name")
}

repositories {
    mavenCentral()
    exclusiveContent {
        forRepository { maven("https://api.modrinth.com/maven") { name = "Modrinth" } }
        filter { includeGroup("maven.modrinth") }
    }
}

// Server-side game tests live in their own source set and their own dev-only mod, so nothing of them ships.
val gametest: SourceSet = sourceSets.create("gametest") {
    compileClasspath += sourceSets.main.get().compileClasspath + sourceSets.main.get().output
    runtimeClasspath += sourceSets.main.get().runtimeClasspath + sourceSets.main.get().output
}

loom {
    // common code cannot see client classes at compile time: src/main is server-safe by construction
    splitEnvironmentSourceSets()

    mods {
        register("evoluta") {
            sourceSet(sourceSets.main.get())
            sourceSet(sourceSets.getByName("client"))
        }
        register("evoluta-gametest") {
            sourceSet(gametest)
        }
    }

    runs {
        register("gametest") {
            server()
            displayName = "Game Test"
            jvmArguments.add("-Dfabric-api.gametest")
            jvmArguments.add("-Dfabric-api.gametest.report-file=${layout.buildDirectory.file("gametest/junit.xml").get().asFile}")
            // fuzz knobs: ./gradlew runGametest -Pevoluta.fuzz.cycles=20000 -Pevoluta.fuzz.seed=42 (-Pevoluta.fuzz.only=N replays one case)
            for (knob in listOf("evoluta.fuzz.cycles", "evoluta.fuzz.seed", "evoluta.fuzz.only")) {
                providers.gradleProperty(knob).orNull?.let { jvmArguments.add("-D$knob=$it") }
            }
            runDirectory = layout.buildDirectory.dir("gametest")
            sourceSet = gametest.name
        }
    }
}

// The dev client also loads a data-only dev mod whose /function evoluta_showcase:scene sets up the showcase scene.
val showcase: SourceSet = sourceSets.create("showcase") {
    val client = sourceSets.getByName("client")
    compileClasspath += client.compileClasspath + client.output
    runtimeClasspath += client.runtimeClasspath + client.output
}
// the game tests run the scene function on the test server, so a broken scene fails the build
gametest.runtimeClasspath += showcase.output

loom {
    // mod dependencies for the showcase source set alone, so they reach the dev client and nothing else
    createRemapConfigurations(showcase)
    mods {
        register("evoluta-showcase") {
            sourceSet(showcase)
        }
    }
    runs {
        named("client") {
            sourceSet = showcase.name
        }
    }
}

dependencies {
    minecraft("com.mojang:minecraft:$minecraftVersion")
    mappings("net.fabricmc:yarn:$yarnMappings:v2")
    modImplementation("net.fabricmc:fabric-loader:$loaderVersion")
    modImplementation("net.fabricmc.fabric-api:fabric-api:$fabricApiVersion")

    modLocalRuntime("maven.modrinth:spark:$sparkVersion")
    // spark nests this in its jar, but a dev runtime does not unpack nested jars
    modLocalRuntime("me.lucko:fabric-permissions-api:0.3.1")

    // the dev client only, to film the showcase with shaders: never in tests, never a dependency of the mod
    "modShowcaseRuntimeOnly"("maven.modrinth:sodium:mc1.21.1-0.8.13-fabric")
    "modShowcaseRuntimeOnly"("maven.modrinth:iris:1.8.14-beta.1+1.21.1-fabric")
    // Iris nests these three libraries; a dev runtime does not unpack nested jars
    "showcaseRuntimeOnly"("org.antlr:antlr4-runtime:4.13.1")
    "showcaseRuntimeOnly"("io.github.douira:glsl-transformer:3.0.0-pre3")
    "showcaseRuntimeOnly"("org.anarres:jcpp:1.4.14")

    // runProductionClient: the jars exactly as players get them (the Fabric API bundle unpacks its own modules)
    "productionRuntimeMods"("net.fabricmc.fabric-api:fabric-api:$fabricApiVersion")
    "productionRuntimeMods"("maven.modrinth:sodium:mc1.21.1-0.8.13-fabric")
    "productionRuntimeMods"("maven.modrinth:iris:1.8.14-beta.1+1.21.1-fabric")

    testImplementation("net.fabricmc:fabric-loader-junit:$loaderVersion")
    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    toolchain { languageVersion = JavaLanguageVersion.of(21) }
    withSourcesJar()
}

tasks.withType<JavaCompile>().configureEach {
    options.release = 21
    options.encoding = "UTF-8"
}

tasks.processResources {
    val modVersion = project.version.toString()
    inputs.property("version", modVersion)
    filesMatching("fabric.mod.json") { expand("version" to modVersion) }
}

configurations.named("productionRuntimeMods") { isTransitive = false }

// The showcase scene as a plain data jar, for the production client below.
val showcaseJar = tasks.register<Jar>("showcaseJar") {
    archiveClassifier = "showcase"
    destinationDirectory = layout.buildDirectory.dir("devlibs")
    from(showcase.output)
}

// The client as a player runs it: intermediary Minecraft, Fabric Loader's production path and the remapped Evoluta
// jar, plus Sodium, Iris and the showcase scene. Unlike runClient it never goes through the dev-time mixin remapper,
// whose null-descriptor bug in Loader 0.19.5 stops Iris there. Offline token: singleplayer only.
tasks.register<net.fabricmc.loom.task.prod.ClientProductionRunTask>("runProductionClient") {
    group = "fabric"
    description = "Runs the production client with the remapped jar, Fabric API, Sodium, Iris and the showcase scene."
    mods.from(showcaseJar)
    programArgs.addAll("--accessToken", "0", "--version", minecraftVersion)
    javaLauncher = javaToolchains.launcherFor { languageVersion = JavaLanguageVersion.of(21) }
}

tasks.test {
    useJUnitPlatform()
    // the same fuzz knobs as the game tests, for the config fuzz: -Pevoluta.fuzz.cycles=200000 -Pevoluta.fuzz.seed=42
    for (knob in listOf("evoluta.fuzz.cycles", "evoluta.fuzz.seed")) {
        providers.gradleProperty(knob).orNull?.let { systemProperty(knob, it) }
    }
    systemProperty("mixin.debug.countInjections", "true")
    // fabric-loader-junit treats the working directory as the game directory (mods/, config/, logs/): keep it in build/
    val gameDir = layout.buildDirectory.dir("junit").get().asFile
    workingDir = gameDir
    doFirst { gameDir.mkdirs() }
}

// The game tests boot a headless 1.21.1 server (Fabric skips the EULA prompt for test runs), run every
// @GameTest and exit non-zero on any failure; build/gametest/junit.xml keeps the per-test verdicts.
tasks.named("check") { dependsOn("runGametest") }

// What players download: the remapped jar. It must hold the mod and nothing of the dev tooling.
val verifyJar = tasks.register("verifyJar") {
    group = "verification"
    description = "Checks the remapped jar: mod descriptor, mixin configs, no dev-only classes, no unremapped names."
    val jar = tasks.named<org.gradle.jvm.tasks.Jar>("remapJar").flatMap { it.archiveFile }
    val expectedVersion = project.version.toString()
    inputs.file(jar).withPropertyName("jar")
    doLast {
        val file: File = jar.get().asFile
        val problems = mutableListOf<String>()
        ZipFile(file).use { zip ->
            val entries = zip.entries().toList()
            val names = entries.filter { !it.isDirectory }.map { it.name }
            fun text(name: String): String? = zip.getEntry(name)?.let { String(zip.getInputStream(it).readAllBytes(), Charsets.UTF_8) }
            val descriptor = text("fabric.mod.json")
            if (descriptor == null) {
                problems.add("no fabric.mod.json")
            } else {
                if (!Regex("\"id\"\\s*:\\s*\"evoluta\"").containsMatchIn(descriptor)) problems.add("fabric.mod.json does not declare id evoluta")
                if (!Regex("\"version\"\\s*:\\s*\"${Regex.escape(expectedVersion)}\"").containsMatchIn(descriptor)) {
                    problems.add("fabric.mod.json does not carry version $expectedVersion")
                }
                // every mixin config the descriptor names must ship, with the refmap it points at
                for (config in Regex("\"([\\w.-]+\\.mixins\\.json)\"").findAll(descriptor).map { it.groupValues[1] }.toSet()) {
                    val mixins = text(config)
                    if (mixins == null) {
                        problems.add("fabric.mod.json names $config, which is not in the jar")
                        continue
                    }
                    Regex("\"refmap\"\\s*:\\s*\"([^\"]+)\"").find(mixins)?.groupValues?.get(1)?.let { refmap ->
                        if (refmap !in names) problems.add("$config points at $refmap, which is not in the jar")
                    }
                }
            }
            val strays = names.filter {
                it.startsWith("com/nyr/evoluta/gametest/") || it.startsWith("data/evoluta_showcase/") ||
                        it.contains("spark", ignoreCase = true) || it.contains("iris", ignoreCase = true) || it.contains("sodium", ignoreCase = true)
            }
            if (strays.isNotEmpty()) problems.add("carries dev-only files: $strays")
            // a player's game only has intermediary names: a Yarn name left in a class (a mixin target, say) fails there,
            // never in the dev runs that use Yarn names. These are the only 1.21.1 classes intermediary leaves named.
            val yarnName = Regex("net/minecraft/(?!class_|client/ClientBrandRetriever|client/main/Main|data/Main|obfuscate/DontObfuscate" +
                    "|server/Main|server/MinecraftServer|util/profiling/jfr/event/)[a-z]")
            for (entry in entries.filter { it.name.endsWith(".class") }) {
                val text = String(zip.getInputStream(entry).readAllBytes(), Charsets.ISO_8859_1)
                yarnName.find(text)?.let { problems.add("${entry.name} still names ${text.substring(it.range.first, minOf(text.length, it.range.first + 48)).substringBefore(';')}") }
            }
            if (problems.isNotEmpty()) throw GradleException("${file.name}:\n  " + problems.joinToString("\n  "))
            logger.lifecycle("verified ${file.name}: ${names.size} entries, ${file.length() / 1024} KiB")
        }
    }
}
tasks.named("remapJar") { finalizedBy(verifyJar) }

// Client rendering helpers are tested without opening a game window.
sourceSets.named("test") {
    val client = sourceSets.getByName("client")
    compileClasspath += client.output + client.compileClasspath
    runtimeClasspath += client.output + client.runtimeClasspath
}

plugins {
    id("java-library")
    id("net.neoforged.moddev") version "2.0.147"
}

version = property("mod_version") as String
group = property("mod_group_id") as String

base {
    archivesName.set(property("mod_id") as String)
}

java.toolchain.languageVersion.set(JavaLanguageVersion.of(21))

sourceSets.main.get().resources {
    srcDir("src/generated/resources")
    exclude("**/.cache/**")
}

neoForge {
    version = property("neo_version") as String

    runs {
        create("client") {
            client()
            systemProperty("neoforge.enabledGameTestNamespaces", property("mod_id") as String)
        }
        create("server") {
            server()
            programArgument("--nogui")
            systemProperty("neoforge.enabledGameTestNamespaces", property("mod_id") as String)
        }
        create("gameTestServer") {
            type = "gameTestServer"
            systemProperty("neoforge.enabledGameTestNamespaces", property("mod_id") as String)
        }
        create("data") {
            data()
            programArguments.addAll(
                "--mod", property("mod_id") as String,
                "--all",
                "--output", file("src/generated/resources").absolutePath,
                "--existing", file("src/main/resources").absolutePath
            )
        }
        configureEach {
            systemProperty("forge.logging.markers", "REGISTRIES")
        }
    }

    mods {
        create(property("mod_id") as String) {
            sourceSet(sourceSets.main.get())
        }
    }
}

repositories {
    maven("https://maven.createmod.net")            // Create, Ponder, Flywheel
    maven("https://maven.ithundxr.dev/snapshots")   // Registrate
    maven("https://maven.blamejared.com")           // JEI
    maven("https://maven.kosmx.dev")                // playerAnimator
    exclusiveContent {                              // Distant Horizons
        forRepository { maven("https://api.modrinth.com/maven") }
        filter { includeGroup("maven.modrinth") }
    }
}

dependencies {
    implementation("com.simibubi.create:create-${property("minecraft_version")}:${property("create_version")}:slim") { isTransitive = false }
    implementation("net.createmod.ponder:ponder-neoforge:${property("ponder_version")}+mc${property("minecraft_version")}")
    compileOnly("dev.engine-room.flywheel:flywheel-neoforge-api-${property("minecraft_version")}:${property("flywheel_version")}")
    runtimeOnly("dev.engine-room.flywheel:flywheel-neoforge-${property("minecraft_version")}:${property("flywheel_version")}")
    implementation("com.tterrag.registrate:Registrate:${property("registrate_version")}")

    compileOnly("mezz.jei:jei-${property("minecraft_version")}-neoforge-api:${property("jei_version")}")
    runtimeOnly("mezz.jei:jei-${property("minecraft_version")}-neoforge:${property("jei_version")}")
    // Veil (bundled in Sable / Create Aeronautics): coloured dynamic lights, only when it is there.
    compileOnly("foundry.veil:veil-neoforge-${property("minecraft_version")}:4.3.2") { isTransitive = false }
    // playerAnimator (in the pack): drugged body poses, only when it is there.
    compileOnly("dev.kosmx.player-anim:player-animation-lib-forge:2.0.4+1.21.1") { isTransitive = false }
    // Distant Horizons (in the pack): the far landscape's depth, only when it is there.
    compileOnly("maven.modrinth:distanthorizons:3.2.0-b-1.21.1") { isTransitive = false }

    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    // Required to actually launch the JUnit Platform. Without it, the first unit
    // test in the repo fails with "Could not start Gradle Test Executor 1: Failed
    // to load JUnit Platform." Gradle does not pull this in implicitly.
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.10.2")
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}

tasks.processResources {
    val props = mapOf("mod_version" to project.version.toString())
    inputs.properties(props)
    filesMatching("META-INF/neoforge.mods.toml") { expand(props) }
}

tasks.test {
    useJUnitPlatform()
}

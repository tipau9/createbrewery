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
    maven("https://dl.cloudsmith.io/public/geckolib3/geckolib/maven/") // GeckoLib
    maven("https://maven.maxhenkel.de/repository/public")              // Simple Voice Chat API
    maven("https://maven.minecraftforge.net")                          // Tough As Nails, GlitchCore
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
    // GeckoLib (in the pack): animated hallucinations, only when it is there.
    compileOnly("software.bernie.geckolib:geckolib-neoforge-${property("minecraft_version")}:4.9.3") { isTransitive = false }
    // Simple Voice Chat (in the pack): your voice on drugs, only when it is there.
    compileOnly("de.maxhenkel.voicechat:voicechat-api:2.5.36")
    // Tough As Nails (in the pack): body temperature and thirst, only when it is there.
    compileOnly("com.github.glitchfiend:ToughAsNails-neoforge:${property("minecraft_version")}-10.1.0.3") { isTransitive = false }
    compileOnly("com.github.glitchfiend:GlitchCore-neoforge:${property("minecraft_version")}-2.1.0.2") { isTransitive = false }

    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    // MP3 decoding for the drop trainer (DropTrainer, ./gradlew trainDrops); tests only, not in the mod.
    testImplementation("javazoom:jlayer:1.0.1")
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

// Tunes DropDetector.DEFAULT on your own songs: ./gradlew trainDrops --args="C:/path/to/songs"
tasks.register<JavaExec>("trainDrops") {
    group = "verification"
    description = "Learns the drop detection settings from MP3s and their drops.txt"
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("com.createbrewery.drunk.DropTrainer")
    workingDir = projectDir
}

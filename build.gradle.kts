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
}

dependencies {
    implementation("com.simibubi.create:create-${property("minecraft_version")}:${property("create_version")}:slim") { isTransitive = false }
    implementation("net.createmod.ponder:ponder-neoforge:${property("ponder_version")}+mc${property("minecraft_version")}")
    compileOnly("dev.engine-room.flywheel:flywheel-neoforge-api-${property("minecraft_version")}:${property("flywheel_version")}")
    runtimeOnly("dev.engine-room.flywheel:flywheel-neoforge-${property("minecraft_version")}:${property("flywheel_version")}")
    implementation("com.tterrag.registrate:Registrate:${property("registrate_version")}")

    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}

tasks.test {
    useJUnitPlatform()
}

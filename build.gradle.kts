plugins {
    id("java-library")
    id("com.gradleup.shadow") version "9.6.1"
    id("xyz.jpenilla.run-paper") version "3.1.0"
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.extendedclip.com/releases/")
}

val brigadierSourceSet = sourceSets.create("brigadier") {
    java.srcDir("src/brigadier/java")
    compileClasspath += sourceSets.main.get().output
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.18.2-R0.1-SNAPSHOT")
    add("brigadierCompileOnly", "io.papermc.paper:paper-api:1.20.6-R0.1-SNAPSHOT")
    implementation("org.bstats:bstats-bukkit:3.2.1")
    compileOnly("me.clip:placeholderapi:2.12.3")

    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("io.papermc.paper:paper-api:1.18.2-R0.1-SNAPSHOT")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks {
    compileJava {
        options.release = 17
    }

    compileTestJava {
        options.release = 17
    }

    named<JavaCompile>(brigadierSourceSet.compileJavaTaskName) {
        options.release = 21
    }

    jar {
        enabled = false
    }

    build {
        dependsOn(shadowJar)
    }

    test {
        useJUnitPlatform()
    }

    runServer {
        val paperVersion = providers.gradleProperty("paperVersion").orElse("26.3").get()
        minecraftVersion(paperVersion)
        runDirectory.set(layout.projectDirectory.dir("run/$paperVersion"))
        javaLauncher = project.javaToolchains.launcherFor {
            languageVersion = JavaLanguageVersion.of(
                providers.gradleProperty("paperJavaVersion").orElse("25").get().toInt())
        }
        jvmArgs("-Xms2G", "-Xmx2G", "-Dcom.mojang.eula.agree=true")

        downloadPlugins {
            hangar("PlaceholderAPI", "2.12.3")
        }
    }

    shadowJar {
        from(brigadierSourceSet.output)
        archiveClassifier.set("")
        configurations = project.configurations.runtimeClasspath.map { setOf(it) }

        dependencies {
            exclude { it.moduleGroup != "org.bstats" }
        }

        relocate("org.bstats", "dev.kwlew.haven.lib.bstats")
    }

    processResources {
        val props = mapOf("version" to version, "description" to project.description)
        filesMatching("plugin.yml") {
            expand(props)
        }
    }
}

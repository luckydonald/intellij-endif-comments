import org.gradle.process.CommandLineArgumentProvider
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.4.0"
    id("org.jetbrains.intellij.platform") version "2.18.1"
}

group = providers.gradleProperty("pluginGroup").get()
version = providers.gradleProperty("pluginVersion").getOrElse("0.1.0")

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
    // Home of the `remote-robot`/`remote-fixtures` artifacts used by the `uiTest` UI test.
    maven("https://packages.jetbrains.team/maven/p/ij/intellij-dependencies")
}

val remoteRobotVersion = "0.11.23"

// Dedicated source set for the `intellij-ui-test-robot` UI smoke test: it's a *client* that talks
// to a separately-launched IDE process over HTTP, so it must not share a classpath/JVM with the
// regular unit tests (which run inside the IDE fixture itself).
sourceSets {
    create("uiTest") {
        kotlin.srcDir("src/uiTest/kotlin")
        compileClasspath += sourceSets.main.get().output
        runtimeClasspath += sourceSets.main.get().output
    }
}

val uiTestImplementation: Configuration = configurations.getByName("uiTestImplementation") {
    extendsFrom(configurations.getByName("implementation"))
}
configurations.getByName("uiTestRuntimeOnly").extendsFrom(configurations.getByName("runtimeOnly"))

dependencies {
    intellijPlatform {
        val platformType = providers.gradleProperty("platformType")
        val platformVersion = providers.gradleProperty("platformVersion")
        create(platformType, platformVersion)

        // PythonCore is the Python-support plugin bundled inside PyCharm CE, PyCharm Professional,
        // and IntelliJ Ultimate once the (free) Python plugin is installed — depending on it alone
        // (not the Professional-only `Pythonid`) keeps this plugin loadable in all three.
        bundledPlugin("PythonCore")

        pluginVerifier()
        zipSigner()
        testFramework(org.jetbrains.intellij.platform.gradle.TestFrameworkType.Platform)
    }

    testImplementation("junit:junit:4.13.2")

    uiTestImplementation(kotlin("stdlib"))
    uiTestImplementation("com.intellij.remoterobot:remote-robot:$remoteRobotVersion")
    uiTestImplementation("com.intellij.remoterobot:remote-fixtures:$remoteRobotVersion")
    uiTestImplementation("junit:junit:4.13.2")
}

intellijPlatform {
    // The bytecode instrumentation task (for @NotNull param assertions) hits an Ant/Groovy bug
    // under very new JDKs; this plugin's Kotlin code doesn't rely on Java @NotNull instrumentation.
    instrumentCode = false

    // Only one non-searchable checkbox in Settings — skip indexing it for the Settings search box.
    buildSearchableOptions = false

    pluginConfiguration {
        id = providers.gradleProperty("pluginGroup")
        name = providers.gradleProperty("pluginName")
        version = project.version.toString()

        ideaVersion {
            sinceBuild = providers.gradleProperty("pluginSinceBuild")
            untilBuild = provider { null }
        }
    }
}

// Launches a sandboxed IDE with our plugin and the `robot-server` companion plugin installed, so
// `uiTest` (below) can drive it over HTTP. Run as `./gradlew runIdeForUiTests &`, then `./gradlew
// uiTest`, matching the intellij-ui-test-robot Quick Start.
val runIdeForUiTests = intellijPlatformTesting.runIde.register("runIdeForUiTests") {
    task {
        jvmArgumentProviders += CommandLineArgumentProvider {
            listOf(
                "-Drobot-server.port=8082",
                "-Djb.consents.confirmation.enabled=false",
                "-Didea.trust.all.projects=true",
                "-Dide.show.tips.on.startup.default.value=false",
            )
        }
    }
    plugins {
        robotServerPlugin()
    }
}

val uiTest = tasks.register<Test>("uiTest") {
    description = "Runs the intellij-ui-test-robot smoke test against an IDE started by runIdeForUiTests."
    group = "verification"
    testClassesDirs = sourceSets["uiTest"].output.classesDirs
    classpath = sourceSets["uiTest"].runtimeClasspath
    useJUnit()
}

kotlin {
    jvmToolchain(21)
}

tasks {
    withType<KotlinCompile> {
        compilerOptions {
            freeCompilerArgs.add("-Xjvm-default=all")
        }
    }
}

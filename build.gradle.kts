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
}

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
            untilBuild = providers.gradleProperty("pluginUntilBuild")
        }
    }
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

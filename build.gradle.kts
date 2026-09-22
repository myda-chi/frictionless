import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.intellij.platform")
    id("org.jetbrains.changelog")
}


kotlin {
    jvmToolchain(21)
}


intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            sinceBuild = "252"
        }
    }
}

val localIdePath = providers.gradleProperty("localIdePath").orNull?.takeIf { it.isNotBlank() }

dependencies {
    // Koog: JetBrains' Kotlin agent framework. The brief names it directly.
    //
    // Coroutines are excluded on purpose. The IntelliJ Platform ships a patched fork of
    // kotlinx-coroutines (it adds runBlockingWithParallelismCompensation, which the service
    // container calls); letting Koog drag in the vanilla artifact shadows that fork and every
    // platform test dies with NoSuchMethodError. The plugin gets coroutines from the platform.
    implementation("ai.koog:koog-agents:1.2.0") {
        exclude(group = "org.jetbrains.kotlinx", module = "kotlinx-coroutines-core")
        exclude(group = "org.jetbrains.kotlinx", module = "kotlinx-coroutines-core-jvm")
        exclude(group = "org.jetbrains.kotlinx", module = "kotlinx-coroutines-jdk8")
        exclude(group = "org.jetbrains.kotlinx", module = "kotlinx-coroutines-slf4j")
    }

    testImplementation("junit:junit:4.13.2")

    // IntelliJ Platform Gradle Plugin Dependencies Extension - read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-dependencies-extension.html
    intellijPlatform {
        if (localIdePath != null) {
            local(localIdePath)
        } else {
            intellijIdea(providers.gradleProperty("platformVersion"))
        }
        bundledPlugin("Git4Idea")
        testFramework(TestFrameworkType.Platform)

        // E1 builds and launches a JUnitConfiguration, which lives in the bundled Java and JUnit
        // plugins rather than the base platform - see plugin.xml for the matching runtime <depends>.
        bundledPlugin("com.intellij.java")
        bundledPlugin("JUnit")
    }
}

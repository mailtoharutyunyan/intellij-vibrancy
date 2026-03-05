plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "1.9.23"
    id("org.jetbrains.intellij.platform") version "2.0.0-rc1"
}

val pluginGroup: String by project
val pluginVersion: String by project
val platformVersion: String by project
val platformSinceBuild: String by project

group = pluginGroup
version = pluginVersion

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
        intellijDependencies()
    }
}

dependencies {
    intellijPlatform {
        intellijIdeaCommunity(platformVersion)
        instrumentationTools()
    }
}

kotlin {
    jvmToolchain(17)
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            sinceBuild = platformSinceBuild
            untilBuild = provider { null }
        }
    }
}

afterEvaluate {
    tasks.withType<JavaExec> {
        if (name == "runIde") {
            jvmArgs(
                "-Dsun.java2d.metal=false",
                "-Dsun.java2d.opengl=true",
                "-Dsun.java2d.opengl.fbobject=true"
            )
        }
    }
}

tasks {
    wrapper {
        gradleVersion = "8.5"
    }
}

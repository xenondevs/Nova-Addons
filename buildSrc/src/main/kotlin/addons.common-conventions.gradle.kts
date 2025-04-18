import org.gradle.accessors.dm.LibrariesForLibs

group = "xyz.xenondevs.nova.addon"

plugins {
    `java-library`
    kotlin("jvm")
    id("io.papermc.paperweight.userdev")
    id("xyz.xenondevs.nova.nova-gradle-plugin")
}

val libs = the<LibrariesForLibs>()

repositories {
    mavenLocal { content { includeGroupAndSubgroups("xyz.xenondevs") } }
    gradlePluginPortal()
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.xenondevs.xyz/releases")
}

dependencies {
    paperweight.paperDevBundle(libs.versions.paper.get())
    implementation(libs.nova)
}

kotlin {
    compilerOptions {
        optIn.add("xyz.xenondevs.invui.ExperimentalReactiveApi")
    }
}

java {
    withSourcesJar()
}

addon {
    val outDir = project.findProperty("outDir")
    if (outDir is String)
        destination.set(File(outDir))
}

afterEvaluate {
    // remove "dev" classifier set by paperweight-userdev
    tasks.getByName<Jar>("jar") {
        archiveClassifier = ""
    }
}
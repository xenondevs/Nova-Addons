
import org.gradle.accessors.dm.LibrariesForLibs
import xyz.xenondevs.novagradle.task.AddonJarTask

group = "xyz.xenondevs.nova.addon"

plugins {
    `java-library`
    kotlin("jvm")
    id("io.papermc.paperweight.userdev")
    id("xyz.xenondevs.nova.nova-gradle-plugin")
    id("xyz.xenondevs.publish.plugin-publish")
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

tasks {
    jar {
        archiveClassifier = "intermediate"
    }
}

addon {
    val outDir = project.findProperty("outDir")
    if (outDir is String)
        destination = File(outDir)
}

pluginPublish {
    file = tasks.named<AddonJarTask>("addonJar").flatMap { it.output }
    githubRepository = "xenondevs/Nova-Addons"
}
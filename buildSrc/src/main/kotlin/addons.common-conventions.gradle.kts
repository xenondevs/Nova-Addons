
import org.gradle.accessors.dm.LibrariesForLibs

group = "xyz.xenondevs.nova.addon"

plugins {
    `java-library`
    kotlin("jvm")
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
        destination = File(outDir)
}

pluginPublish {
    file = tasks.named<Jar>("addonJar").flatMap { it.archiveFile }
    githubRepository = "xenondevs/Nova-Addons"
}
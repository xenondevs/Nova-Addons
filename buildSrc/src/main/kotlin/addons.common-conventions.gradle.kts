import org.gradle.accessors.dm.LibrariesForLibs
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.internal.config.AnalysisFlags.optIn
import xyz.xenondevs.origami.extension.OrigamiExtension

group = "xyz.xenondevs.nova.addon"

plugins {
    id("xyz.xenondevs.nova.nova-gradle-plugin")
    id("xyz.xenondevs.publish.plugin-publish")
}

val libs = the<LibrariesForLibs>()

repositories {
    val local = mavenLocal { content { includeGroupAndSubgroups("xyz.xenondevs") } }
    remove(local)
    addFirst(local)
    
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
        optIn.addAll(
            "xyz.xenondevs.invui.ExperimentalReactiveApi",
            "xyz.xenondevs.invui.dsl.ExperimentalDslApi"
        )
        
        freeCompilerArgs.addAll(
            "-Xcollection-literals"
        )
    }
}

java {
    withSourcesJar()
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
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

extensions.configure<OrigamiExtension> {
    runServer.workingDirectory.set(layout.dir(providers.gradleProperty("serverDir").map(::File)))
}
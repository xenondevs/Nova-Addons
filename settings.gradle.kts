rootProject.name = "Official-Addons"

include("jetpacks")
include("logistics")
include("machines")
include("simple-upgrades")
include("vanilla-hammers")

dependencyResolutionManagement {
    repositories {
        mavenLocal { content { includeGroup("org.spigotmc") } }
        mavenCentral()
        maven("https://libraries.minecraft.net")
        maven("https://repo.xenondevs.xyz/releases")
    }
    versionCatalogs {
        create("libs") {
            from("xyz.xenondevs.nova:catalog:0.14.1")
        }
    }
}

pluginManagement {
    repositories {
        mavenLocal { content { includeGroup("org.spigotmc") } }
        gradlePluginPortal()
        mavenCentral()
        maven("https://repo.xenondevs.xyz/releases")
        maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/") // for nova-gradle-plugin
    }
}
rootProject.name = "Nova-Addons"

include("jetpacks")
include("logistics")
include("machines")
include("simple-upgrades")
include("vanilla-hammers")
include("gigantic-chests")
include("all-addons")

dependencyResolutionManagement {
    repositories {
        mavenLocal { content { includeGroupAndSubgroups("xyz.xenondevs") } }
        maven("https://repo.xenondevs.xyz/releases")
    }
    versionCatalogs {
        create("libs") {
            from("xyz.xenondevs.nova:catalog:0.25.0-alpha.2") // !! also update in buildSrc/settings.gradle.kts !!
        }
    }
}
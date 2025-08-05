rootProject.name = "Nova-Addons"

include("jetpacks")
include("logistics")
include("machines")
include("simple-upgrades")
include("vanilla-hammers")

dependencyResolutionManagement {
    repositories {
        mavenLocal { content { includeGroupAndSubgroups("xyz.xenondevs") } }
        maven("https://repo.xenondevs.xyz/releases")
    }
    versionCatalogs {
        create("libs") {
            from("xyz.xenondevs.nova:catalog:0.20-RC.3")
        }
    }
}
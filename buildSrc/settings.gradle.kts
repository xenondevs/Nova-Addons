dependencyResolutionManagement {
    repositories {
        mavenLocal { content { includeGroupAndSubgroups("xyz.xenondevs") } }
        maven("https://repo.xenondevs.xyz/releases")
    }
    versionCatalogs {
        create("libs") {
            from("xyz.xenondevs.nova:catalog:0.20-alpha.1")
        }
    }
}
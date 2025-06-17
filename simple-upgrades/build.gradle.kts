version = "1.6"

plugins {
    id("addons.common-conventions")
    `maven-publish`
}

addon {
    main = "xyz.xenondevs.nova.addon.simpleupgrades.SimpleUpgrades"
    name = "Simple_Upgrades"
    version = project.version.toString()
    authors = listOf("StudioCode")
}

pluginPublish {
    hangar("Simple-Upgrades") {
        gameVersions(libs.versions.minecraft.get())
        requiredDependency("Nova")
    }
}

publishing {
    repositories {
        maven {
            credentials {
                name = "xenondevs"
                url = uri { "https://repo.xenondevs.xyz/releases/" }
                credentials(PasswordCredentials::class)
            }
        }
    }
    
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }
}
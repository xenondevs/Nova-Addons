version = "1.5"

plugins {
    `maven-publish`
}

addon {
    main.set("xyz.xenondevs.nova.addon.simpleupgrades.SimpleUpgrades")
    name.set("Simple_Upgrades")
    version.set(project.version.toString())
    authors.addAll("StudioCode")
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
            from(components.getByName("kotlin"))
            artifact(tasks.getByName("sources"))
        }
    }
}
version = "1.3"

plugins {
    `maven-publish`
}

addon {
    main.set("xyz.xenondevs.nova.addon.simpleupgrades.SimpleUpgrades")
    id.set("simple_upgrades")
    name.set("Simple-Upgrades")
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
            artifact(tasks.getByName("jar")).classifier = ""
            artifact(tasks.getByName("sources"))
        }
    }
}
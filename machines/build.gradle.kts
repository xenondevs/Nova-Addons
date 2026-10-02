plugins {
    id("addons.common-conventions")
}

version = "0.11.0-alpha.3"

dependencies {
    implementation(project(":simple-upgrades"))
}

origami {
    runServer.plugins.from(
        project(":simple-upgrades")
            .tasks
            .withType<Jar>()
            .matching { it.name == "addonJar" }
    )
}

addon {
    name = "Machines"
    main = "xyz.xenondevs.nova.addon.machines.Machines"
    version = project.version.toString()
    authors = listOf("StudioCode", "ByteZ", "Javahase")
    dependency("Simple_Upgrades")
}

pluginPublish {
    hangar("Machines") {
        gameVersions(libs.versions.minecraft.get())
        requiredDependency("Nova")
        requiredDependency("Simple-Upgrades")
    }
    modrinth("rFmCpMcN") {
        gameVersions(libs.versions.minecraft.get())
        requiredDependency("yCVqpwUy") // Nova
        requiredDependency("uUuVhtJ7") // Simple-Upgrades
    }
}
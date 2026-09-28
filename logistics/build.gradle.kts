plugins {
    id("addons.common-conventions")
}

version = "0.9.0-alpha.2"

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
    name = "Logistics"
    main = "xyz.xenondevs.nova.addon.logistics.Logistics"
    version = project.version.toString()
    authors = listOf("StudioCode", "ByteZ", "Javahase")
    dependency("Simple_Upgrades")
}

pluginPublish {
    hangar("Logistics") {
        gameVersions(libs.versions.minecraft.get())
        requiredDependency("Nova")
        requiredDependency("Simple-Upgrades")
    }
    modrinth("YsED8eqt") {
        gameVersions(libs.versions.minecraft.get())
        requiredDependency("yCVqpwUy") // Nova
        requiredDependency("uUuVhtJ7") // Simple-Upgrades
    }
}
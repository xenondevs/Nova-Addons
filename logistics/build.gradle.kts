plugins {
    id("addons.common-conventions")
}

version = "0.5.0-alpha.2"

dependencies {
    implementation(project(":simple-upgrades"))
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
}
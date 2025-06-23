plugins {
    id("addons.common-conventions")
}

version = "1.8-alpha.1"

addon {
    main = "xyz.xenondevs.nova.addon.vanillahammers.VanillaHammers"
    name = "Vanilla_Hammers"
    version = project.version.toString()
    authors = listOf("StudioCode")
}

pluginPublish {
    hangar("Vanilla-Hammers") {
        gameVersions(libs.versions.minecraft.get())
        requiredDependency("Nova")
    }
}
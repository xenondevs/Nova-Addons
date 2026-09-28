plugins {
    id("addons.common-conventions")
}

version = "0.0.0"

addon {
    name = "Gigantic_Chests"
    main = "xyz.xenondevs.nova.addon.giganticchests.GiganticChests"
    version = project.version.toString()
    authors = listOf("StudioCode")
}
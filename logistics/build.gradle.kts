version = "0.2.6"

dependencies {
    implementation(project(":simple-upgrades"))
}

addon {
    main.set("xyz.xenondevs.nova.addon.logistics.Logistics")
    version.set(project.version.toString())
    authors.addAll("StudioCode", "ByteZ", "Javahase")
}
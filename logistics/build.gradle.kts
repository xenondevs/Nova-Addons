version = "0.2.6"

dependencies {
    implementation(project(":simple-upgrades"))
}

addon {
    main.set("xyz.xenondevs.nova.addon.logistics.Logistics")
    authors.addAll("StudioCode", "ByteZ", "Javahase")
}
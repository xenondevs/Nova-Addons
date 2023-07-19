version = "0.4.3"

dependencies {
    implementation(project(":simple-upgrades"))
}

addon {
    main.set("xyz.xenondevs.nova.addon.machines.Machines")
    authors.addAll("StudioCode", "ByteZ", "Javahase")
}
version = "0.4.7-alpha.4"

dependencies {
    implementation(project(":simple-upgrades"))
}

addon {
    name.set("Machines")
    main.set("xyz.xenondevs.nova.addon.machines.Machines")
    version.set(project.version.toString())
    authors.addAll("StudioCode", "ByteZ", "Javahase")
    dependency("Simple_Upgrades")
}
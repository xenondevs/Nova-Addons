version = "0.4.5-RC.1"

dependencies {
    implementation(project(":simple-upgrades"))
}

addon {
    main.set("xyz.xenondevs.nova.addon.machines.Machines")
    depend.add("simple_upgrades")
    version.set(project.version.toString())
    authors.addAll("StudioCode", "ByteZ", "Javahase")
}
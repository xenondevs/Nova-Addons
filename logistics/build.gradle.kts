version = "0.2.9-alpha.7"

dependencies {
    implementation(project(":simple-upgrades"))
}

addon {
    main.set("xyz.xenondevs.nova.addon.logistics.Logistics")
    depend.add("simple_upgrades")
    version.set(project.version.toString())
    authors.addAll("StudioCode", "ByteZ", "Javahase")
}
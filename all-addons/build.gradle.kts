import xyz.xenondevs.origami.extension.OrigamiExtension

plugins {
    id("xyz.xenondevs.nova.nova-gradle-plugin")
}

repositories {
    val local = mavenLocal { content { includeGroupAndSubgroups("xyz.xenondevs") } }
    remove(local)
    addFirst(local)
    
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.xenondevs.xyz/releases")
}

addon {
    addAddonJarToServerPlugins = false
}

tasks.named("_novaSyncInjectables") { enabled = false }
tasks.named("_novaPrepareAddonJar") { enabled = false }
tasks.named("_oriPrepareMarker") { enabled = false }
tasks.withType<Jar>().configureEach { enabled = false }

extensions.configure<OrigamiExtension> {
    runServer {
        plugins.from(
            rootProject.subprojects
                .filter { it != project }
                .map { addonProject ->
                    addonProject.tasks
                        .withType<Jar>()
                        .matching { it.name == "addonJar" }
                }
        )
        workingDirectory.set(layout.dir(providers.gradleProperty("serverDir").map(::File)))
        javaLauncher = javaToolchains.launcherFor {
            languageVersion = JavaLanguageVersion.of(25)
            vendor = JvmVendorSpec.JETBRAINS
        }
        jvmArgs.addAll("-XX:+AllowEnhancedClassRedefinition")
    }
}

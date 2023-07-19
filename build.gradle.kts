import org.gradle.configurationcache.extensions.capitalized
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

group = "xyz.xenondevs.nova.addon"

val mojangMapped = project.hasProperty("mojang-mapped")

plugins {
    alias(libs.plugins.kotlin)
    alias(libs.plugins.nova)
    alias(libs.plugins.specialsource)
    alias(libs.plugins.stringremapper)
}

subprojects {
    apply(plugin = rootProject.libs.plugins.kotlin.get().pluginId)
    apply(plugin = rootProject.libs.plugins.nova.get().pluginId)
    apply(plugin = rootProject.libs.plugins.specialsource.get().pluginId)
    apply(plugin = rootProject.libs.plugins.stringremapper.get().pluginId)
    
    dependencies { 
        implementation(rootProject.libs.nova)
    }

    addon {
        id.set(this@subprojects.name)
        name.set(this@subprojects.name.capitalized())
        version.set(this@subprojects.version.toString())
        novaVersion.set(rootProject.libs.versions.nova)
    }

    spigotRemap {
        spigotVersion.set(rootProject.libs.versions.spigot.map { it.substringBefore('-') })
        sourceJarTask.set(tasks.jar)
    }

    remapStrings {
        remapGoal.set(if (mojangMapped) "mojang" else "spigot")
        spigotVersion.set(rootProject.libs.versions.spigot)
    }

    tasks {
        register<Copy>("addonJar") {
            group = "build"
            dependsOn("addon", if (mojangMapped) "jar" else "remapObfToSpigot")

            from(File(project.buildDir, "libs/${project.name}-${project.version}.jar"))
            into((project.findProperty("outDir") as? String)?.let(::File) ?: project.buildDir)
            rename { it.replace(project.name, addon.get().addonName.get()) }
        }

        withType<KotlinCompile> {
            kotlinOptions {
                jvmTarget = "17"
            }
        }
    }
}
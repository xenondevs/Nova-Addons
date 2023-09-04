import org.gradle.configurationcache.extensions.capitalized
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import xyz.xenondevs.stringremapper.StringRemapExtension

group = "xyz.xenondevs.nova.addon"

val mojangMapped = project.hasProperty("mojang-mapped")

plugins {
    alias(libs.plugins.paperweight)
    alias(libs.plugins.kotlin)
    alias(libs.plugins.nova)
    alias(libs.plugins.stringremapper)
}

repositories { configureRepositories() }
dependencies { configureDependencies() }

fun RepositoryHandler.configureRepositories() {
    mavenLocal { content { includeGroupAndSubgroups("xyz.xenondevs") } }
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.xenondevs.xyz/releases/")
}

fun DependencyHandlerScope.configureDependencies() {
    paperweight.paperDevBundle(rootProject.libs.versions.paper)
    configurations.getByName("mojangMappedServer").apply {
        exclude("org.spongepowered", "configurate-yaml")
    }
    implementation(rootProject.libs.nova)
}

fun StringRemapExtension.configureRemapStrings() {
    remapGoal.set(if (mojangMapped) "mojang" else "spigot")
    gameVersion.set(libs.versions.paper.get().substringBefore("-"))
}

subprojects {
    apply(plugin = rootProject.libs.plugins.kotlin.get().pluginId)
    apply(plugin = rootProject.libs.plugins.nova.get().pluginId)
    apply(plugin = rootProject.libs.plugins.paperweight.get().pluginId)
    apply(plugin = rootProject.libs.plugins.stringremapper.get().pluginId)
    
    repositories { configureRepositories() }
    dependencies { configureDependencies() }
    remapStrings { configureRemapStrings() }
    
    addon {
        id.set(this@subprojects.name)
        name.set(this@subprojects.name.capitalized())
        novaVersion.set(rootProject.libs.versions.nova)
    }
    
    tasks {
        val buildDir = project.layout.buildDirectory.get().asFile
        val outDir = (project.findProperty("outDir") as? String)?.let(::File) ?: buildDir
        
        register<Copy>("addonJar") {
            group = "build"
            if (mojangMapped) {
                dependsOn("jar")
                from(File(buildDir, "libs/${project.name}-${project.version}-dev.jar"))
            } else {
                dependsOn("reobfJar")
                from(File(buildDir, "libs/${project.name}-${project.version}.jar"))
            }
            
            into(outDir)
            rename { "${addonMetadata.get().addonName.get()}-${project.version}.jar" }
        }
        
        withType<KotlinCompile> {
            kotlinOptions {
                jvmTarget = "17"
            }
        }
    }
}
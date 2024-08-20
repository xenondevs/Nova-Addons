import org.gradle.configurationcache.extensions.capitalized
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

group = "xyz.xenondevs.nova.addon"

plugins {
    alias(libs.plugins.paperweight)
    alias(libs.plugins.kotlin)
    alias(libs.plugins.nova)
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

subprojects {
    group = "xyz.xenondevs.nova.addon"
    
    apply(plugin = rootProject.libs.plugins.kotlin.get().pluginId)
    apply(plugin = rootProject.libs.plugins.nova.get().pluginId)
    apply(plugin = rootProject.libs.plugins.paperweight.get().pluginId)
    
    repositories { configureRepositories() }
    dependencies { configureDependencies() }
    
    addon {
        id.set(this@subprojects.name)
        name.set(this@subprojects.name.capitalized())
        novaVersion.set(rootProject.libs.versions.nova)
    }
    
    tasks {
        withType<KotlinCompile> {
            compilerOptions {
                jvmTarget = JvmTarget.JVM_21
            }
        }
        
        register<Jar>("sources") {
            dependsOn(JavaPlugin.CLASSES_TASK_NAME)
            from("src/main/java", "src/main/kotlin")
            archiveClassifier.set("sources")
        }
        
        val buildDir = project.layout.buildDirectory.get().asFile
        val outDir = (project.findProperty("outDir") as? String)?.let(::File) ?: buildDir
        register<Copy>("addonJar") {
            group = "build"
            dependsOn("jar")
            
            from(File(buildDir, "libs/${project.name}-${project.version}.jar"))
            into(outDir)
            rename { "${addonMetadata.get().addonName.get()}-${project.version}.jar" }
        }
    }
    
    afterEvaluate {
        // remove "dev" classifier set by paperweight-userdev
        tasks.getByName<Jar>("jar") {
            archiveClassifier = ""
        }
    }
}

tasks {
    getByName("addonMetadata").enabled = false
}
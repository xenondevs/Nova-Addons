
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
        val outDir = project.findProperty("outDir")
        if (outDir is String)
            destination.set(File(outDir))
    }
    
    tasks {
        withType<KotlinCompile> {
            compilerOptions {
                jvmTarget = JvmTarget.JVM_21
                freeCompilerArgs.addAll(
                    "-opt-in=xyz.xenondevs.invui.ExperimentalReactiveApi"
                )
            }
        }
        
        register<Jar>("sources") {
            dependsOn(JavaPlugin.CLASSES_TASK_NAME)
            from("src/main/java", "src/main/kotlin")
            archiveClassifier.set("sources")
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
    getByName("addonJar").enabled = false
}
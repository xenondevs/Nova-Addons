plugins {
    `kotlin-dsl`
}

repositories {
    mavenLocal { content { includeGroupAndSubgroups("xyz.xenondevs") } }
    gradlePluginPortal()
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.xenondevs.xyz/releases")
}

dependencies {
    implementation(libs.kotlin.plugin)
    implementation(libs.nova.plugin)
    implementation(libs.paperweight.userdev.plugin)
    
    // https://github.com/gradle/gradle/issues/15383#issuecomment-779893192
    implementation(files(libs.javaClass.superclass.protectionDomain.codeSource.location))
}
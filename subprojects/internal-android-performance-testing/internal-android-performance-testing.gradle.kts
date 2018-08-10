import org.gradle.gradlebuild.unittestandcompile.ModuleType
import org.gradle.gradlebuild.BuildClassPath

plugins {
    application
}

val androidTools by configurations.creating
configurations.compile.extendsFrom(androidTools)

repositories {
    google()
}

dependencies {
    compile(project(":toolingApi"))
    androidTools("com.android.tools.build:gradle:3.0.0")
}

gradlebuildJava {
    moduleType = ModuleType.INTERNAL
}

application {
    mainClassName = "org.gradle.performance.android.Main"
    applicationName = "android-test-app"
}

tasks.register<BuildClassPath>("buildClassPath") {
    classpath.from(androidTools)
    classpath.from(tasks.named("jar"))
    outputFile.set(project.layout.buildDirectory.file("classpath.txt"))
}

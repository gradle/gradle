import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

buildscript {
    dependencies { classpath(kotlin("gradle-plugin")) }
    repositories { jcenter() }
}

allprojects {

    group = "org.gradle.kotlin.dsl.samples.multiprojectci"

    version = "1.0"

    repositories {
        jcenter()
    }
}

// Configure all KotlinCompile tasks on each sub-project
subprojects {

    tasks.withType<KotlinCompile> {
        println("Configuring $name in project ${project.name}...")
        kotlinOptions {
            suppressWarnings = true
        }
    }
}

plugins {
    base
}

dependencies {
    // Make the root project archives configuration depend on every subproject
    subprojects.forEach {
        archives(it)
    }
}

import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

buildscript {
    repositories { gradleScriptKotlin() }
    dependencies { classpath(kotlinModule("gradle-plugin")) }
}

allprojects {

    group = "org.gradle.script.kotlin.samples.multiprojectci"

    version = "1.0"

    configure(listOf(repositories, buildscript.repositories)) {
        gradleScriptKotlin()
    }
}

// Configure all KotlinCompile tasks on each sub-project
subprojects {
    tasks.withType<KotlinCompile> {
        println("Configuring ${name} in project ${project.name}...")
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

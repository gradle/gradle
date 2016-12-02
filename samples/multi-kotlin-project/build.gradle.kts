allprojects {

    group = "org.gradle.script.kotlin.samples.multiproject"

    version = "1.0"

    configure(listOf(repositories, buildscript.repositories)) {
        gradleScriptKotlin()
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

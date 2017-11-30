plugins {
    base
    kotlin("jvm") version "1.2.0" apply false
}

allprojects {

    group = "org.gradle.kotlin.dsl.samples.multiproject"

    version = "1.0"

    repositories {
        jcenter()
    }
}

dependencies {
    // Make the root project archives configuration depend on every sub-project
    subprojects.forEach {
        archives(it)
    }
}

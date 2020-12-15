plugins {
    kotlin("multiplatform")
}

kotlin {
    jvm()
    js()
    macosX64()
    linuxX64()
}

dependencies {
    "commonMainImplementation"(kotlin("stdlib-common"))
    "jvmMainImplementation"(kotlin("stdlib"))
    "jsMainImplementation"(kotlin("stdlib-js"))
}

afterEvaluate {
    publishing {
        publications.forEach { println("Koltin-Native publication: ${it.name}") }
    }
}
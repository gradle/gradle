import org.jetbrains.kotlin.gradle.dsl.Coroutines

plugins {
    application
    kotlin("jvm")
}

application {
    mainClassName = "samples.HelloCoroutinesKt"
}

kotlin { // configure<org.jetbrains.kotlin.gradle.dsl.KotlinProjectExtension>
    experimental.coroutines = Coroutines.ENABLE
}

dependencies {
    compile(kotlin("stdlib"))
}

repositories {
    jcenter()
}

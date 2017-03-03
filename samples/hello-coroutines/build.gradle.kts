import org.jetbrains.kotlin.gradle.dsl.Coroutines

plugins {
    application
    id("nebula.kotlin") version embeddedKotlinVersion
}

application {
    mainClassName = "samples.HelloCoroutinesKt"
}

kotlin { // configure<org.jetbrains.kotlin.gradle.dsl.KotlinProjectExtension>
    experimental.coroutines = Coroutines.ENABLE
}

repositories {
    jcenter()
}

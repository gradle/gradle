import build.futureKotlin

plugins {
    id("kotlin-dsl-plugin-bundle")
}

base {
    archivesBaseName = "gradle-kotlin-dsl-plugins"
}

dependencies {
    compileOnly(gradleKotlinDsl())

    implementation(futureKotlin("stdlib-jdk8"))
    implementation(futureKotlin("gradle-plugin"))
    implementation(futureKotlin("sam-with-receiver"))

    testImplementation(project(":test-fixtures"))
}


// plugins ------------------------------------------------------------

kotlinDslPlugins {
    create("embeddedKotlin") {
        displayName = "Embedded Kotlin Gradle Plugin"
        id = "org.gradle.kotlin.embedded-kotlin"
        implementationClass = "org.gradle.kotlin.dsl.plugins.embedded.EmbeddedKotlinPlugin"
    }
    create("kotlinDsl") {
        displayName = "Gradle Kotlin DSL Plugin"
        id = "org.gradle.kotlin.kotlin-dsl"
        implementationClass = "org.gradle.kotlin.dsl.plugins.dsl.KotlinDslPlugin"
    }
}

import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("java-library")
    kotlin("jvm")
    id("gradlebuild.module-jar")
    id("gradlebuild.reproducible-archives")
    id("gradlebuild.repositories")
    id("gradlebuild.code-quality")
    id("gradlebuild.ktlint")
    id("gradlebuild.test-retry")
    id("gradlebuild.ci-reporting")
}

java {
    configureJavaToolChain()
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

kotlin {
    compilerOptions {
        allWarningsAsErrors = true
        jvmTarget.set(JvmTarget.JVM_1_8)
    }
}

dependencies {
    testImplementation("org.junit.vintage:junit-vintage-engine")
}

tasks {
    named("codeQuality") {
        dependsOn("ktlintCheck")
    }
    withType<Test>().configureEach {
        useJUnitPlatform()
    }
}

import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
}

group = "com.h0tk3y"
version = "1.0-SNAPSHOT"

kotlin {
    compilerOptions { 
        jvmTarget.set(JvmTarget.JVM_1_8)
    }
}

java.targetCompatibility = JavaVersion.VERSION_1_8

dependencies {
    implementation(kotlin("reflect"))
    
    api("kotlinx.ast:parser-antlr-kotlin:0.1.0")
    implementation("kotlinx.ast:grammar-kotlin-parser-antlr-kotlin:0.1.0")
    
    testImplementation(kotlin("test-junit5"))
    testImplementation("org.jetbrains:annotations:24.0.1")
}

testing.suites.named("test", JvmTestSuite::class) {
    useJUnitJupiter()
}
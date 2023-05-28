plugins {
    alias(libs.plugins.kotlin.jvm)
}

group = "com.h0tk3y"
version = "1.0-SNAPSHOT"

dependencies {
    implementation("kotlinx.ast:parser-antlr-kotlin:0.1.0")
    implementation("kotlinx.ast:grammar-kotlin-parser-antlr-kotlin:0.1.0")
}
import org.gradle.kotlin.dsl.support.kotlinEap

plugins {
    `kotlin-dsl`
}

repositories {
    kotlinEap()
    jcenter()
}

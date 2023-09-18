package demo.demoPlugins

fun main() {
    val result = TopLevelScope().run {
        plugins {
            val kotlinVersion = "1.9.20"
            id("org.jetbrains.kotlin.jvm").version(kotlinVersion)
            id("org.jetbrains.kotlin.kapt").version(kotlinVersion).apply(false)
            id("application").id
        }

    }
}
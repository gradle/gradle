// NOTE: Precompiled script plugins are only be written in Kotlin at the moment.

plugins {
    `kotlin-dsl`
}

group = "org.gradle.sample"
version = "1.0"

repositories {
    jcenter()
}

kotlinDslPluginOptions {
    experimentalWarning.set(false)
}

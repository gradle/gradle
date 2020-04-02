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

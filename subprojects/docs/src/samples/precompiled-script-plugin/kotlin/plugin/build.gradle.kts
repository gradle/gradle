plugins {
    `kotlin-dsl`
}

group = "com.example"
version = "1.0"

repositories {
    jcenter()
}

kotlinDslPluginOptions {
    experimentalWarning.set(false)
}

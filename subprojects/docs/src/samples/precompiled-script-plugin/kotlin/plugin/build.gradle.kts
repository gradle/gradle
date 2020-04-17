plugins {
    `kotlin-dsl`
}

group = "com.example"
version = "1.0"

repositories {
    jcenter()
}

dependencies {
    testImplementation("junit:junit:4.12")
}

kotlinDslPluginOptions {
    experimentalWarning.set(false)
}

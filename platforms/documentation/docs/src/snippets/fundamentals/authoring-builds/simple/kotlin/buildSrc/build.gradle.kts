plugins {
    `kotlin-dsl`
    `maven-publish`
}

repositories {
    gradlePluginPortal()
}

// tag::plugin-3[]
group = "com.example"
version = "1.0.0"

gradlePlugin {
    plugins {
        create("my-binary-plugin") {
            id = "com.example.my-binary-plugin"
            implementationClass = "MyCreateFileBinaryPlugin"
        }
    }
}

publishing {
    repositories {
        mavenLocal()
    }
}
// end::plugin-3[]

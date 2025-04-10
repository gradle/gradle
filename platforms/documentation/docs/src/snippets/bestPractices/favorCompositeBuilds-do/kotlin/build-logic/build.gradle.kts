// tag::do-this[]
// This file is located in /build-logic

plugins {
    `java-gradle-plugin`
}

gradlePlugin {
    plugins {
        create("myPlugin") {
            id = "org.example.myplugin"
            implementationClass = "org.example.MyPlugin"
        }
    }
}
// end::do-this[]

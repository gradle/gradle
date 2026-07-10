// tag::avoid-this[]
// This file is located in /buildSrc

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
// end::avoid-this[]

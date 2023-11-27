plugins {
    id("java-gradle-plugin")
}

gradlePlugin {
    plugins {
        create("serverPlugin") {
            id = "org.myorg.server"
            implementationClass = "org.myorg.ServerPlugin"
        }
    }
}

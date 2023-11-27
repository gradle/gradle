plugins {
    id("java-gradle-plugin")
}

gradlePlugin {
    plugins {
        create("serverEnvironmentPlugin") {
            id = "org.myorg.server-env"
            implementationClass = "org.myorg.ServerEnvironmentPlugin"
        }
    }
}

plugins {
    id("java-gradle-plugin")
}

gradlePlugin {
    plugins {
       create("binaryRepositoryVersionPlugin") {
            id = "org.myorg.binary-repository-version"
            implementationClass = "org.myorg.BinaryRepositoryVersionPlugin"
        }
    }
}

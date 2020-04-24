plugins {
    groovy
}

dependencies {
    implementation(localGroovy())
}

tasks {
    register<JavaExec>("runScript") {
        main = "scope"
        classpath = sourceSets.main.get().runtimeClasspath
        jvmArgs = listOf("--add-opens", "java.base/java.lang=ALL-UNNAMED")
    }
}

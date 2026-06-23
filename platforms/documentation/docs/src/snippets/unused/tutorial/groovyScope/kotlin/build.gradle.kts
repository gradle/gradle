plugins {
    groovy
}

dependencies {
    implementation(localGroovy())
}

tasks {
    register<JavaExec>("runScript") {
        mainClass = "scope"
        classpath = sourceSets.main.get().runtimeClasspath
        if (JavaVersion.current().isCompatibleWith(JavaVersion.VERSION_1_9)) {
            jvmArgs = listOf("--add-opens", "java.base/java.lang=ALL-UNNAMED")
        }
    }
}

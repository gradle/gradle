plugins {
    `extra-java-module-info` // apply my own plugin written in buildSrc
}

// tag::extraModuleInfo[]
extraJavaModuleInfo {
    // This does not have to be a complete description (e.g. here 'org.apache.commons.collections' does not export anything here).
    // It only needs to be good enough to work in the context of this application we are building.
    addModuleInfo("commons-beanutils-1.9.4.jar", "org.apache.commons.beanutils", "1.9.4",
        listOf("org.apache.commons.beanutils"), // exports
        listOf("org.apache.commons.logging", "java.sql", "java.desktop"), emptyList()) // module dependencies
    addModuleInfo("commons-cli-1.4.jar", "org.apache.commons.cli", "3.2.2",
        listOf("org.apache.commons.cli"), // exports
        emptyList(), emptyList())
    addModuleInfo("commons-collections-3.2.2.jar", "org.apache.commons.collections", "3.2.2", emptyList(), emptyList(), emptyList())
    addAutomatic("commons-logging-1.2.jar", "org.apache.commons.logging")
}
// end::extraModuleInfo[]

subprojects {
    version = "1.0.2"
    group = "org.gradle.sample"

    repositories {
        mavenCentral()
    }

    plugins.withType<JavaPlugin>().configureEach {
        configure<JavaPluginExtension> {
            modularity.inferModulePath.set(true)
        }
    }
}

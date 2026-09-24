rootProject.name = "catalogs-external-properties"

// tag::inject_versions[]
val sharedVersions = java.util.Properties().apply {
    file("gradle/shared-versions.properties").inputStream().use { load(it) }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
    versionCatalogs {
        create("distributionLibs") {
            from(files("gradle/distribution.versions.toml"))
            version("errorProne", sharedVersions.getProperty("errorProne"))
            version("kotlin", sharedVersions.getProperty("kotlin"))
        }
        create("testLibs") {
            from(files("gradle/test.versions.toml"))
            version("errorProne", sharedVersions.getProperty("errorProne"))
            version("kotlin", sharedVersions.getProperty("kotlin"))
        }
    }
}
// end::inject_versions[]

rootProject.name = "catalogs-kotlin-version-sharing"

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

// tag::kotlin_version_sharing[]
dependencyResolutionManagement {
    versionCatalogs {
        create("libs") {
            from(files("gradle/catalog.versions.toml"))
            // Single source of truth for the Kotlin version
            version("kotlin", "2.3.20")
        }
    }
}
// end::kotlin_version_sharing[]

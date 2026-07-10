rootProject.name = "catalogs-combine-from-version"

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

// tag::combine_from_with_version[]
dependencyResolutionManagement {
    versionCatalogs {
        create("libs") {
            from(files("gradle/catalog.versions.toml"))

            // Override an existing version
            version("groovy", "3.0.6")

            // Add a new version not present in the TOML file
            version("errorProne", "2.28.0")

            // Add a library that uses the injected version
            library("errorProne-core", "com.google.errorprone", "error_prone_core").versionRef("errorProne")
        }
    }
}
// end::combine_from_with_version[]

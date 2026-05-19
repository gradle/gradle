rootProject.name = "consumer"

dependencyResolutionManagement {
    repositories {
        maven {
            url = uri("${file("../catalog/repo")}")
        }
        mavenCentral()
    }
}

if (providers.systemProperty("create1").getOrNull() != null) {
    // tag::consume_with_overrides[]
    dependencyResolutionManagement {
        versionCatalogs {
            // Import the organization-wide catalog
            create("libs") {
                from("com.mycompany:catalog:1.0")
                // Override a specific version for this project
                version("kotlin", "2.3.21")
            }

            // A local catalog for project-specific dependencies
            create("projectLibs") {
                from(files("gradle/project.versions.toml"))
            }
        }
    }
    // end::consume_with_overrides[]
}

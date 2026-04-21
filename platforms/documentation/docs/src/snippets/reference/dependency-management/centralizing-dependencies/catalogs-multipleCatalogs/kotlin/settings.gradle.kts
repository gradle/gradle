rootProject.name = "multiple-catalogs"

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
    // tag::multiple-catalogs[]
    versionCatalogs {
        create("tools") {
            from(files("gradle/tools.versions.toml"))
        }
        create("testLibs") {
            from(files("gradle/test.versions.toml"))
        }
        // HOWEVER THIS IS NOT ALLOWED - IT WILL NOT WORK
        /* In version catalog libs, you can only call the 'from' method a single time:
        create("libs") {
            from(files("gradle/base.versions.toml"))
            from(files("gradle/extras.versions.toml")) // Error!
        }
        */

    }
    // end::multiple-catalogs[]
}

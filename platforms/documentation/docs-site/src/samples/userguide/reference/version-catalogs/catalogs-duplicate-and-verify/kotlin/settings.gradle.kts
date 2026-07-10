rootProject.name = "catalogs-duplicate-and-verify"

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
    versionCatalogs {
        create("distributionLibs") {
            from(files("gradle/distribution.versions.toml"))
        }
        create("testLibs") {
            from(files("gradle/test.versions.toml"))
        }
    }
}

rootProject.name = "buildscript"

dependencyResolutionManagement {
    versionCatalogs {
        create("libs") {
            plugin("jmh", "me.champeau.jmh").version("0.6.5")
        }
    }
}

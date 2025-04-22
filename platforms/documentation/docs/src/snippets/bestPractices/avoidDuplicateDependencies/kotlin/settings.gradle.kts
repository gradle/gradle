rootProject.name = "avoidDuplicateDependencies"

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

include(":do-this", ":avoid-this")
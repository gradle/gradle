dependencyResolutionManagement {
    repositories {
        gradlePluginPortal()
        google()
    }
}
includeBuild("../platforms")

rootProject.name = "build-logic"
include("commons")
include("java-library")
include("kotlin-library")
include("android-application")
include("spring-boot-application")
include("report-aggregation")

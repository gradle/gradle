rootProject.name = "dependency-resolution-management-plugin-management"

// tag::plugin-management[]
pluginManagement {
    repositories {
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
    }
}
// end::plugin-management[]

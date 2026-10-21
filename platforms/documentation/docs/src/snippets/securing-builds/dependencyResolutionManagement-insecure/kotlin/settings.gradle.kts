rootProject.name = "dependency-resolution-management-insecure"

// tag::insecure[]
dependencyResolutionManagement {
    repositories {
        maven {
            url = uri("http://repo.example.com/maven")
            isAllowInsecureProtocol = true  // required to make Gradle accept HTTP, but you shouldn't
        }
    }
}
// end::insecure[]

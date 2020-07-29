// tag::android[]
include("lib", "app")
// end::android[]

rootProject.name = "android-build"

gradle.allprojects {
    repositories {
        google()
        jcenter()
    }
}

// tag::lifecycle-before-project[]
include("sub1")
include("sub2")

gradle.lifecycle.beforeProject {
    apply(plugin = "base")

    repositories {
        mavenCentral()
    }

    group = "org.example.app"
    version = providers.fileContents(
        isolated.rootProject.projectDirectory.file("version.txt")
    ).asText.get()
}
// end::lifecycle-before-project[]

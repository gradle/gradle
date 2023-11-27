pluginManagement {
  repositories {
      maven(url = "./maven-repo")
  }
}

// tag::include-subprojects[]
include("hello-a")
include("hello-b")
include("goodbye-c")
// end::include-subprojects[]

rootProject.name = "multiproject"

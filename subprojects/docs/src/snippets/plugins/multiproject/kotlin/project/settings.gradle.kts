pluginManagement {
  repositories {
      maven(url = "../maven-repo")
  }
}

// tag::include-subprojects[]
include("helloA")
include("helloB")
include("goodbye-c")
// end::include-subprojects[]

rootProject.name = "multiproject"

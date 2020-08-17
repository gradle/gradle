pluginManagement {
  repositories {
      maven(url = "../maven-repo")
  }
}

// tag::include-subprojects[]
include("helloA")
include("helloB")
include("goodbyeC")
// end::include-subprojects[]

rootProject.name = "multiproject"

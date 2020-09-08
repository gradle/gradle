// tag::configure-plugin-version[]
pluginManagement {
  val helloPluginVersion: String by settings
  plugins {
    id("com.example.hello") version "${helloPluginVersion}"
  }
// end::configure-plugin-version[]
  repositories {
      maven(url = "./maven-repo")
  }
// tag::configure-plugin-version[]
}
// end::configure-plugin-version[]

rootProject.name = "plugin-versions"

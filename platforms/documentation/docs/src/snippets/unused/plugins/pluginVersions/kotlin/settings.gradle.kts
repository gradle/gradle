// tag::configure-plugin-version[]
pluginManagement {
  val helloPluginVersion = settings.providers.gradleProperty("helloPluginVersion").get()
  plugins {
    id("com.example.hello") version "${helloPluginVersion}"
  }
// end::configure-plugin-version[]
  repositories {
      maven(url = file("./maven-repo"))
  }
// tag::configure-plugin-version[]
}
// end::configure-plugin-version[]

rootProject.name = "plugin-versions"

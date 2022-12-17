// tag::configure-plugin-version[]
pluginManagement {
  val helloPluginVersion: String = settings.withGroovyBuilder { getProperty("helloPluginVersion") } as String
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

// tag::buildscript_block[]
import org.yaml.snakeyaml.Yaml
import java.io.File

buildscript {
    repositories {
        maven {
            url = uri("https://plugins.gradle.org/m2/")
        }
        mavenCentral()  // Where to find the plugin
    }
    dependencies {
        classpath("org.yaml:snakeyaml:1.19") // The library's classpath dependency
        classpath("com.gradleup.shadow:shadow-gradle-plugin:8.3.4") // Plugin dependency for legacy plugin application
    }
}

// Applies legacy Shadow plugin
apply(plugin = "com.gradleup.shadow")

// Uses the library in the build script
val yamlContent = """
        name: Project
    """.trimIndent()
val yaml = Yaml()
val data: Map<String, Any> = yaml.load(yamlContent)
// end::buildscript_block[]

// tag::plugin[]
class MyPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        println("Plugin ${this.javaClass.simpleName} applied on ${project.name}")
    }
}

apply<MyPlugin>()
// end::plugin[]

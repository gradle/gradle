// tag::buildscript_block[]
import org.yaml.snakeyaml.Yaml
import java.io.File

buildscript {
    repositories {
        mavenCentral()  // Where to find the plugin
    }
    dependencies {
        classpath("org.yaml:snakeyaml:1.19") // The library's classpath dependency
        classpath("org.springframework.boot:spring-boot-gradle-plugin:3.3.1") // The plugin's classpath dependency
        classpath("com.github.johnrengelman.shadow:shadow-gradle-plugin:6.1.0") // The legacy version of Shadow Plugin that needs buildscript
    }
}

// Applies the plugin by its ID after the plugin classpath is configured.
apply(plugin = "org.springframework.boot")
// Applies legacy Shadow plugin
apply(plugin = "com.github.johnrengelman.shadow")

// Uses the library in the build script
val prop = Yaml().loadAll(File("${projectDir}/src/main/resources/application.yml").inputStream()).first()
val path = (prop as Map<*, *>)["temp"]?.let { (it as Map<*, *>)["files"] }?.let { (it as Map<*, *>)["path"] }
// end::buildscript_block[]

// tag::plugin[]
class MyPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        println("Plugin ${this.javaClass.simpleName} applied on ${project.name}")
    }
}

apply<MyPlugin>()
// end::plugin[]

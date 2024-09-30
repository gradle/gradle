// tag::buildscript_block[]
buildscript {
    repositories {
        mavenCentral()  // Where to find the plugin
    }
    dependencies {
        classpath("org.springframework.boot:spring-boot-gradle-plugin:3.3.1") // The plugin's classpath dependency
    }
}

// Applies the plugin by its ID after the plugin classpath is configured.
apply(plugin = "org.springframework.boot")
// end::buildscript_block[]

// tag::plugin[]
class MyPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        println("Plugin ${this.javaClass.simpleName} applied on ${project.name}")
    }
}

apply<MyPlugin>()
// end::plugin[]

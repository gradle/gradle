import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.provider.Property
import org.gradle.api.tasks.bundling.Jar

// tag::do-this[]
interface AppInfoExtension {
    val appName: Property<String>
}

class AppInfoPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val extension = project.extensions.create("appInfo", AppInfoExtension::class.java)
        extension.appName.convention("unnamed") // <1>

        project.tasks.register("printAppInfo") {
            val name = extension.appName
            doLast {
                println("App: ${name.get()}") // <2>
            }
        }

        project.pluginManager.withPlugin("java-library") { // <3>
            project.tasks.named("printAppInfo") {
                val jarName = extension.appName
                doLast {
                    println("Jar: ${jarName.get()}.jar")
                }
            }
            project.tasks.named("jar", Jar::class.java) {
                archiveBaseName.set(extension.appName)
            }
        }
    }
}
// end::do-this[]

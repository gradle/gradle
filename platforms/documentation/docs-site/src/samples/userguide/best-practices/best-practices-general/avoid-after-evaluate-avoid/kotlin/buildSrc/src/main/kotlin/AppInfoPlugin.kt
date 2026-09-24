import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.provider.Property
import org.gradle.api.tasks.bundling.Jar

// tag::avoid-this[]
interface AppInfoExtension {
    val appName: Property<String>
}

class AppInfoPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val extension = project.extensions.create("appInfo", AppInfoExtension::class.java)

        project.afterEvaluate { // <1>
            val name = extension.appName.getOrElse("unnamed") // <2>

            tasks.register("printAppInfo") { // <3>
                doLast {
                    println("App: $name")
                }
            }

            if (plugins.hasPlugin("java-library")) { // <4>
                tasks.named("printAppInfo") {
                    doLast {
                        println("Jar: $name.jar")
                    }
                }
                tasks.named("jar", Jar::class.java) {
                    archiveBaseName.set(name)
                }
            }
        }
    }
}
// end::avoid-this[]

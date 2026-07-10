import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.provider.Property
import org.gradle.api.tasks.bundling.Jar

// tag::do-this[]
interface AppInfoExtension {
    Property<String> getAppName()
}

class AppInfoPlugin implements Plugin<Project> {
    void apply(Project project) {
        def extension = project.extensions.create("appInfo", AppInfoExtension)
        extension.appName.convention("unnamed") // <1>

        project.tasks.register("printAppInfo") {
            def name = extension.appName
            doLast {
                println "App: ${name.get()}" // <2>
            }
        }

        project.pluginManager.withPlugin("java-library") { // <3>
            project.tasks.named("printAppInfo") {
                def jarName = extension.appName
                doLast {
                    println "Jar: ${jarName.get()}.jar"
                }
            }
            project.tasks.named("jar", Jar) {
                archiveBaseName.set(extension.appName)
            }
        }
    }
}
// end::do-this[]

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.provider.Property
import org.gradle.api.tasks.bundling.Jar

// tag::avoid-this[]
interface AppInfoExtension {
    Property<String> getAppName()
}

class AppInfoPlugin implements Plugin<Project> {
    void apply(Project project) {
        def extension = project.extensions.create("appInfo", AppInfoExtension)

        project.afterEvaluate { // <1>
            def name = extension.appName.getOrElse("unnamed") // <2>

            project.tasks.register("printAppInfo") { // <3>
                doLast {
                    println "App: $name"
                }
            }

            if (project.plugins.hasPlugin("java-library")) { // <4>
                project.tasks.named("printAppInfo") {
                    doLast {
                        println "Jar: ${name}.jar"
                    }
                }
                project.tasks.named("jar", Jar) {
                    archiveBaseName.set(name)
                }
            }
        }
    }
}
// end::avoid-this[]

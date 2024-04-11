// tag::no-script-plugin[]
class GreetingPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.task("hello") {
            doLast {
                println("Hello from the GreetingPlugin")
            }
        }
    }
}

// Apply the plugin
apply<GreetingPlugin>()
// end::no-script-plugin[]

// tag::script-plugin[]
apply(from = "other.gradle.kts")
// end::script-plugin[]

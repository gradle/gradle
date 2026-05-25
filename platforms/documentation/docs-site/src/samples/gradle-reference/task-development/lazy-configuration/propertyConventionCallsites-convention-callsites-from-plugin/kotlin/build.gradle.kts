// setting convention when registering a task from plugin
class GreetingPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.getTasks().register<GreetingTask>("hello") {
            greeter.convention("Greeter")
        }
    }
}

// tag::lazy-properties[]
abstract class MyTask : DefaultTask() {
    // Avoid Java Bean properties
    @Input
    var myEagerProperty: String = "default value"

    // Use Gradle managed properties instead
    @get:Input
    abstract val myLazyProperty: Property<String>

    @TaskAction
    fun myAction() {
        println("Use ${myLazyProperty.get()} and do NOT use $myEagerProperty")
    }
}
// end::lazy-properties[]

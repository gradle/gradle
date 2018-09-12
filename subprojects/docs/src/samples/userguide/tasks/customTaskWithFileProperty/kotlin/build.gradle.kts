// tag::all[]
// tag::task[]
open class GreetingToFileTask : DefaultTask() {

    var destination: Any? = null

    fun getDestination(): File {
        return project.file(destination!!)
    }

    @TaskAction
    fun greet() {
        val file = getDestination()
        file.parentFile.mkdirs()
        file.writeText("Hello!")
    }
}
// end::task[]

// tag::config[]
task<GreetingToFileTask>("greet") {
    destination = provider { project.extra["greetingFile"]!! }
}

task("sayGreeting") {
    dependsOn("greet")
    doLast {
        println(file(project.extra["greetingFile"]!!).readText())
    }
}

extra["greetingFile"] = "$buildDir/hello.txt"
// end::config[]
// end::all[]

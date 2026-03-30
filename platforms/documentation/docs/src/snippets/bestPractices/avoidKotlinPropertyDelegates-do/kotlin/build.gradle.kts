// tag::do-this[]
val myLibs = configurations.create("myLibs") // <1>
extra.set("buildNumber", 42) // <2>

val greet = tasks.register("greet") { // <3>
    val configName = myLibs.name
    doLast {
        println("Configuration: $configName")
    }
}
// end::do-this[]

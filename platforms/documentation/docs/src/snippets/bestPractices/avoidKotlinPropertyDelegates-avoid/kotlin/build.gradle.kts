// tag::avoid-this[]
val myLibs by configurations.creating // <1>
var buildNumber by extra(42) // <2>

val greet by tasks.registering { // <3>
    val configName = myLibs.name
    doLast {
        println("Configuration: $configName")
    }
}
// end::avoid-this[]

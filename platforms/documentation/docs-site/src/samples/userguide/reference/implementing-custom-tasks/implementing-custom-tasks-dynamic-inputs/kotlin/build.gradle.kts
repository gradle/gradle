// tag::dynamic-inputs[]
tasks.register("printVersionDynamic") {
    inputs.property("version", project.version.toString())
    doLast {
        println("Version: ${inputs.properties["version"]}")
    }
}
// end::dynamic-inputs[]

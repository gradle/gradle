// tag::apply-by-id[]
apply(plugin = "java")


tasks.register("show") {
    doLast {
        println(relativePath(tasks.getByName<JavaCompile>("compileJava").destinationDir))
        println(relativePath(tasks.getByName<ProcessResources>("processResources").destinationDir))
    }
}
// end::apply-by-id[]
// tag::apply-by-type[]
apply<JavaPlugin>()
// end::apply-by-type[]
// tag::explicit-apply[]
apply(plugin = "java")
apply(plugin = "groovy")
// end::explicit-apply[]

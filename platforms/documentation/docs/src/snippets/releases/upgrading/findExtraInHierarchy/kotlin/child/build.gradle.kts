// tag::helper[]
fun Project.findExtraInHierarchy(name: String): Any? =
    if (extra.has(name)) extra[name] else parent?.findExtraInHierarchy(name)
// end::helper[]

tasks.register("printFoo") {
    val fromRoot = findExtraInHierarchy("foo")
    val missing = findExtraInHierarchy("missing")
    doLast {
        println("foo = $fromRoot")
        println("missing = $missing")
    }
}

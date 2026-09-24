// tag::helper[]
tailrec fun Project.findExtraInHierarchy(name: String): Any? {
    if (extra.has(name)) return extra[name]
    val ancestor = parent ?: return null
    return ancestor.findExtraInHierarchy(name)
}
// end::helper[]

tasks.register("printFoo") {
    val fromRoot = findExtraInHierarchy("foo")
    val missing = findExtraInHierarchy("missing")
    doLast {
        println("foo = $fromRoot")
        println("missing = $missing")
    }
}

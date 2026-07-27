
class TargetType {
    var foo = ""
    fun name(s: String, i: Int, a: Any) {}
    fun blockName(closure: groovy.lang.Closure<*>) {}
    fun another(map: Map<String, *>) {}
}


val target = TargetType()
val aReference = ""

// tag::withGroovyBuilder[]
target.withGroovyBuilder {                                          // <1>

    // GroovyObject methods available                               // <2>
    if (hasProperty("foo")) { /*...*/ }
    val foo = getProperty("foo")
    setProperty("foo", "bar")
    invokeMethod("name", arrayOf("parameters", 42, aReference))

    // Kotlin DSL utilities
    "name"("parameters", 42, aReference)                            // <3>
        "blockName" {                                               // <4>
            // Same Groovy Builder semantics on `blockName`
        }
    "another"("name" to "example", "url" to "https://example.com/") // <5>
}
// end::withGroovyBuilder[]

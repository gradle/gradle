import groovy.lang.Closure

fun somePlugin(action: SomePluginExtension.() -> Unit) =
    SomePluginExtension.action()

object SomePluginExtension {

    fun takingParameterLessClosure(closure: Closure<*>) =
        require(closure.call() == "result") { "parameter-less closure" }

    fun takingUnaryClosure(closure: Closure<*>) =
        require(closure.call("foo") == "result from single parameter foo") { "unary closure" }

    fun takingBinaryClosure(closure: Closure<*>) =
        require(closure.call("foo", "bar") == "result from parameters foo and bar") { "binary closure" }
}

// tag::kotlinClosure[]
somePlugin {

    // Adapt parameter-less function
    takingParameterLessClosure(KotlinClosure0({
        "result"
    }))

    // Adapt unary function
    takingUnaryClosure(KotlinClosure1<String, String>({
        "result from single parameter $this"
    }))

    // Adapt binary function
    takingBinaryClosure(KotlinClosure2<String, String, String>({ a, b ->
        "result from parameters $a and $b"
    }))
}
// end::kotlinClosure[]

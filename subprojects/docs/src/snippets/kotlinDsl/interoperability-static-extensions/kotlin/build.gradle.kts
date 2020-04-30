import myGroovy.TheTargetType
import myGroovy.TheTargetTypeGroovyExtension

val receiver = TheTargetType()
val aReference = ""

// tag::groovy-from-kotlin[]
TheTargetTypeGroovyExtension.groovyExtensionMethod(receiver, "parameters", 42, aReference)
// end::groovy-from-kotlin[]

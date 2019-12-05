import java.net.URI
// tag::resource[]

// Type must have a public constructor that takes the element name as a parameter
// Type must have a 'name' property, which should be read-only
open class Resource(val name: String) {
    var uri: URI? = null
    var userName: String? = null
}
// end::resource[]

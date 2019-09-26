// tag::resource[]

class Resource {
    // Type must have a 'name' property, which should be read-only
    final String name
    URI uri
    String userName

    // Type must have a public constructor that takes the element name as a parameter
    Resource(String name) {
        this.name = name
    }
}
// end::resource[]

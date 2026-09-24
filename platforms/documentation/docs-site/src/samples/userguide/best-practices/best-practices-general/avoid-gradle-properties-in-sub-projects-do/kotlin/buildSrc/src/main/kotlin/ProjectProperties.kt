import org.gradle.api.provider.Property

interface ProjectProperties { // <1>
    val propertyA: Property<String>
    val propertyB: Property<String>
}

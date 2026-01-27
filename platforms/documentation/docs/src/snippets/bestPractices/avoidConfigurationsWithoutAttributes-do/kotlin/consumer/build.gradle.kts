import org.gradle.api.attributes.Attribute
import org.gradle.api.attributes.Category

// tag::do-this[]
val customElementsDependencies = configurations.dependencyScope("customElementsDependencies")

dependencies {
    customElementsDependencies(project(":producer")) // <3>
}

val CUSTOM_ATTRIBUTE = Attribute.of("custom", String::class.java) // <4>
dependencies.attributesSchema.attribute(CUSTOM_ATTRIBUTE)

val customElements = configurations.resolvable("customElements") {
    extendsFrom(customElementsDependencies.get())
    attributes { // <5>
        attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.LIBRARY))
        attribute(CUSTOM_ATTRIBUTE, "my-custom-value")
    }
}

tasks.register("resolveCustom") {
    inputs.files(customElements.get())
    doLast {
        inputs.files.forEach { file: File ->
            logger.lifecycle("Resolved: ${file.name}")
        }
    }
}
// end::do-this[]

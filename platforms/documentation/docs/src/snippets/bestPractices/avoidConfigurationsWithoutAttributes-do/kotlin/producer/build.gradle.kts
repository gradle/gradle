import org.gradle.api.attributes.Attribute
import org.gradle.api.attributes.Category

// tag::do-this[]
val CUSTOM_ATTRIBUTE = Attribute.of("custom", String::class.java) // <1>
dependencies.attributesSchema.attribute(CUSTOM_ATTRIBUTE)

configurations {
    consumable("customElements") {
        attributes { // <2>
            attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.LIBRARY))
            attribute(CUSTOM_ATTRIBUTE, "my-custom-value")
        }
    }
}

val generateFile = tasks.register("generateFile") {
    val outputFile = layout.buildDirectory.file("custom/output.txt")
    outputs.file(outputFile)
    doLast {
        outputFile.get().asFile.writeText("Custom output from producer")
    }
}

artifacts {
    add("customElements", generateFile)
}
// end::do-this[]

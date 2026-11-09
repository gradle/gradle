import org.gradle.api.attributes.Attribute

// tag::do-this[]
val MY_FORMAT = Attribute.of("my-format", String::class.java) // <1>
dependencies.attributesSchema.attribute(MY_FORMAT)

val myElements = configurations.consumable("myElements") {
    attributes {
        attribute(MY_FORMAT, "json") // <2>
    }
}

tasks.register("printFormat") {
    val format = myElements.get().attributes.getAttribute(MY_FORMAT)!! // <3>
    doLast {
        logger.lifecycle("my-format = $format")
    }
}
// end::do-this[]

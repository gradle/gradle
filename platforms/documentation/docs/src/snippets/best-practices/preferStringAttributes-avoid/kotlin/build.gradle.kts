import org.gradle.api.Named
import org.gradle.api.attributes.Attribute

// tag::avoid-this[]
interface MyFormat : Named // <1>

val MY_FORMAT = Attribute.of("my-format", MyFormat::class.java)
dependencies.attributesSchema.attribute(MY_FORMAT)

val myElements = configurations.consumable("myElements") {
    attributes {
        attribute(MY_FORMAT, objects.named(MyFormat::class.java, "json")) // <2>
    }
}

tasks.register("printFormat") {
    val format = myElements.get().attributes.getAttribute(MY_FORMAT)!!.name // <3>
    doLast {
        logger.lifecycle("my-format = $format")
    }
}
// end::avoid-this[]

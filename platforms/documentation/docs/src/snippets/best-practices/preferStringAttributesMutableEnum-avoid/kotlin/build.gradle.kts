import org.gradle.api.Named
import org.gradle.api.attributes.Attribute

// tag::avoid-this[]
enum class MyFormat : Named {
    TYPE_A, TYPE_B;

    var extension: String = "unset" // <1>

    override fun getName(): String = name
}

MyFormat.TYPE_A.extension = "txt-a" // <2>

val MY_FORMAT = Attribute.of("my-format", MyFormat::class.java)
dependencies.attributesSchema.attribute(MY_FORMAT)

val myElements = configurations.consumable("myElements") {
    attributes {
        attribute(MY_FORMAT, MyFormat.TYPE_A)
    }
}

tasks.register("showExtension") {
    val value = myElements.get().attributes.getAttribute(MY_FORMAT)!! // <3>
    doLast {
        logger.lifecycle("attr value = ${value.name}, extension = ${value.extension}")
    }
}
// end::avoid-this[]

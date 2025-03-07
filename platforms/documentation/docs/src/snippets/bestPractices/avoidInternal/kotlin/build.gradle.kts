// tag::avoid-this[]
import org.gradle.api.internal.attributes.AttributeContainerInternal

configurations.create("bad") {
    attributes {
        attribute(Usage.USAGE_ATTRIBUTE, objects.named<Usage>(Usage.JAVA_RUNTIME))
    }
    val badMap = (attributes as AttributeContainerInternal).asMap() // <1>
    badMap.forEach { (key, value) ->
        logger.warn("$key -> $value")
    }
}
// end::avoid-this[]

// tag::do-this[]
configurations.create("good") {
    attributes {
        attribute(Usage.USAGE_ATTRIBUTE, objects.named<Usage>(Usage.JAVA_RUNTIME))
    }
    val goodMap = attributes.keySet().associate {
        Attribute.of(it.name, it.type) to attributes.getAttribute(it)
    }
    goodMap.forEach { (key, value) ->
        logger.warn("$key -> $value")
    }
}
// end::do-this[]

// tag::avoid-this[]
import org.gradle.api.internal.attributes.AttributeContainerInternal

configurations.create("bad") {
    attributes {
        attribute(Usage.USAGE_ATTRIBUTE, objects.named<Usage>(Usage.JAVA_RUNTIME))
        attribute(Category.CATEGORY_ATTRIBUTE, objects.named<Category>(Category.LIBRARY))
    }
    val badMap = (attributes as AttributeContainerInternal).asMap() // <1>
    logger.warn("Bad map")
    badMap.forEach { (key, value) ->
        logger.warn("$key -> $value")
    }
}
// end::avoid-this[]

// tag::do-this[]
configurations.create("good") {
    attributes {
        attribute(Usage.USAGE_ATTRIBUTE, objects.named<Usage>(Usage.JAVA_RUNTIME))
        attribute(Category.CATEGORY_ATTRIBUTE, objects.named<Category>(Category.LIBRARY))
    }
    val goodMap = attributes.keySet().associate { // <1>
        Attribute.of(it.name, it.type) to attributes.getAttribute(it)
    }
    logger.warn("Good map")
    goodMap.forEach { (key, value) ->
        logger.warn("$key -> $value")
    }
}
// end::do-this[]

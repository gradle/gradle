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

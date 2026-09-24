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

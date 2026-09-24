import javax.inject.Inject

plugins {
    id("application")
}

repositories {
    mavenCentral()
}

val customConfig = configurations.create("customConfig") {
    isCanBeResolved = true
    isCanBeConsumed = false
    attributes {
        attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_API))
    }
}

val kindAttr = Attribute.of("kind", String::class.java)

dependencies {
    customConfig(project(":lib"))
    attributesSchema {
        attribute(kindAttr) {
            disambiguationRules.add(MyCustomDisambiguationRule::class.java)
        }
    }
}

abstract class MyCustomDisambiguationRule : AttributeDisambiguationRule<String> {
    override fun execute(details: MultipleCandidatesDetails<String>) {
        details.closestMatch("api")
    }
}

// tag::disambiguation_rule[]
dependencies {
    attributesSchema {
        attribute(Usage.USAGE_ATTRIBUTE) {  // <1>
            disambiguationRules.add(CustomDisambiguationRule::class.java)  // <2>
        }
    }
}

abstract class CustomDisambiguationRule @Inject constructor(
    private val objects: ObjectFactory
) : AttributeDisambiguationRule<Usage> {
    override fun execute(details: MultipleCandidatesDetails<Usage>) {
        // Prefer the JAVA_API usage over others (e.g., JAVA_RUNTIME) when multiple candidates exist
        details.closestMatch(objects.named(Usage::class.java, Usage.JAVA_API))  // <3>
    }
}
// end::disambiguation_rule[]

tasks.register("resolveCustom") {
    doLast {
        println("Resolved: " + customConfig.files)
    }
}

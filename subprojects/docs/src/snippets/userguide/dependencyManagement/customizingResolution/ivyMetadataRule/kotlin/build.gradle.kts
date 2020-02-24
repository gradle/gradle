import javax.inject.Inject

plugins {
    `java-library`
}

repositories {
    ivy {
        url = uri("$projectDir/repo")
    }
}

// tag::ivy-component-metadata-rule[]
open class IvyVariantDerivationRule : ComponentMetadataRule {
    @Inject open fun getObjects(): ObjectFactory = throw UnsupportedOperationException()

    override fun execute(context: ComponentMetadataContext) {
        context.details.addVariant("runtimeElements", "default") {
            attributes {
                attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, getObjects().named(LibraryElements.JAR))
                attribute(Category.CATEGORY_ATTRIBUTE, getObjects().named(Category.LIBRARY))
                attribute(Usage.USAGE_ATTRIBUTE, getObjects().named(Usage.JAVA_RUNTIME))
            }
        }
        context.details.addVariant("apiElements", "compile") {
            attributes {
                attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, getObjects().named(LibraryElements.JAR))
                attribute(Category.CATEGORY_ATTRIBUTE, getObjects().named(Category.LIBRARY))
                attribute(Usage.USAGE_ATTRIBUTE, getObjects().named(Usage.JAVA_API))
            }
        }
    }
}

dependencies {
    components { all<IvyVariantDerivationRule>() }
}
// end::ivy-component-metadata-rule[]

dependencies {
    implementation("org.sample:api:2.0")
}

tasks.register("compileClasspathArtifacts") {
    doLast {
        configurations["compileClasspath"].forEach { println(it.name) }
    }
}
tasks.register("runtimeClasspathArtifacts") {
    doLast {
        configurations["runtimeClasspath"].forEach { println(it.name) }
    }
}

plugins {
    `java-library`
}

repositories {
    ivy {
        url = uri("$projectDir/repo")
    }
}

// tag::ivy-component-metadata-rule[]
abstract class IvyVariantDerivationRule @Inject internal constructor(objectFactory: ObjectFactory) : ComponentMetadataRule {
    private val jarLibraryElements: LibraryElements
    private val libraryCategory: Category
    private val javaRuntimeUsage: Usage
    private val javaApiUsage: Usage

    init {
        jarLibraryElements = objectFactory.named(LibraryElements.JAR)
        libraryCategory = objectFactory.named(Category.LIBRARY)
        javaRuntimeUsage = objectFactory.named(Usage.JAVA_RUNTIME)
        javaApiUsage = objectFactory.named(Usage.JAVA_API)
    }

    override fun execute(context: ComponentMetadataContext) {
        // This filters out any non Ivy module
        if(context.getDescriptor(IvyModuleDescriptor::class) == null) {
            return
        }

        context.details.addVariant("runtimeElements", "default") {
            attributes {
                attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, jarLibraryElements)
                attribute(Category.CATEGORY_ATTRIBUTE, libraryCategory)
                attribute(Usage.USAGE_ATTRIBUTE, javaRuntimeUsage)
            }
        }
        context.details.addVariant("apiElements", "compile") {
            attributes {
                attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, jarLibraryElements)
                attribute(Category.CATEGORY_ATTRIBUTE, libraryCategory)
                attribute(Usage.USAGE_ATTRIBUTE, javaApiUsage)
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
    val compileClasspath: FileCollection = configurations["compileClasspath"]
    doLast {
        compileClasspath.forEach { println(it.name) }
    }
}
tasks.register("runtimeClasspathArtifacts") {
    val runtimeClasspath: FileCollection = configurations["runtimeClasspath"]
    doLast {
        runtimeClasspath.forEach { println(it.name) }
    }
}

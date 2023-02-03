plugins {
    `java-library`
}


repositories {
    mavenCentral()
}

// tag::test_dependency[]
dependencies {
    testImplementation("junit:junit:4.13")
    testImplementation(project(":producer"))
}
// end::test_dependency[]

// tag::ask-for-instrumented-classes[]
configurations {
    testRuntimeClasspath {
        attributes {
            attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objects.named(LibraryElements::class.java, "instrumented-jar"))
        }
    }
}
// end::ask-for-instrumented-classes[]

// tag::compatibility-rule-use[]
dependencies {
    attributesSchema {
        attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE) {
            compatibilityRules.add(InstrumentedJarsRule::class.java)
        }
    }
}
// end::compatibility-rule-use[]

// tag::compatibility-rule[]
abstract class InstrumentedJarsRule: AttributeCompatibilityRule<LibraryElements> {

    override fun execute(details: CompatibilityCheckDetails<LibraryElements>) = details.run {
        if (consumerValue?.name == "instrumented-jar" && producerValue?.name == "jar") {
            compatible()
        }
    }
}
// end::compatibility-rule[]

tasks.register("showTestClasspath") {
    val testCompileClasspath: FileCollection = configurations.testCompileClasspath.get()
    val testRuntimeClasspath: FileCollection = configurations.testRuntimeClasspath.get()
    inputs.files(testCompileClasspath)
    inputs.files(testRuntimeClasspath)
    doLast {
        println(testCompileClasspath.files.map(File::getName))
        println(testRuntimeClasspath.files.map(File::getName))
    }
}

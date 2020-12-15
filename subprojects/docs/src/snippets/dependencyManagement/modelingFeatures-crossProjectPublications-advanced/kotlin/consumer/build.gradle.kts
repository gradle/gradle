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
open class InstrumentedJarsRule: AttributeCompatibilityRule<LibraryElements> {

    override fun execute(details: CompatibilityCheckDetails<LibraryElements>) = details.run {
        if (consumerValue?.name == "instrumented-jar" && producerValue?.name == "jar") {
            compatible()
        }
    }
}
// end::compatibility-rule[]

tasks.register("showTestClasspath") {
    inputs.files(configurations.testCompileClasspath)
    inputs.files(configurations.testRuntimeClasspath)
    doLast {
        println(configurations.testCompileClasspath.get().files.map(File::getName))
        println(configurations.testRuntimeClasspath.get().files.map(File::getName))
    }
}

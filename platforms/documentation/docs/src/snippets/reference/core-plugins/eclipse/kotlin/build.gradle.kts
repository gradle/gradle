// tag::module-when-merged[]
import org.gradle.plugins.ide.eclipse.model.AbstractClasspathEntry
import org.gradle.plugins.ide.eclipse.model.Classpath
// end::module-when-merged[]
// tag::use-eclipse-plugin[]
// tag::use-eclipse-wtp-plugin[]
plugins {
    // end::use-eclipse-plugin[]
// end::use-eclipse-wtp-plugin[]
    war
// tag::use-eclipse-plugin[]
    eclipse
// end::use-eclipse-plugin[]
// tag::use-eclipse-wtp-plugin[]
    `eclipse-wtp`
// end::use-eclipse-wtp-plugin[]
// tag::use-eclipse-plugin[]
// tag::use-eclipse-wtp-plugin[]
}
// end::use-eclipse-plugin[]
// end::use-eclipse-wtp-plugin[]


// tag::module-when-merged[]

eclipse.classpath.file {
    whenMerged(Action<Classpath> { ->
        entries.filter { entry -> entry.kind == "lib" }
            .forEach { (it as AbstractClasspathEntry).isExported = false }
    })
}
// end::module-when-merged[]

val integTest = sourceSets.create("integTest")
val functional = configurations.create("functional")

eclipse {
    classpath {
        // TODO k2-gradle9 replace with += once https://youtrack.jetbrains.com/issue/KT-68963 is fixed
        plusConfigurations.add(functional)
    }
}

// tag::test-sources[]
eclipse {
    classpath {
        testSourceSets = testSourceSets.get() + setOf(integTest)
        testConfigurations = testConfigurations.get() + setOf(functional)
    }
}
// end::test-sources[]

// tag::test-fixtures[]
eclipse {
    classpath {
        containsTestFixtures = true
    }
}
// end::test-fixtures[]


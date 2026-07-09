// tag::module-when-merged[]
import org.gradle.plugins.ide.eclipse.model.AbstractClasspathEntry
// end::module-when-merged[]
// tag::module-before-merged[]
// tag::module-when-merged[]
import org.gradle.plugins.ide.eclipse.model.Classpath
// end::module-when-merged[]
// end::module-before-merged[]
// tag::project-before-merged[]
import org.gradle.plugins.ide.eclipse.model.Project
// end::project-before-merged[]
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


// tag::module-before-merged[]
// tag::module-when-merged[]

eclipse.classpath.file {
// end::module-when-merged[]
    beforeMerged(Action<Classpath> {
        entries.removeAll { entry -> entry.kind == "lib" || entry.kind == "var" }
    })
// end::module-before-merged[]
// tag::module-when-merged[]
    whenMerged(Action<Classpath> { ->
        entries.filter { entry -> entry.kind == "lib" }
            .forEach { (it as AbstractClasspathEntry).isExported = false }
    })
// tag::module-before-merged[]
}
// end::module-before-merged[]
// end::module-when-merged[]

// tag::project-before-merged[]

eclipse.project.file.beforeMerged(Action<Project> {
    natures.clear()
})
// end::project-before-merged[]

// tag::classpath-with-xml[]

eclipse.classpath.file.withXml(Action<XmlProvider> {
    asNode().appendNode("classpathentry", mapOf("kind" to "output", "path" to "custom-bin"))
})
// end::classpath-with-xml[]

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


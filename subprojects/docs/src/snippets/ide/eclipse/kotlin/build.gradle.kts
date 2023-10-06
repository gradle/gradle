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
// tag::wtp-with-xml[]
import org.w3c.dom.Element
// end::wtp-with-xml[]

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

// tag::wtp-with-xml[]

eclipse.wtp.facet.file.withXml(Action<XmlProvider> {
    fun Element.firstElement(predicate: Element.() -> Boolean) =
        childNodes
            .run { (0 until length).map(::item) }
            .filterIsInstance<Element>()
            .first { it.predicate() }

    asElement()
        .firstElement { tagName === "fixed" && getAttribute("facet") == "jst.java" }
        .setAttribute("facet", "jst2.java")
})
// end::wtp-with-xml[]

val integTest by sourceSets.creating
val functional by configurations.creating

eclipse {
    classpath {
        plusConfigurations += functional
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


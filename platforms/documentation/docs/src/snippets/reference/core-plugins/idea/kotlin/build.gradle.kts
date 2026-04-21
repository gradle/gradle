// tag::module-before-merged[]
// tag::module-when-merged[]
import org.gradle.plugins.ide.idea.model.Module
// end::module-when-merged[]
// end::module-before-merged[]

// tag::module-when-merged[]
import org.gradle.plugins.ide.idea.model.ModuleDependency
// end::module-when-merged[]

// tag::project-before-merged[]
import org.gradle.plugins.ide.idea.model.Project
// end::project-before-merged[]

// tag::project-with-xml[]
import org.w3c.dom.Element
// end::project-with-xml[]

// tag::use-plugin[]
plugins {
    idea
}
// end::use-plugin[]

// tag::module-before-merged[]

idea.module.iml {
    beforeMerged(Action<Module> {
        dependencies.clear()
    })
}
// end::module-before-merged[]

// tag::project-before-merged[]

idea.project.ipr {
    beforeMerged(Action<Project> {
        modulePaths.clear()
    })
}
// end::project-before-merged[]

// tag::module-when-merged[]

idea.module.iml {
    whenMerged(Action<Module> {
        dependencies.forEach {
            (it as ModuleDependency).isExported = true
        }
    })
}
// end::module-when-merged[]

// tag::project-with-xml[]

idea.project.ipr {
    withXml(Action<XmlProvider> {
        fun Element.firstElement(predicate: (Element.() -> Boolean)) =
            childNodes
                .run { (0 until length).map(::item) }
                .filterIsInstance<Element>()
                .first { it.predicate() }

        asElement()
            .firstElement { tagName == "component" && getAttribute("name") == "VcsDirectoryMappings" }
            .firstElement { tagName == "mapping" }
            .setAttribute("vcs", "Git")
    })
}
// end::project-with-xml[]

import org.w3c.dom.Element

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

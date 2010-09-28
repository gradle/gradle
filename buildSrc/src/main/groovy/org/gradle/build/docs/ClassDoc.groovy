package org.gradle.build.docs

import org.w3c.dom.Element

class ClassDoc {
    final Element classSection
    final String className
    final String id
    final String classSimpleName

    ClassDoc(String className, Element classSection, DslModel model) {
        this.classSection = classSection
        this.className = className
        id = "dsl:$className"
        classSimpleName = className.tokenize('.').last()

        classSection.extends.each { Element e ->
            e.parentNode.removeChild(e)
            ClassDoc supertype = model.getClassDoc(e.text().trim())
            supertype.propertiesTable.tr.each { Element tr ->
                propertiesTable << tr
            }
            supertype.methodsTable.tr.each { Element tr ->
                methodsTable << tr
            }
        }

        if (description) {
            description + {
                section {
                    title('API Documentation')
                    para {
                        apilink('class': className)
                    }
                }
            }
        }
    }

    Element getPropertiesTable() {
        return getSection('Properties').table[0]
    }

    Element getMethodsTable() {
        return getSection('Methods').table[0]
    }

    private Element getSection(String title) {
        def sections = classSection.section.findAll { it.title[0].text().trim() == title }
        if (sections.size() < 1) {
            throw new RuntimeException("Docbook content for $className does not contain a '$title' section.")
        }
        return sections[0]
    }
    
    Element getDescription() {
        return classSection.para[0]
    }
}

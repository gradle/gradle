package org.gradle.build.docs

import org.w3c.dom.Element

class ClassDoc {
    final Element classSection
    final String className
    final String id
    final String classSimpleName
    final ClassMetaData classMetaData

    ClassDoc(String className, Element classSection, ClassMetaData classMetaData, ExtensionMetaData extensionMetaData, DslModel model) {
        this.classSection = classSection
        this.className = className
        id = "dsl:$className"
        classSimpleName = className.tokenize('.').last()
        this.classMetaData = classMetaData

        classSection['@id'] = id
        classSection.addFirst {
            title(classSimpleName)
        }

        propertiesTable.tr.each { Element tr ->
            def cells = tr.td
            if (cells.size() != 2) {
                throw new RuntimeException("Expected 2 cells in <tr>, found: $tr")
            }
            String propName = cells[0].text().trim()
            String type = classMetaData.classProperties[propName]
            if (!type) {
                throw new RuntimeException("No metadata for property '$className.$propName'. Available properties: ${classMetaData.classProperties.keySet()}")
            }
            tr.td[0].children = { literal(propName) }
            tr.td[0] + {
                td {
                    if (type.startsWith('org.gradle')) {
                        apilink('class': type)
                    } else if (type.startsWith('java.lang.') || type.startsWith('java.util.') || type.startsWith('java.io.')) {
                        classname(type.tokenize('.').last())
                    } else {
                        classname(type)
                    }
                }
            }
        }

        if (classMetaData.superClassName) {
            ClassDoc supertype = model.getClassDoc(classMetaData.superClassName)
            supertype.propertiesTable.tr.each { Element tr ->
                propertiesTable << tr
            }
            supertype.methodsTable.tr.each { Element tr ->
                methodsTable << tr
            }
        }

        classSection.section[0].addBefore {
            section {
                title('API Documentation')
                para {
                    apilink('class': className, lang: lang)
                }
            }
        }

        extensionMetaData.extensionClasses.each { Map map ->
            ClassDoc extensionClassDoc = model.getClassDoc(map.extensionClass)
            classSection << extensionClassDoc.classSection
            
            classSection.lastChild.title[0].text = "${map.plugin} - ${extensionClassDoc.classSimpleName}"
        }
    }

    Element getPropertiesTable() {
        return getSection('Properties').table[0]
    }

    Element getMethodsTable() {
        return getSection('Methods').table[0]
    }

    String getLang() {
        return classMetaData.groovy ? 'groovy' : 'java'
    }

    private Element getSection(String title) {
        def sections = classSection.section.findAll { it.title[0].text().trim() == title }
        if (sections.size() < 1) {
            throw new RuntimeException("Docbook content for $className does not contain a '$title' section.")
        }
        return sections[0]
    }

    Element getHasDescription() {
        def paras = classSection.para
        return paras.size() > 0 ? paras[0] : null
    }

    Element getDescription() {
        def paras = classSection.para
        if (paras.size() < 1) {
            throw new RuntimeException("Docbook content for $className does not contain a description paragraph.")
        }
        return paras[0]
    }
}

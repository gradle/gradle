/*
 * Copyright 2010 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gradle.build.docs.dsl.docbook

import org.gradle.build.docs.dsl.model.ClassMetaData
import org.gradle.build.docs.dsl.model.PropertyMetaData
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node

class ClassDoc {
    final Element classSection
    final String className
    final String id
    final String classSimpleName
    final ClassMetaData classMetaData

    ClassDoc(String className, Element classContent, Document targetDocument, ClassMetaData classMetaData, ExtensionMetaData extensionMetaData, DslDocModel model, JavadocConverter javadocConverter) {
        this.className = className
        id = className
        classSimpleName = className.tokenize('.').last()
        this.classMetaData = classMetaData

        classSection = targetDocument.createElement('chapter')

        classSection.setAttribute('id', id)
        classSection.addFirst {
            title(classSimpleName)
        }
        classContent.childNodes.each { Node n ->
            classSection << n
        }

        propertiesTable.addFirst { title("Properties - $classSimpleName") }
        def propertyTableHeader = propertiesTable.thead[0].tr[0]
        propertyTableHeader.td[0].addAfter { td('Description'); td('Type') }
        propertiesTable.tr.each { Element tr ->
            def cells = tr.td
            if (cells.size() < 1) {
                throw new RuntimeException("Expected at leat 1 cell in <tr>, found: $tr")
            }
            String propName = cells[0].text().trim()
            PropertyMetaData property = classMetaData.classProperties[propName]
            if (!property) {
                throw new RuntimeException("No metadata for property '$className.$propName'. Available properties: ${classMetaData.classProperties.keySet()}")
            }
            String type = property.type
            tr.td[0].children = { literal(propName) }
            tr.td[0].addAfter { td() }
            javadocConverter.parse(property.rawCommentText, property, classMetaData).docbook.each { node ->
                tr.td[1] << node
            }
            tr.td[1].addAfter {
                td {
                    if (type.startsWith('org.gradle')) {
                        apilink('class': type)
                    } else if (type.startsWith('java.lang.') || type.startsWith('java.util.') || type.startsWith('java.io.')) {
                        classname(type.tokenize('.').last())
                    } else {
                        classname(type)
                    }
                    if (!property.writeable) {
                        text(" (read-only)")
                    }
                }
            }
        }

        methodsTable.addFirst { title("Methods - $classSimpleName")}

        if (classMetaData.superClassName) {
            ClassDoc supertype = model.getClassDoc(classMetaData.superClassName)
            supertype.propertiesTable.tr.each { Element tr ->
                while (tr.td.size() < propertyTableHeader.td.size()) {
                    tr << { td() }
                }
                propertiesTable << tr
            }
            supertype.methodsTable.tr.each { Element tr ->
                methodsTable << tr
            }
        }

        def properties = getSection('Properties')

        def javadocComment = javadocConverter.parse(classMetaData.rawCommentText, classMetaData)
        javadocComment.docbook.each { node ->
            properties.addBefore(node)
        }

        properties.addBefore {
            section {
                title('API Documentation')
                para {
                    apilink('class': className, style: style)
                }
            }
        }

//                extensionMetaData.extensionClasses.each { Map map ->
//                    ClassDoc extensionClassDoc = model.getClassDoc(map.extensionClass)
//                    classSection << extensionClassDoc.classSection
//
//                    classSection.lastChild.title[0].text = "${map.plugin} - ${extensionClassDoc.classSimpleName}"
//                }
    }

    Element getPropertiesTable() {
        return getSection('Properties').table[0]
    }

    Element getMethodsTable() {
        return getSection('Methods').table[0]
    }

    String getStyle() {
        return classMetaData.groovy ? 'groovydoc' : 'javadoc'
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

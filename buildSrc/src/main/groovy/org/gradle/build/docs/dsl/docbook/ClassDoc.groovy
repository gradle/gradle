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
import org.gradle.build.docs.dsl.model.MethodMetaData

class ClassDoc {
    final Element classSection
    final String className
    final String id
    final String classSimpleName
    final ClassMetaData classMetaData
    private final JavadocConverter javadocConverter
    private final DslDocModel model

    ClassDoc(String className, Element classContent, Document targetDocument, ClassMetaData classMetaData, ExtensionMetaData extensionMetaData, DslDocModel model, JavadocConverter javadocConverter) {
        this.className = className
        id = className
        classSimpleName = className.tokenize('.').last()
        this.classMetaData = classMetaData
        this.javadocConverter = javadocConverter
        this.model = model

        classSection = targetDocument.createElement('chapter')

        classSection.setAttribute('id', id)
        classSection.addFirst {
            title(classSimpleName)
        }
        classContent.childNodes.each { Node n ->
            classSection << n
        }
    }

    ClassDoc mergeContent() {
        mergeDescription()
        mergeProperties()
        mergeMethods()
        return this
    }

    ClassDoc mergeDescription() {
        def properties = getSection('Properties')

        def javadocComment = javadocConverter.parse(classMetaData)
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

        return this
    }

    ClassDoc mergeProperties() {
        propertiesTable.addFirst { title("Properties - $classSimpleName") }
        def propertyTableHeader = propertiesTable.thead[0].tr[0]
        propertyTableHeader.td[0].addAfter { td('Description'); td('Type') }

        Set<String> props = [] as Set

        propertiesTable.tr.each { Element tr ->
            def cells = tr.td
            if (cells.size() < 1) {
                throw new RuntimeException("Expected at least 1 cell in <tr>, found: $tr")
            }
            String propName = cells[0].text().trim()
            props << propName
            PropertyMetaData property = classMetaData.findProperty(propName)
            if (!property) {
                throw new RuntimeException("No metadata for property '$className.$propName'. Available properties: ${classMetaData.propertyNames}")
            }
            String type = property.type.signature
            tr.td[0].children = { literal(propName) }
            tr.td[0].addAfter { td() }
            javadocConverter.parse(property).docbook.each { node ->
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

        if (classMetaData.superClassName) {
            ClassDoc supertype = model.getClassDoc(classMetaData.superClassName)
            supertype.propertiesTable.tr.each { Element tr ->
                String propName = tr.td[0].text().trim()
                if (props.add(propName)) {
                    while (tr.td.size() < propertyTableHeader.td.size()) {
                        tr << { td() }
                    }
                    propertiesTable << tr
                }
            }
        }

        return this
    }

    ClassDoc mergeMethods() {
        methodsTable.addFirst { title("Methods - $classSimpleName")}

        def methodTableHeader = methodsTable.thead[0].tr[0]
        methodTableHeader.td[0].addAfter { td('Description'); td('Signature') }

        methodsTable.tr.each { Element tr ->
            def cells = tr.td
            if (cells.size() < 1) {
                throw new RuntimeException("Expected at least 1 cell in <tr>, found: $tr")
            }
            String methodName = cells[0].text().trim()
            Collection<MethodMetaData> methods = classMetaData.declaredMethods.findAll { it.name == methodName }
            if (!methods) {
                throw new RuntimeException("No metadata for method '$className.$methodName()'. Available methods: ${classMetaData.declaredMethods.collect{it.name} as TreeSet}")
            }

            Element row = tr
            methods.eachWithIndex { MethodMetaData method, int index ->
                if (index > 0) {
                    def sibling = row.nextSibling
                    row = row.ownerDocument.createElement('tr')
                    methodsTable.insertBefore(row, sibling)
                    row << { td() }
                }

                String signature = method.signature
                row.td[0].children = { literal(methodName) }
                row.td[0].addAfter { td() }
                def comment = javadocConverter.parse(method).docbook
                comment.each { node ->
                    row.td[1] << node
                }
                row.td[1].addAfter {
                    td {
                        literal(signature)
                    }
                }

                methodsTable.addAfter {
                    section(id: signature) {
                        title(signature)
                        comment.each { node -> appendChild(node) }
                    }
                }
            }
        }

        if (classMetaData.superClassName) {
            ClassDoc supertype = model.getClassDoc(classMetaData.superClassName)
            supertype.methodsTable.tr.each { Element tr ->
                methodsTable << tr
            }
        }

        return this
    }

    Element getPropertiesTable() {
        return getTable('Properties')
    }

    Element getMethodsTable() {
        return getTable('Methods')
    }

    String getStyle() {
        return classMetaData.groovy ? 'groovydoc' : 'javadoc'
    }

    private Element getTable(String title) {
        def table = getSection(title).table[0]
        if (!table) {
            throw new RuntimeException("Section '$title' does not contain a <table> element.")
        }
        if (!table.thead[0]) {
            throw new RuntimeException("Table '$title' does not contain a <thead> element.")
        }
        if (!table.thead[0].tr[0]) {
            throw new RuntimeException("Table '$title' does not contain a <thead>/<tr> element.")
        }
        return table
    }

    private Element getSection(String title) {
        def sections = classSection.section.findAll {
            it.title[0] && it.title[0].text().trim() == title
        }
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

    BlockDoc getBlock(String name) {
        def method = classMetaData.declaredMethods.find { it.name == name && it.parameters.size() == 1 && it.parameters[0].type.signature == Closure.class.name }
        if (!method) {
            throw new RuntimeException("Class $className does not have a script block '$name'.")
        }

        Element description = javadocConverter.parse(method).docbook.find { Element e -> e.nodeName == 'para' }

        return new BlockDoc(method.signature, description)
    }
}

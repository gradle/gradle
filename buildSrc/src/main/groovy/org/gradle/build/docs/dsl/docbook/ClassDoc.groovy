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
    final List<PropertyDoc> classProperties = []
    final List<MethodDoc> classMethods = []
    private final JavadocConverter javadocConverter
    private final DslDocModel model
    private final ClassLinkRenderer linkRenderer
    final Element propertiesTable
    final Element methodsTable

    ClassDoc(String className, Element classContent, Document targetDocument, ClassMetaData classMetaData, ExtensionMetaData extensionMetaData, DslDocModel model, JavadocConverter javadocConverter) {
        this.className = className
        id = className
        classSimpleName = className.tokenize('.').last()
        this.classMetaData = classMetaData
        this.javadocConverter = javadocConverter
        linkRenderer = new ClassLinkRenderer(targetDocument, model)
        this.model = model

        classSection = targetDocument.createElement('chapter')

        classSection.setAttribute('id', id)
        classSection.addFirst {
            title(classSimpleName)
        }
        classContent.childNodes.each { Node n ->
            classSection << n
        }

        propertiesTable = getTable('Properties')
        methodsTable = getTable('Methods')
    }

    def getClassProperties() { return classProperties }
    def getClassMethods() { return classMethods }

    ClassDoc mergeContent() {
        buildProperties()
        buildMethods()
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

        classSection.title[0].addAfter {
            segmentedlist {
                segtitle('API Documentation')
                seglistitem {
                    seg { apilink('class': className, style: style) }
                }
            }
        }

        return this
    }

    ClassDoc buildProperties() {
        Set<String> props = [] as Set
        propertiesTable.tr.each { Element tr ->
            def cells = tr.td.collect { it }
            if (cells.size() < 1) {
                throw new RuntimeException("Expected at least 1 cell in <tr>, found: $tr")
            }
            String propName = cells[0].text().trim()
            props << propName
            PropertyMetaData property = classMetaData.findProperty(propName)
            if (!property) {
                throw new RuntimeException("No metadata for property '$className.$propName'. Available properties: ${classMetaData.propertyNames}")
            }
            def additionalValues = cells.subList(1, cells.size())
            PropertyDoc propertyDoc = new PropertyDoc(property, javadocConverter.parse(property).docbook, additionalValues)
            classProperties << propertyDoc
        }
        if (classMetaData.superClassName) {
            ClassDoc supertype = model.getClassDoc(classMetaData.superClassName)
            supertype.getClassProperties().each { propertyDoc ->
                if (props.add(propertyDoc.name)) {
                    classProperties << propertyDoc
                }
            }
        }

        classProperties.sort { it.name }
        return this
    }

    ClassDoc mergeProperties() {
        def propertyTableHeader = propertiesTable.thead[0].tr[0]
        propertyTableHeader.td[0].addAfter { td('Description') }
        def colCount = propertyTableHeader.td.size()

        propertiesTable.children = { }
        propertiesTable << {
            title("Properties - $classSimpleName")
            thead {
                appendChild(propertyTableHeader)
            }
        }

        classProperties.each { propDoc ->
            propertiesTable << {
                tr {
                    td { link(linkend: propDoc.id) { literal(propDoc.name) } }
                    td { appendChild(propDoc.description) }
                    propDoc.additionalValues.each { td ->
                        appendChild(td)
                    }
                    (colCount - 2 - propDoc.additionalValues.size()).times { td() }
                }
            }

            propertiesTable.addAfter {
                section(id: propDoc.id) {
                    title(role: 'signature') {
                        appendChild linkRenderer.link(propDoc.metaData.type)
                        text(' ')
                        literal(role: 'name', propDoc.name)
                        if (!propDoc.metaData.writeable) {
                            text(' (read-only)')
                        }
                    }
                    propDoc.comment.each { node ->
                        appendChild(node)
                    }
                }
            }
        }

        return this
    }

    ClassDoc buildMethods() {
        Set signatures = [] as Set

        methodsTable.tr.each { Element tr ->
            def cells = tr.td
            if (cells.size() != 1) {
                throw new RuntimeException("Expected 1 cell in <tr>, found: $tr")
            }
            String methodName = cells[0].text().trim()
            Collection<MethodMetaData> methods = classMetaData.declaredMethods.findAll { it.name == methodName }
            if (!methods) {
                throw new RuntimeException("No metadata for method '$className.$methodName()'. Available methods: ${classMetaData.declaredMethods.collect {it.name} as TreeSet}")
            }
            methods.each { method ->
                classMethods << new MethodDoc(method, javadocConverter.parse(method).docbook)
                signatures << method.overrideSignature
            }
        }

        if (classMetaData.superClassName) {
            ClassDoc supertype = model.getClassDoc(classMetaData.superClassName)
            supertype.getClassMethods().each { method ->
                if (signatures.add(method.metaData.overrideSignature)) {
                    classMethods << method
                }
            }
        }

        classMethods.sort { it.metaData.overrideSignature }

        return this
    }

    ClassDoc mergeMethods() {
        methodsTable.children = {
            title("Methods - $classSimpleName")
            thead {
                tr {
                    td('Name')
                    td('Description')
                }
            }
        }

        classMethods.each { method ->
            methodsTable << {
                tr {
                    td { link(linkend: method.id) { literal(method.name) } }
                    td { appendChild method.description }
                }
            }

            methodsTable.addAfter {
                section(id: method.id) {
                    title(role: 'signature') {
                        appendChild linkRenderer.link(method.metaData.returnType)
                        literal(role: 'name', method.name)
                        text('(')
                        method.metaData.parameters.eachWithIndex {param, i ->
                            if (i > 0) {
                                text(', ')
                            }
                            appendChild linkRenderer.link(param.type)
                            text(" $param.name")
                        }
                        text(')')
                    }
                    method.comment.each { node -> appendChild(node) }
                }
            }
        }

        return this
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

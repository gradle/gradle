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
    private final String className
    private final String id
    private final String simpleName
    final ClassMetaData classMetaData
    private final Element classSection
    private final ExtensionMetaData extensionMetaData
    private final List<PropertyDoc> classProperties = []
    private final List<MethodDoc> classMethods = []
    private final List<BlockDoc> classBlocks = []
    private final List<ClassExtensionDoc> classExtensions = []
    private final JavadocConverter javadocConverter
    private final DslDocModel model
    private final Element propertiesTable
    private final Element methodsTable
    private final Element propertiesSection
    private final Element methodsSection
    private List<Element> comment

    ClassDoc(String className, Element classContent, Document targetDocument, ClassMetaData classMetaData, ExtensionMetaData extensionMetaData, DslDocModel model, JavadocConverter javadocConverter) {
        this.className = className
        id = className
        simpleName = className.tokenize('.').last()
        this.classMetaData = classMetaData
        this.javadocConverter = javadocConverter
        this.model = model
        this.extensionMetaData = extensionMetaData

        classSection = targetDocument.createElement('chapter')

        classContent.childNodes.each { Node n ->
            classSection << n
        }

        propertiesTable = getTable('Properties')
        propertiesSection = propertiesTable.parentNode
        methodsTable = getTable('Methods')
        methodsSection = methodsTable.parentNode
    }

    def getId() { return id }

    def getName() { return className }

    def getSimpleName() { return simpleName }

    def getComment() { return comment }

    def getClassProperties() { return classProperties }

    def getClassMethods() { return classMethods }

    def getClassBlocks() { return classBlocks }

    def getClassExtensions() { return classExtensions }

    def getClassSection() { return classSection }

    def getPropertiesTable() { return propertiesTable }

    def getPropertiesSection() { return propertiesSection }

    def getMethodsTable() { return methodsTable }

    def getMethodsSection() { return methodsSection }

    def getBlocksTable() { return getTable('Script blocks') }

    ClassDoc mergeContent() {
        buildDescription()
        buildProperties()
        buildMethods()
        buildExtensions()
        return this
    }

    ClassDoc buildDescription() {
        comment = javadocConverter.parse(classMetaData).docbook
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
            if (propertyDoc.description == null) {
                throw new RuntimeException("Docbook content for '$className.$propName' does not contain a description paragraph.")
            }
            classProperties << propertyDoc
        }
        if (classMetaData.superClassName) {
            ClassDoc supertype = model.getClassDoc(classMetaData.superClassName)
            supertype.getClassProperties().each { propertyDoc ->
                if (props.add(propertyDoc.name)) {
                    classProperties << propertyDoc.forClass(classMetaData)
                }
            }
        }

        classProperties.sort { it.name }
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
                def methodDoc = new MethodDoc(method, javadocConverter.parse(method).docbook)
                if (!methodDoc.description) {
                    throw new RuntimeException("Docbook content for '$className $method.signature' does not contain a description paragraph.")
                }
                if (method.parameters.size() == 1 && method.parameters[0].type.signature == Closure.class.name && classMetaData.findProperty(method.name)) {
                    classBlocks << new BlockDoc(methodDoc)
                } else {
                    classMethods << methodDoc
                    signatures << method.overrideSignature
                }
            }
        }

        if (classMetaData.superClassName) {
            ClassDoc supertype = model.getClassDoc(classMetaData.superClassName)
            supertype.getClassMethods().each { method ->
                if (signatures.add(method.metaData.overrideSignature)) {
                    classMethods << method.forClass(classMetaData)
                }
            }
        }

        classMethods.sort { it.metaData.overrideSignature }
        classBlocks.sort { it.name }

        return this
    }

    ClassDoc buildExtensions() {
        extensionMetaData.extensionClasses.keySet().each { String pluginId ->
            List<ClassDoc> extensionClasses = []
            extensionMetaData.extensionClasses.get(pluginId).each { String extensionClass ->
                extensionClasses << model.getClassDoc(extensionClass)
            }
            extensionClasses.sort { it.name }
            classExtensions << new ClassExtensionDoc(pluginId, extensionClasses)
        }

        classExtensions.sort { it.pluginId }

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
        def block = classBlocks.find { it.name == name }
        if (!block) {
            throw new RuntimeException("Class $className does not have a script block '$name'.")
        }
        return block
    }
}

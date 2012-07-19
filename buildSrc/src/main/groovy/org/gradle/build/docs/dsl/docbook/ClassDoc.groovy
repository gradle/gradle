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

import org.gradle.build.docs.dsl.source.model.ClassMetaData
import org.gradle.build.docs.dsl.source.model.MethodMetaData
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.gradle.build.docs.dsl.docbook.model.*

class ClassDoc implements DslElementDoc {
    private final String className
    private final String id
    private final String simpleName
    final ClassMetaData classMetaData
    private final Element classSection
    private final ClassExtensionMetaData extensionMetaData
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
    List<Element> comment = []
    private final GenerationListener listener = new DefaultGenerationListener()

    ClassDoc(String className, Element classContent, Document targetDocument, ClassMetaData classMetaData, ClassExtensionMetaData extensionMetaData, DslDocModel model, JavadocConverter javadocConverter) {
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

    String getId() { return id }

    def getName() { return className }

    def getSimpleName() { return simpleName }

    boolean isDeprecated() {
        return classMetaData.deprecated
    }

    boolean isExperimental() {
        return classMetaData.experimental
    }

    Collection<PropertyDoc> getClassProperties() { return classProperties }

    void addClassProperty(PropertyDoc propertyDoc) {
        classProperties.add(propertyDoc)
    }

    Collection<MethodDoc> getClassMethods() { return classMethods }

    void addClassMethod(MethodDoc methodDoc) {
        classMethods.add(methodDoc)
    }

    def getClassBlocks() { return classBlocks }

    void addClassBlock(BlockDoc blockDoc) {
        classBlocks.add(blockDoc)
    }

    def getClassExtensions() { return classExtensions }

    def getClassSection() { return classSection }

    Element getPropertiesTable() { return propertiesTable }

    def getPropertiesSection() { return propertiesSection }

    def getPropertyDetailsSection() { return getSection('Property details') }

    Element getMethodsTable() { return methodsTable }

    def getMethodsSection() { return methodsSection }

    def getMethodDetailsSection() { return getSection('Method details') }

    def getBlocksTable() { return getTable('Script blocks') }

    def getBlockDetailsSection() { return getSection('Script block details') }

    ClassDoc mergeContent() {
        buildExtensions()
        classMethods.sort { it.metaData.overrideSignature }
        classBlocks.sort { it.name }
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
                def methodDoc = new MethodDoc(method, javadocConverter.parse(method, listener).docbook)
                if (!methodDoc.description) {
                    throw new RuntimeException("Docbook content for '$className $method.signature' does not contain a description paragraph.")
                }
                def property = findProperty(method.name)
                def multiValued = false
                if (method.parameters.size() == 1 && method.parameters[0].type.signature == Closure.class.name && property) {
                    def type = property.metaData.type
                    if (type.name == 'java.util.List' || type.name == 'java.util.Collection' || type.name == 'java.util.Set' || type.name == 'java.util.Iterable') {
                        type = type.typeArgs[0]
                        multiValued = true
                    }
                    classBlocks << new BlockDoc(methodDoc, property, type, multiValued)
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
        def plugins = [:]
        extensionMetaData.mixinClasses.each { MixinMetaData mixin ->
            def pluginId = mixin.pluginId
            def classExtensionDoc = plugins[pluginId]
            if (!classExtensionDoc) {
                classExtensionDoc = new ClassExtensionDoc(pluginId, classMetaData)
                plugins[pluginId] = classExtensionDoc
            }
            classExtensionDoc.mixinClasses << model.getClassDoc(mixin.mixinClass)
        }
        extensionMetaData.extensionClasses.each { ExtensionMetaData extension ->
            def pluginId = extension.pluginId
            def classExtensionDoc = plugins[pluginId]
            if (!classExtensionDoc) {
                classExtensionDoc = new ClassExtensionDoc(pluginId, classMetaData)
                plugins[pluginId] = classExtensionDoc
            }
            classExtensionDoc.extensionClasses[extension.extensionId] = model.getClassDoc(extension.extensionClass)
        }

        classExtensions.addAll(plugins.values())
        classExtensions.each { extension -> extension.buildMetaData(model) }
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

    PropertyDoc findProperty(String name) {
        return classProperties.find { it.name == name }
    }

    BlockDoc getBlock(String name) {
        def block = classBlocks.find { it.name == name }
        if (block) {
            return block
        }
        for (extensionDoc in classExtensions) {
            block = extensionDoc.extensionBlocks.find { it.name == name }
            if (block) {
                return block
            }
        }
        throw new RuntimeException("Class $className does not have a script block '$name'.")
    }
}

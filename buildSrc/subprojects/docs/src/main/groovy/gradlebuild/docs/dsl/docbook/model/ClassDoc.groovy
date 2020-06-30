/*
 * Copyright 2020 the original author or authors.
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
package gradlebuild.docs.dsl.docbook.model

import gradlebuild.docs.dsl.source.model.ClassMetaData
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node

class ClassDoc implements DslElementDoc {
    private final String className
    private final String id
    private final String simpleName
    final ClassMetaData classMetaData
    private final Element classSection
    final ClassExtensionMetaData extensionMetaData
    private final List<PropertyDoc> classProperties = []
    private final List<MethodDoc> classMethods = []
    private final List<BlockDoc> classBlocks = []
    private final List<ClassExtensionDoc> classExtensions = []
    private final Element propertiesTable
    private final Element methodsTable
    private final Element propertiesSection
    private final Element methodsSection
    ClassDoc superClass
    List<ClassDoc> interfaces = []
    List<ClassDoc> subClasses = []
    List<Element> comment = []

    ClassDoc(String className, Element classContent, Document targetDocument, ClassMetaData classMetaData, ClassExtensionMetaData extensionMetaData) {
        this.className = className
        id = className
        simpleName = className.tokenize('.').last()
        this.classMetaData = classMetaData
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

    String getName() { return className }

    String getSimpleName() { return simpleName }

    boolean isDeprecated() {
        return classMetaData.deprecated
    }

    boolean isIncubating() {
        return classMetaData.incubating
    }

    boolean isReplaced() {
        return classMetaData.replaced
    }

    @Override
    String getReplacement() {
        return classMetaData.replacement
    }

    Collection<PropertyDoc> getClassProperties() { return classProperties }

    void addClassProperty(PropertyDoc propertyDoc) {
        classProperties.add(propertyDoc.forClass(this))
    }

    Collection<MethodDoc> getClassMethods() { return classMethods }

    void addClassMethod(MethodDoc methodDoc) {
        classMethods.add(methodDoc.forClass(this))
    }

    Collection<BlockDoc> getClassBlocks() { return classBlocks }

    void addClassBlock(BlockDoc blockDoc) {
        classBlocks.add(blockDoc.forClass(this))
    }

    Collection<ClassExtensionDoc> getClassExtensions() { return classExtensions }

    void addClassExtension(ClassExtensionDoc extensionDoc) {
        classExtensions.add(extensionDoc)
    }

    List<ClassDoc> getSuperTypes() {
        return superClass == null ? interfaces : [superClass] + interfaces
    }

    Element getClassSection() { return classSection }

    Element getPropertiesTable() { return propertiesTable }

    def getPropertiesSection() { return propertiesSection }

    def getPropertyDetailsSection() { return getSection('Property details') }

    Element getMethodsTable() { return methodsTable }

    def getMethodsSection() { return methodsSection }

    def getMethodDetailsSection() { return getSection('Method details') }

    def getBlocksTable() { return getTable('Script blocks') }

    def getBlockDetailsSection() { return getSection('Script block details') }

    List<ClassDoc> getSubClasses() {
        return subClasses
    }
    void addSubClass(ClassDoc subClass) {
        subClasses.add(subClass)
    }
    ClassDoc mergeContent() {
        classProperties.sort { it.name }
        classMethods.sort { it.metaData.overrideSignature }
        classBlocks.sort { it.name }
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

    Element getDescription() {
        if (comment.isEmpty() || comment[0].tagName != 'para') {
            throw new RuntimeException("Class $className does not have a description paragraph.")
        }
        return comment[0]
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

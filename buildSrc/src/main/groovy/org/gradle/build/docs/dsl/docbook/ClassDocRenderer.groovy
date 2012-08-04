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

import org.gradle.build.docs.dsl.docbook.model.DslElementDoc
import org.gradle.build.docs.dsl.docbook.model.ClassDoc
import org.gradle.build.docs.dsl.docbook.model.ClassExtensionDoc
import org.w3c.dom.Element

class ClassDocRenderer {
    private final LinkRenderer linkRenderer
    private final GenerationListener listener = new DefaultGenerationListener()
    private final PropertyTableRenderer propertyTableRenderer = new PropertyTableRenderer()
    private final PropertiesDetailRenderer propertiesDetailRenderer
    private Element propertiesSummarySection
    private Element propertiesDetailSection
    private Element methodsSummarySection
    private Element methodsDetailSection
    private Element blocksSummarySection
    private Element blocksDetailSection

    ClassDocRenderer(LinkRenderer linkRenderer) {
        this.linkRenderer = linkRenderer
        propertiesDetailRenderer = new PropertiesDetailRenderer(linkRenderer, listener)
    }

    void mergeContent(ClassDoc classDoc, Element parent) {
        listener.start("class $classDoc.name")
        try {
            def chapter = parent.ownerDocument.createElement("chapter")
            parent.appendChild(chapter)
            mergeDescription(classDoc, chapter)
            mergeProperties(classDoc, chapter)
            mergeBlocks(classDoc, chapter)
            mergeMethods(classDoc, chapter)
            mergeExtensions(classDoc, chapter)
        } finally {
            propertiesSummarySection = null
            propertiesDetailSection = null
            methodsSummarySection = null
            methodsDetailSection = null
            blocksSummarySection = null
            blocksDetailSection = null
            listener.finish()
        }
    }

    void mergeDescription(ClassDoc classDoc, Element classContent) {
        classContent.setAttribute('id', classDoc.id)
        classContent.addFirst {
            title(classDoc.simpleName)
            segmentedlist {
                segtitle('API Documentation')
                seglistitem {
                    seg { apilink('class': classDoc.name, style: classDoc.style) }
                }
            }
            addWarnings(classDoc, 'class', delegate)
            appendChildren classDoc.comment
            appendChildren classDoc.classSection.childNodes.findAll { it instanceof Element && it.tagName != 'section' }
        }
    }

    void mergeProperties(ClassDoc classDoc, Element classContent) {
        propertiesSummarySection = classContent << {
            section {
                title('Properties')
            }
        }

        def classProperties = classDoc.classProperties
        if (classProperties.isEmpty()) {
            propertiesSummarySection << {
                para('No properties')
            }
            return
        }

        def propertiesTable = propertiesSummarySection << {
            table { }
        }

        propertiesTable.children = {
            title("Properties - $classDoc.simpleName")
        }
        propertyTableRenderer.renderTo(classProperties, propertiesTable)

        propertiesDetailSection = classContent << {
            section {
                title('Property details')
            }
        }
        classProperties.each { propDoc ->
            propertiesDetailRenderer.renderTo(propDoc, propertiesDetailSection)
        }
    }

    void mergeMethods(ClassDoc classDoc, Element classContent) {
        methodsSummarySection = classContent << {
            section { title('Methods') }
        }

        def classMethods = classDoc.classMethods
        if (classMethods.isEmpty()) {
            methodsSummarySection << {
                para('No methods')
            }
            return
        }

        methodsSummarySection << {
            table {
                title("Methods - $classDoc.simpleName")
                thead {
                    tr {
                        td('Method')
                        td('Description')
                    }
                }
                classMethods.each { method ->
                    tr {
                        td {
                            literal {
                                link(linkend: method.id) { text(method.name) }
                                text('(')
                                method.metaData.parameters.eachWithIndex { param, index ->
                                    if ( index > 0 ) {
                                        text(', ')
                                    }
                                    text(param.name)
                                }
                                text(')')
                            }
                        }
                        td { appendChild method.description }
                    }
                }
            }
        }

        methodsDetailSection = classContent << {
            section {
                title('Method details')
                classMethods.each { method ->
                    section(id: method.id, role: 'detail') {
                        title {
                            appendChild linkRenderer.link(method.metaData.returnType, listener)
                            text(' ')
                            literal(method.name)
                            text('(')
                            method.metaData.parameters.eachWithIndex {param, i ->
                                if (i > 0) {
                                    text(', ')
                                }
                                appendChild linkRenderer.link(param.type, listener)
                                text(" $param.name")
                            }
                            text(')')
                        }
                        addWarnings(method, 'method', delegate)
                        appendChildren method.comment
                    }
                }
            }
        }
    }

    void mergeBlocks(ClassDoc classDoc, Element classContent) {
        blocksSummarySection = classContent << {
            section {
                title('Script blocks')
            }
        }

        def classBlocks = classDoc.classBlocks
        if (classBlocks.isEmpty()) {
            blocksSummarySection << {
                para('No script blocks')
            }
            return
        }

        blocksSummarySection << {
            table {
                title("Script blocks - $classDoc.simpleName")
                thead {
                    tr {
                        td('Block'); td('Description')
                    }
                }
                classBlocks.each { block ->
                    tr {
                        td { link(linkend: block.id) { literal(block.name) } }
                        td { appendChild block.description }
                    }
                }
            }
        }
        blocksDetailSection = classContent << {
            section {
                title('Script block details')
                classBlocks.each { block ->
                    section(id: block.id, role: 'detail') {
                        title {
                            literal(block.name); text(' { }')
                        }
                        addWarnings(block, 'script block', delegate)
                        appendChildren block.comment
                        segmentedlist {
                            segtitle('Delegates to')
                            seglistitem {
                                seg {
                                    if (block.multiValued) {
                                        text('Each ')
                                        appendChild linkRenderer.link(block.type, listener)
                                        text(' in ')
                                        link(linkend: block.blockProperty.id) { literal(block.blockProperty.name) }
                                    } else {
                                        appendChild linkRenderer.link(block.type, listener)
                                        text(' from ')
                                        link(linkend: block.blockProperty.id) { literal(block.blockProperty.name) }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    void addWarnings(DslElementDoc elementDoc, String description, Object delegate) {
        if (elementDoc.deprecated) {
            delegate.caution {
                para{
                    text("Note: This $description is ")
                    link(linkend: 'dsl-element-types', "deprecated")
                    text(" and will be removed in the next major version of Gradle.")
                }
            }
        }
        if (elementDoc.experimental) {
            delegate.caution {
                para{
                    text("Note: This $description is ")
                    link(linkend: 'dsl-element-types', "experimental")
                    text(" and may change in a future version of Gradle.")
                }
            }
        }
    }

    void mergeExtensions(ClassDoc classDoc, Element classContent) {
        if (!classDoc.classExtensions) {
            return
        }
        mergeExtensionProperties(classDoc, propertiesSummarySection, propertiesDetailSection)
        mergeExtensionMethods(classDoc, methodsSummarySection, methodsDetailSection)
        mergeExtensionBlocks(classDoc, blocksSummarySection, blocksDetailSection)
    }

    void mergeExtensionProperties(ClassDoc classDoc, Element summaryParent, Element detailParent) {
        summaryParent << {
            classDoc.classExtensions.each { ClassExtensionDoc extension ->
                if (!extension.extensionProperties) {
                    return
                }
                def section = section {
                    title { text("Properties added by the "); literal(extension.pluginId); text(" plugin") }
                    titleabbrev { literal(extension.pluginId); text(" plugin") }
                    table {
                        title { text("Properties - "); literal(extension.pluginId); text(" plugin") }
                    }
                }
                propertyTableRenderer.renderTo(extension.extensionProperties, section.table[0])
            }
        }

        classDoc.classExtensions.each { ClassExtensionDoc extension ->
            extension.extensionProperties.each { propDoc ->
                propertiesDetailRenderer.renderTo(propDoc, detailParent)
            }
        }
    }

    void mergeExtensionMethods(ClassDoc classDoc, Element summaryParent, Element detailParent) {
        summaryParent << {
            classDoc.classExtensions.each { ClassExtensionDoc extension ->
                if (!extension.extensionMethods) {
                    return
                }
                section {
                    title { text("Methods added by the "); literal(extension.pluginId); text(" plugin") }
                    titleabbrev { literal(extension.pluginId); text(" plugin") }
                    table {
                        title { text("Methods - "); literal(extension.pluginId); text(" plugin") }
                        thead { tr { td('Method'); td('Description') } }
                        extension.extensionMethods.each { method ->
                            tr {
                                td {
                                    literal {
                                        link(linkend: method.id) { text(method.name) }
                                        text('(')
                                        method.metaData.parameters.eachWithIndex { param, index ->
                                            if ( index > 0 ) {
                                                text(', ')
                                            }
                                            text(param.name)
                                        }
                                        text(')')
                                    }
                                }
                                td { appendChild method.description }
                            }
                        }
                    }
                }
            }
        }
        detailParent << {
            classDoc.classExtensions.each { ClassExtensionDoc extension ->
                extension.extensionMethods.each { method ->
                    section(id: method.id, role: 'detail') {
                        title {
                            appendChild linkRenderer.link(method.metaData.returnType, listener)
                            text(' ')
                            literal(method.name)
                            text('(')
                            method.metaData.parameters.eachWithIndex {param, i ->
                                if (i > 0) {
                                    text(', ')
                                }
                                appendChild linkRenderer.link(param.type, listener)
                                text(" $param.name")
                            }
                            text(')')
                        }
                        appendChildren method.comment
                    }
                }
            }
        }
    }

    void mergeExtensionBlocks(ClassDoc classDoc, Element summaryParent, Element detailParent) {
        summaryParent << {
            classDoc.classExtensions.each { ClassExtensionDoc extension ->
                if (!extension.extensionBlocks) {
                    return
                }
                section {
                    title { text("Script blocks added by the "); literal(extension.pluginId); text(" plugin") }

                    titleabbrev { literal(extension.pluginId); text(" plugin") }
                    table {
                        title { text("Script blocks - "); literal(extension.pluginId); text(" plugin") }
                        thead { tr { td('Block'); td('Description') } }
                        extension.extensionBlocks.each { blockDoc ->
                            tr {
                                td { link(linkend: blockDoc.id) { literal(blockDoc.name) } }
                                td { appendChild blockDoc.description }
                            }
                        }
                    }
                }
            }
        }
        detailParent << {
            classDoc.classExtensions.each { ClassExtensionDoc extension ->
                extension.extensionBlocks.each { block ->
                    section(id: block.id, role: 'detail') {
                        title {
                            literal(block.name); text(' { }')
                        }
                        appendChildren block.comment
                        segmentedlist {
                            segtitle('Delegates to')
                            seglistitem {
                                seg {
                                    if (block.multiValued) {
                                        text('Each ')
                                        appendChild linkRenderer.link(block.type, listener)
                                        text(' in ')
                                        link(linkend: block.blockProperty.id) { literal(block.blockProperty.name) }
                                    } else {
                                        appendChild linkRenderer.link(block.type, listener)
                                        text(' from ')
                                        link(linkend: block.blockProperty.id) { literal(block.blockProperty.name) }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}



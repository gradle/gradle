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

import org.w3c.dom.Element

class ClassDocRenderer {
    private final ClassLinkRenderer linkRenderer

    ClassDocRenderer(ClassLinkRenderer linkRenderer) {
        this.linkRenderer = linkRenderer
    }

    void mergeContent(ClassDoc classDoc) {
        mergeDescription(classDoc)
        mergeProperties(classDoc)
        mergeMethods(classDoc)
        mergeBlocks(classDoc)
        mergeExtensions(classDoc)
    }

    void mergeDescription(ClassDoc classDoc) {
        def classContent = classDoc.classSection
        classContent.setAttribute('id', classDoc.id)
        classContent.addFirst {
            title(classDoc.simpleName)
            segmentedlist {
                segtitle('API Documentation')
                seglistitem {
                    seg { apilink('class': classDoc.name, style: classDoc.style) }
                }
            }
            appendChildren classDoc.comment
        }
    }

    void mergeProperties(ClassDoc classDoc) {
        def propertiesTable = classDoc.propertiesTable
        def propertiesSection = classDoc.propertiesSection
        def classProperties = classDoc.classProperties

        if (classProperties.isEmpty()) {
            propertiesSection.children = {
                title('Properties')
                para('No properties')
            }
            return
        }

        def propertyTableHeader = propertiesTable.thead[0].tr[0]
        def cells = propertyTableHeader.td.collect { it }
        cells = cells.subList(1, cells.size())

        propertiesTable.children = {
            title("Properties - $classDoc.simpleName")
            thead {
                tr { td('Property'); td('Description') }
            }
            classProperties.each { propDoc ->
                tr {
                    td { link(linkend: propDoc.id) { literal(propDoc.name) } }
                    td { appendChild(propDoc.description) }
                }
            }
        }

        classProperties.each { propDoc ->
            propertiesSection << {
                section(id: propDoc.id, role: 'detail') {
                    title {
                        appendChild linkRenderer.link(propDoc.metaData.type)
                        text(' ')
                        literal(role: 'name', propDoc.name)
                        if (!propDoc.metaData.writeable) {
                            text(' (read-only)')
                        }
                    }
                    appendChildren propDoc.comment
                    if (propDoc.additionalValues) {
                        segmentedlist {
                            cells.each { Element node -> segtitle { appendChildren(node.childNodes) } }
                            seglistitem {
                                propDoc.additionalValues.each { Element node ->
                                    seg { appendChildren(node.childNodes) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    void mergeMethods(ClassDoc classDoc) {
        def methodsSection = classDoc.methodsSection
        def methodsTable = classDoc.methodsTable
        def classMethods = classDoc.classMethods

        if (classMethods.isEmpty()) {
            methodsSection.children = {
                title('Methods')
                para('No methods')
            }
        }

        methodsTable.children = {
            title("Methods - $classDoc.simpleName")
            thead {
                tr {
                    td('Method')
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

            methodsTable.parentNode << {
                section(id: method.id, role: 'detail') {
                    title {
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
                    appendChildren method.comment
                }
            }
        }
    }

    void mergeBlocks(ClassDoc classDoc) {
        def propertiesSection = classDoc.propertiesSection
        def classBlocks = classDoc.classBlocks

        propertiesSection.addAfter {
            section {
                title('Script blocks')
                if (classBlocks.isEmpty()) {
                    para('No script blocks')
                    return
                }
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
                classBlocks.each { block ->
                    section(id: block.id, role: 'detail') {
                        title {
                            literal(role: 'name', block.name); text(' { }')
                        }
                        appendChildren block.comment
                    }
                }
            }
        }
    }

    void mergeExtensions(ClassDoc classDoc) {
        if (!classDoc.classExtensions) {
            return
        }

        classDoc.propertiesTable.addAfter {
            classDoc.classExtensions.each { ClassExtensionDoc extension ->
                section {
                    title { text("Properties added by "); literal(extension.pluginId); text(" plugin") }
                    titleabbrev { literal(extension.pluginId); text(" plugin") }
                    table {
                        title { text("Properties - "); literal(extension.pluginId); text(" plugin") }
                        thead { tr { td('Property'); td('Description') } }
                        extension.extensionClasses.each { extensionClass ->
                            extensionClass.classProperties.each { propertyDoc ->
                                tr {
                                    td { literal(propertyDoc.name) }
                                    td { appendChild propertyDoc.description }
                                }
                            }
                        }
                    }
                }
            }
        }
        classDoc.methodsTable.addAfter {
            classDoc.classExtensions.each { ClassExtensionDoc extension ->
                section {
                    title { text("Methods added by "); literal(extension.pluginId); text(" plugin") }
                    titleabbrev { literal(extension.pluginId); text(" plugin") }
                    table {
                        title { text("Methods - "); literal(extension.pluginId); text(" plugin") }
                        thead { tr { td('Method'); td('Description') } }
                        extension.extensionClasses.each { extensionClass ->
                            extensionClass.classMethods.each { methodDoc ->
                                tr {
                                    td { literal(methodDoc.name) }
                                    td { appendChild methodDoc.description }
                                }
                            }
                        }
                    }
                }
            }
        }
        classDoc.blocksTable.addAfter {
            classDoc.classExtensions.each { ClassExtensionDoc extension ->
                section {
                    title { text("Blocks added by "); literal(extension.pluginId); text(" plugin") }

                    titleabbrev { literal(extension.pluginId); text(" plugin") }
                    table {
                        title { text("Blocks - "); literal(extension.pluginId); text(" plugin") }
                        thead { tr { td('Block'); td('Description') } }
                        extension.extensionClasses.each { extensionClass ->
                            extensionClass.classBlocks.each { blockDoc ->
                                tr {
                                    td { literal(blockDoc.name) }
                                    td { appendChild blockDoc.description }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}


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

        propertiesSection.addAfter {
            section {
                title('Property details')
                classProperties.each { propDoc ->
                    section(id: propDoc.id, role: 'detail') {
                        title {
                            appendChild linkRenderer.link(propDoc.metaData.type)
                            text(' ')
                            literal(propDoc.name)
                            if (!propDoc.metaData.writeable) {
                                text(' (read-only)')
                            }
                        }
                        appendChildren propDoc.comment
                        if (propDoc.additionalValues) {
                            segmentedlist {
                                propDoc.additionalValues.each { attributeDoc ->
                                    segtitle { appendChildren(attributeDoc.title) }
                                }
                                seglistitem {
                                    propDoc.additionalValues.each { ExtraAttributeDoc attributeDoc ->
                                        seg { appendChildren(attributeDoc.value) }
                                    }
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
            return
        }

        methodsTable.children = {
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

        methodsSection.addAfter {
            section {
                title('Method details')
                classMethods.each { method ->
                    section(id: method.id, role: 'detail') {
                        title {
                            appendChild linkRenderer.link(method.metaData.returnType)
                            text(' ')
                            literal(method.name)
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
    }

    void mergeBlocks(ClassDoc classDoc) {
        def targetSection = classDoc.methodsSection
        def classBlocks = classDoc.classBlocks

        if (classBlocks.isEmpty()) {
            targetSection.addBefore {
                section {
                    title('Script blocks')
                    para('No script blocks')
                }
            }
            return
        }

        targetSection.addBefore {
            section {
                title('Script blocks')
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
            section {
                title('Script block details')
                classBlocks.each { block ->
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
                                        appendChild linkRenderer.link(block.type)
                                        text(' in ')
                                        link(linkend: block.blockProperty.id) { literal(block.blockProperty.name) }
                                    } else {
                                        appendChild linkRenderer.link(block.type)
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

    void mergeExtensions(ClassDoc classDoc) {
        if (!classDoc.classExtensions) {
            return
        }
        mergeExtensionProperties(classDoc)
        mergeExtensionMethods(classDoc)
        mergeExtensionBlocks(classDoc)
    }

    void mergeExtensionProperties(ClassDoc classDoc) {
        classDoc.propertiesTable.addAfter {
            classDoc.classExtensions.each { ClassExtensionDoc extension ->
                if (!extension.extensionProperties) {
                    return
                }
                section {
                    title { text("Properties added by the "); literal(extension.pluginId); text(" plugin") }
                    titleabbrev { literal(extension.pluginId); text(" plugin") }
                    table {
                        title { text("Properties - "); literal(extension.pluginId); text(" plugin") }
                        thead { tr { td('Property'); td('Description') } }
                        extension.extensionProperties.each { propertyDoc ->
                            tr {
                                td { link(linkend: propertyDoc.id) { literal(propertyDoc.name) } }
                                td { appendChild propertyDoc.description }
                            }
                        }
                    }
                }
            }
        }
        classDoc.propertyDetailsSection << {
            classDoc.classExtensions.each { ClassExtensionDoc extension ->
                extension.extensionProperties.each { propDoc ->
                    section(id: propDoc.id, role: 'detail') {
                        title {
                            appendChild linkRenderer.link(propDoc.metaData.type)
                            text(' ')
                            literal(propDoc.name)
                            if (!propDoc.metaData.writeable) {
                                text(' (read-only)')
                            }
                        }
                        appendChildren propDoc.comment
                        if (propDoc.additionalValues) {
                            segmentedlist {
                                propDoc.additionalValues.each { attributeDoc ->
                                    segtitle { appendChildren(attributeDoc.title) }
                                }
                                seglistitem {
                                    propDoc.additionalValues.each { ExtraAttributeDoc attributeDoc ->
                                        seg { appendChildren(attributeDoc.value) }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    void mergeExtensionMethods(ClassDoc classDoc) {
        classDoc.methodsTable.addAfter {
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
        classDoc.methodDetailsSection << {
            classDoc.classExtensions.each { ClassExtensionDoc extension ->
                extension.extensionMethods.each { method ->
                    section(id: method.id, role: 'detail') {
                        title {
                            appendChild linkRenderer.link(method.metaData.returnType)
                            text(' ')
                            literal(method.name)
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
    }

    void mergeExtensionBlocks(ClassDoc classDoc) {
        classDoc.blocksTable.addAfter {
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
        classDoc.blockDetailsSection << {
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
                                        appendChild linkRenderer.link(block.type)
                                        text(' in ')
                                        link(linkend: block.blockProperty.id) { literal(block.blockProperty.name) }
                                    } else {
                                        appendChild linkRenderer.link(block.type)
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



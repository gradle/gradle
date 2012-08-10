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

import org.gradle.build.docs.dsl.docbook.model.ClassDoc
import org.gradle.build.docs.dsl.docbook.model.ClassExtensionDoc
import org.w3c.dom.Element

class ClassDocRenderer {
    private final LinkRenderer linkRenderer
    private final GenerationListener listener = new DefaultGenerationListener()
    private final ClassDescriptionRenderer descriptionRenderer = new ClassDescriptionRenderer()
    private final BlockDetailRenderer blockDetailRenderer
    private final PropertiesRenderer propertiesRenderer
    private final MethodsRenderer methodsRenderer

    ClassDocRenderer(LinkRenderer linkRenderer) {
        this.linkRenderer = linkRenderer
        blockDetailRenderer = new BlockDetailRenderer(linkRenderer, listener)
        propertiesRenderer = new PropertiesRenderer(linkRenderer, listener)
        methodsRenderer = new MethodsRenderer(linkRenderer, listener)
    }

    void mergeContent(ClassDoc classDoc, Element parent) {
        listener.start("class $classDoc.name")
        try {
            def chapter = parent.ownerDocument.createElement("chapter")
            parent.appendChild(chapter)
            chapter.setAttribute('id', classDoc.id)
            descriptionRenderer.renderTo(classDoc, chapter)
            mergeProperties(classDoc, chapter)
            mergeBlocks(classDoc, chapter)
            mergeMethods(classDoc, chapter)
        } finally {
            listener.finish()
        }
    }

    void mergeProperties(ClassDoc classDoc, Element classContent) {
        propertiesRenderer.renderTo(classDoc, classContent)
    }

    void mergeMethods(ClassDoc classDoc, Element classContent) {
        methodsRenderer.renderTo(classDoc, classContent)
    }

    void mergeBlocks(ClassDoc classDoc, Element classContent) {
        Element blocksSummarySection = classContent << {
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
        Element blocksDetailSection = classContent << {
            section {
                title('Script block details')
            }
        }
        classBlocks.each { block ->
            blockDetailRenderer.renderTo(block, blocksDetailSection)
        }

        mergeExtensionBlocks(classDoc, blocksSummarySection, blocksDetailSection)
    }

    void mergeExtensionBlocks(ClassDoc classDoc, Element summaryParent, Element detailParent) {
        classDoc.classExtensions.each { ClassExtensionDoc extension ->
            summaryParent << {
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
            extension.extensionBlocks.each { block ->
                blockDetailRenderer.renderTo(block, detailParent)
            }
        }
    }
}



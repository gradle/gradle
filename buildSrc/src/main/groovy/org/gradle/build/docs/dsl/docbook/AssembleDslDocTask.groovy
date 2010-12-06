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

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.InputFiles
import org.w3c.dom.Document
import groovy.xml.dom.DOMCategory
import org.w3c.dom.Element
import org.gradle.api.tasks.InputDirectory
import org.gradle.build.docs.XIncludeAwareXmlProvider
import org.gradle.build.docs.BuildableDOMCategory
import org.gradle.build.docs.dsl.model.ClassMetaData
import org.gradle.build.docs.model.ClassMetaDataRepository
import org.gradle.build.docs.model.SimpleClassMetaDataRepository
import org.gradle.build.docs.dsl.LinkMetaData
import org.gradle.api.Project

/**
 * Generates the docbook source for the DSL reference guide.
 *
 * Uses the following as input:
 * <ul>
 * <li>Meta-data extracted from the source by {@link org.gradle.build.docs.dsl.ExtractDslMetaDataTask}.</li>
 * <li>Meta-data about the plugins, in the form of an XML file.</li>
 * <li>A docbook template file containing the introductory material and a list of classes to document.</li>
 * <li>A docbook template file for each class, contained in the {@code classDocbookDir} directory.</li>
 * </ul>
 *
 * Produces the following:
 * <ul>
 * <li>A docbook book XML file containing the reference.</li>
 * <li>A meta-data file containing information about where the canonical documentation for each class can be found:
 * as dsl doc, javadoc or groovydoc.</li>
 * </ul>
 */
class AssembleDslDocTask extends DefaultTask {
    @InputFile
    File sourceFile
    @InputFile
    File classMetaDataFile
    @InputFile
    File pluginsMetaDataFile
    @InputDirectory
    File classDocbookDir
    @OutputFile
    File destFile
    @OutputFile
    File linksFile
    @InputFiles
    FileCollection classpath;

    @TaskAction
    def transform() {
        XIncludeAwareXmlProvider provider = new XIncludeAwareXmlProvider(classpath)
        provider.parse(sourceFile)
        transformDocument(provider.document)
        provider.write(destFile)
    }

    private def transformDocument(Document document) {
        ClassMetaDataRepository<ClassMetaData> classRepository = new SimpleClassMetaDataRepository<ClassMetaData>()
        classRepository.load(classMetaDataFile)
        ClassMetaDataRepository<LinkMetaData> linkRepository = new SimpleClassMetaDataRepository<LinkMetaData>()
        classRepository.each {name, ClassMetaData metaData ->
            linkRepository.put(name, new LinkMetaData(metaData.groovy ? LinkMetaData.Style.Groovydoc : LinkMetaData.Style.Javadoc))
        }

        use(DOMCategory) {
            use(BuildableDOMCategory) {
                Map<String, ExtensionMetaData> extensions = loadPluginsMetaData()
                DslDocModel model = new DslDocModel(classDocbookDir, document, classpath, classRepository, extensions)
                def root = document.documentElement
                root.section[0].table.each { Element table ->
                    mergeContent(table, model, linkRepository)
                }
            }
        }

        linkRepository.store(linksFile)
    }

    def loadPluginsMetaData() {
        XIncludeAwareXmlProvider provider = new XIncludeAwareXmlProvider(classpath)
        provider.parse(pluginsMetaDataFile)
        Map<String, ExtensionMetaData> extensions = [:]
        provider.root.plugin.each { Element plugin ->
            def description = plugin.'@description'
            plugin.extends.each { Element e ->
                def targetClass = e.'@targetClass'
                def extensionClass = e.'@extensionClass'
                def extension = extensions[targetClass]
                if (!extension) {
                    extension = new ExtensionMetaData(targetClass)
                    extensions[targetClass] = extension
                }
                extension.add(description, extensionClass)
            }
        }
        return extensions
    }

    def mergeContent(Element typeTable, DslDocModel model, ClassMetaDataRepository<LinkMetaData> linkRepository) {
        typeTable['@role'] = 'dslTypes'
        def title = typeTable.title[0].text()

        if (title.matches('(?i).* types')) {
            mergeTypes(typeTable, model, linkRepository)
        } else if (title.matches('(?i).* blocks')) {
            mergeBlocks(typeTable, model)
        }
    }

    def mergeBlocks(Element blocksTable, DslDocModel model) {
        blocksTable.addFirst {
            thead {
                tr {
                    td('Block')
                    td('Description')
                }
            }
        }

        def project = model.getClassDoc(Project.class.name)

        blocksTable.tr.each { Element tr ->
            mergeBlock(tr, project)
        }
    }

    def mergeBlock(Element tr, ClassDoc project) {
        String blockName = tr.td[0].text().trim()
        BlockDoc blockDoc = project.getBlock(blockName)
        tr.children = {
            td { link(linkend: blockDoc.id) { literal("$blockName { }")} }
            td(blockDoc.description)
        }
    }

    def mergeTypes(Element typeTable, DslDocModel model, ClassMetaDataRepository<LinkMetaData> linkRepository) {
        typeTable.addFirst {
            thead {
                tr {
                    td('Type')
                    td('Description')
                }
            }
        }

        typeTable.tr.each { Element tr ->
            mergeType(tr, model, linkRepository)
        }
    }

    def mergeType(Element tr, DslDocModel model, ClassMetaDataRepository<LinkMetaData> linkRepository) {
        String className = tr.td[0].text().trim()
        ClassDoc classDoc = model.getClassDoc(className)
        linkRepository.put(className, new LinkMetaData(LinkMetaData.Style.Dsldoc))

        Element root = tr.ownerDocument.documentElement
        root << classDoc.classSection

        tr.children = {
            td {
                link(linkend: classDoc.id) { literal(classDoc.classSimpleName) }
            }
            td(classDoc.description)
        }
    }
}

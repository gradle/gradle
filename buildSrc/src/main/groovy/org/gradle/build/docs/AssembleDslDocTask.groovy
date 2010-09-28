package org.gradle.build.docs

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

class AssembleDslDocTask extends DefaultTask {
    @InputFile
    File sourceFile
    @InputDirectory
    File classDocbookDir
    @OutputFile
    File destFile
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
        use(DOMCategory) {
            use(BuildableDOMCategory) {
                DslModel model = new DslModel(classDocbookDir, document, classpath)
                def root = document.documentElement
                root.table.each { Element table ->
                    insertTypes(table, model)
                }
            }
        }
    }

    def insertTypes(Element typeTable, DslModel model) {
        Element root = typeTable.ownerDocument.documentElement
        typeTable.addFirst {
            thead {
                tr {
                    td('Type')
                    td('Description')
                }
            }
        }

        typeTable.tr.each { Element tr ->
            String className = tr.td[0].text().trim()
            ClassDoc classDoc = model.getClassDoc(className)

            root << classDoc.classSection

            tr.td[0].children = {
                link(linkend: classDoc.id, classDoc.classSimpleName)
            }

            Element classSection = root.lastChild
            classSection['@id'] = classDoc.id
            classSection.addFirst {
                title(classDoc.classSimpleName)
            }
            tr << {
                td(classDoc.description)
            }
        }
    }
}

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

class AssembleDslDocTask extends DefaultTask {
    @InputFile
    File sourceFile
    @OutputFile
    File destFile
    @InputFiles
    FileCollection classpath;

    @TaskAction
    def transform() {
        XIncludeAwareXmlProvider provider = new XIncludeAwareXmlProvider(classpath)
        provider.parse(sourceFile)
        transform(provider.document)
        provider.write(destFile)
    }

    private def transform(Document document) {
        use(DOMCategory) {
            use(BuildableDOMCategory) {
                def root = document.documentElement
                root.para[0] + {
                    table {
                        title('Tasks')
                        thead {
                            tr {
                                td('Type')
                                td('Description')
                            }
                        }
                    }
                }
                Element table = root.table[0]

                root.section.each { Element section ->
                    Element title = section.title[0]
                    String className = title.text()
                    String id = "dsl:$className"
                    section['@id'] = id
                    String baseName = className.tokenize('.').last()
                    title.text = baseName
                    table << {
                        tr {
                            td {
                                link(linkend: id, baseName)
                            }
                            td(section.section[0].para[0])
                        }
                    }

                }
            }
        }
    }
}

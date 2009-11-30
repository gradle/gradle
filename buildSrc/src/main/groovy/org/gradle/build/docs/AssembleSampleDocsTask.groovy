package org.gradle.build.docs

import groovy.xml.DOMBuilder
import groovy.xml.dom.DOMUtil
import org.gradle.api.file.FileVisitDetails
import org.gradle.api.tasks.SourceTask
import org.gradle.api.tasks.TaskAction
import org.w3c.dom.Document
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element
import groovy.xml.dom.DOMCategory
import org.gradle.api.tasks.OutputFile

class AssembleSamplesDocTask extends SourceTask {
    @OutputFile
    File destFile

    @TaskAction
    def generate() {
        List samples = []

        source.visit {FileVisitDetails fvd ->
            if (fvd.isDirectory()) {
                return
            }

            Element sourceDoc;
            fvd.file.withReader {Reader reader ->
                sourceDoc = DOMBuilder.parse(reader).documentElement
            }

            use(DOMCategory) {
                Element titleElement = sourceDoc.title[0]
                samples << [
                        dir: fvd.relativePath.parent as String,
                        title: titleElement.textContent,
                        doc: sourceDoc,
                        include: sourceDoc['*'].size() > 1
                ]
            }
        }

        samples = samples.sort { it.dir }

        Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument()

        DomBuilder builder = new DomBuilder(doc)
        builder.appendix {
            title('Gradle Samples')
            para('Listed below are some of the stand-alone samples which are included in the Gradle distribution.')
            variablelist {
                samples.each {sample ->
                    varlistentry {
                        term {
                            if (sample.include) {
                                link(linkend: sample.hashCode()) { filename(sample.dir) }
                            } else {
                                filename(sample.dir)
                            }
                        }
                        listitem {
                            para(sample.title)
                        }
                    }
                }
            }
            samples.each { sample ->
                if (!sample.include) {
                    return
                }
                section(id: sample.hashCode()) {
                    sample.doc.childNodes.each { n ->
                        builder.appendChild(n)
                    }
                }
            }
        }

        destFile.withOutputStream {OutputStream stream ->
            DOMUtil.serialize(doc.documentElement, stream)
        }
    }
}

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

/**
 * Generates a chapter containing a summary of the readme files for the samples.
 */
class AssembleSamplesDocTask extends SourceTask {
    @OutputFile
    File destFile

    @TaskAction
    def generate() {
        List samples = []
        use(DOMCategory) {

            // Collect up the source sample.xml files
            source.visit {FileVisitDetails fvd ->
                if (fvd.isDirectory()) {
                    return
                }

                Element sourceDoc;
                fvd.file.withReader {Reader reader ->
                    sourceDoc = DOMBuilder.parse(reader).documentElement
                }

                Element firstPara = sourceDoc.para[0]
                if (!firstPara) {
                    throw new RuntimeException("Source file $fvd.file does not contain any <para> elements.")
                }
                samples << [
                        dir: fvd.relativePath.parent as String,
                        firstPara: firstPara,
                        doc: sourceDoc,
                        include: sourceDoc['*'].size() > 1
                ]
            }

            samples = samples.sort { it.dir }

            Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument()

            DomBuilder builder = new DomBuilder(doc)
            builder.appendix(id: 'sample_list') {
                title('Gradle Samples')
                para {
                    text('Listed below are some of the stand-alone samples which are included in the Gradle distribution. ')
                    text('You can find these samples in the ')
                    filename {
                        replaceable('GRADLE_HOME')
                        text('/samples')
                    }
                    text(' directory of the distribution.')
                }
                table {
                    title('Samples included in the distribution')
                    thead {
                        td('Sample')
                        td('Description')
                    }
                    samples.each {sample ->
                        tr {
                            td {
                                if (sample.include) {
                                    link(linkend: sample.hashCode()) { filename(sample.dir) }
                                } else {
                                    filename(sample.dir)
                                }
                            }
                            td(sample.firstPara)
                        }
                    }
                }
                samples.each {sample ->
                    if (!sample.include) {
                        return
                    }
                    section(id: sample.hashCode()) {
                        title {
                            text('Sample ')
                            filename(sample.dir)
                        }
                        sample.doc.childNodes.each {n ->
                            appendChild n
                        }
                    }
                }
            }

            destFile.withOutputStream {OutputStream stream ->
                DOMUtil.serialize(doc.documentElement, stream)
            }
        }
    }
}

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


package org.gradle.build.docs

import groovy.xml.dom.DOMCategory
import org.gradle.api.DefaultTask
import org.gradle.api.InvalidUserDataException
import org.gradle.build.docs.dsl.links.ClassLinkMetaData
import org.gradle.build.docs.dsl.links.LinkMetaData
import org.gradle.build.docs.model.ClassMetaDataRepository
import org.gradle.build.docs.model.SimpleClassMetaDataRepository
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.gradle.api.tasks.*

/**
 * Transforms userguide source into docbook, replacing custom XML elements.
 *
 * Takes the following as input:
 * <ul>
 * <li>A source docbook XML file.</li>
 * <li>A directory containing the snippets for the samples to be included in the document, as produced by {@link ExtractSnippetsTask}.</li>
 * <li>Meta-info about the canonical documentation for each class referenced in the document, as produced by {@link org.gradle.build.docs.dsl.docbook.AssembleDslDocTask}.</li>
 * </ul>
 */
public class UserGuideTransformTask extends DefaultTask {
    @Input
    String getVersion() { return project.version.toString() }

    def javadocUrl
    def groovydocUrl
    def dsldocUrl
    def websiteUrl

    @InputFile
    File sourceFile
    @InputFile
    File linksFile
    @OutputFile
    File destFile
    @InputDirectory
    File snippetsDir
    @Input
    Set<String> tags = new HashSet()

    final SampleElementValidator validator = new SampleElementValidator();

    @Input String getJavadocUrl() {
        javadocUrl
    }

    @Input String getGroovydocUrl() {
        groovydocUrl
    }

    @Input String getDsldocUrl() {
        dsldocUrl
    }

    @Input String getWebsiteUrl() {
        websiteUrl
    }

    @TaskAction
    def transform() {
        XIncludeAwareXmlProvider provider = new XIncludeAwareXmlProvider()
        provider.parse(sourceFile)
        transform(provider.document)
        provider.write(destFile)
    }

    private def transform(Document doc) {
        use(DOMCategory) {
            use(BuildableDOMCategory) {
                addVersionInfo(doc)
                applyConditionalChunks(doc)
                transformSamples(doc)
                transformApiLinks(doc)
                transformWebsiteLinks(doc)
                fixProgramListings(doc)
            }
        }
    }

    def addVersionInfo(Document doc) {
        Element releaseInfo = doc.createElement('releaseinfo')
        releaseInfo.appendChild(doc.createTextNode(version.toString()))
        if (doc.documentElement.bookinfo[0]) {
            doc.documentElement.bookinfo[0].appendChild(releaseInfo)
        }
    }

    def fixProgramListings(Document doc) {
        doc.documentElement.depthFirst().findAll { it.name() == 'programlisting' || it.name() == 'screen' }.each {Element element ->
            element.setTextContent(normalise(element.getTextContent()))
        }
    }

    String normalise(String content) {
        return content.trim().replace('\t', '    ').replace('\r\n', '\n')
    }

    def transformApiLinks(Document doc) {
        ClassMetaDataRepository<ClassLinkMetaData> linkRepository = new SimpleClassMetaDataRepository<ClassLinkMetaData>()
        linkRepository.load(linksFile)

        doc.documentElement.depthFirst().findAll { it.name() == 'apilink' }.each {Element element ->
            String className = element.'@class'
            if (!className) {
                throw new RuntimeException('No "class" attribute specified for <apilink> element.')
            }
            String methodName = element.'@method'

            def classMetaData = linkRepository.get(className)
            LinkMetaData linkMetaData = methodName ? classMetaData.getMethod(methodName) : classMetaData.classLink
            String style = element.'@style' ?: linkMetaData.style.toString().toLowerCase()

            Element ulinkElement = doc.createElement('ulink')

            String href
            if (style == 'dsldoc') {
                href = "$dsldocUrl/${className}.html"
            } else if (style == "groovydoc" || style == "javadoc") {
                def base = style == "groovydoc" ? groovydocUrl : javadocUrl
                def packageName = classMetaData.packageName
                href = "$base/${packageName.replace('.', '/')}/${className.substring(packageName.length()+1)}.html"
            } else {
                throw new InvalidUserDataException("Unknown api link style '$style'.")
            }

            if (linkMetaData.urlFragment) {
                href = "$href#$linkMetaData.urlFragment"
            }

            ulinkElement.setAttribute('url', href)

            Element classNameElement = doc.createElement('classname')
            ulinkElement.appendChild(classNameElement)

            classNameElement.appendChild(doc.createTextNode(linkMetaData.displayName))

            element.parentNode.replaceChild(ulinkElement, element)
        }
    }

    def transformWebsiteLinks(Document doc) {
        doc.documentElement.depthFirst().findAll { it.name() == 'ulink' }.each {Element element ->
            String url = element.'@url'
            if (url.startsWith('website:')) {
                url = url.substring(8)
                if (websiteUrl) {
                    url = "${websiteUrl}/${url}"
                }
                element.setAttribute('url', url)
            }
        }
    }

    def transformSamples(Document doc) {
        XIncludeAwareXmlProvider samplesXmlProvider = new XIncludeAwareXmlProvider()
        samplesXmlProvider.emptyDoc() << {
            samples()
        }
        Element samplesXml = samplesXmlProvider.root.documentElement
        doc.documentElement.depthFirst().findAll { it.name() == 'sample' }.each { Element element ->
            validator.validate(element)
            String sampleId = element.'@id'
            String srcDir = element.'@dir'

            // This class handles the responsibility of adding the location tips to the first child of first
            // example defined in the sample.
            SampleElementLocationHandler locationHandler = new SampleElementLocationHandler(doc, element, srcDir)
            SampleLayoutHandler layoutHandler = new SampleLayoutHandler(srcDir)

            samplesXml << { sample(id: sampleId, dir: srcDir) }

            String title = element.'@title'

            Element exampleElement = doc.createElement('example')
            exampleElement.setAttribute('id', sampleId)
            Element titleElement = doc.createElement('title')
            titleElement.appendChild(doc.createTextNode(title))
            exampleElement.appendChild(titleElement);

            element.children().each {Element child ->
                if (child.name() == 'sourcefile') {
                    String file = child.'@file'

                    Element sourcefileTitle = doc.createElement("para")
                    Element commandElement = doc.createElement('filename')
                    commandElement.appendChild(doc.createTextNode(file))
                    sourcefileTitle.appendChild(commandElement)
                    exampleElement.appendChild(sourcefileTitle);

                    Element programListingElement = doc.createElement('programlisting')
                    if (file.endsWith('.gradle') || file.endsWith('.groovy') || file.endsWith('.java')) {
                        programListingElement.setAttribute('language', 'java')
                    }
                    else if (file.endsWith('.xml')) {
                        programListingElement.setAttribute('language', 'xml')
                    }
                    File srcFile
                    String snippet = child.'@snippet'
                    if (snippet) {
                        srcFile = new File(snippetsDir, "$srcDir/$file-$snippet")
                    } else {
                        srcFile = new File(snippetsDir, "$srcDir/$file")
                    }
                    programListingElement.appendChild(doc.createTextNode(normalise(srcFile.text)))
                    exampleElement.appendChild(programListingElement)
                } else if (child.name() == 'output') {
                    String args = child.'@args'
                    String outputFile = child.'@outputFile' ?: "${sampleId}.out"
                    boolean ignoreExtraLines = child.'@ignoreExtraLines' ?: false

                    samplesXml << { sample(id: sampleId, dir: srcDir, args: args, outputFile: outputFile, ignoreExtraLines: ignoreExtraLines) }

                    Element outputTitle = doc.createElement("para")
                    outputTitle.appendChild(doc.createTextNode("Output of "))
                    Element commandElement = doc.createElement('userinput')
                    commandElement.appendChild(doc.createTextNode("gradle $args"))
                    outputTitle.appendChild(commandElement)
                    exampleElement.appendChild(outputTitle)

                    Element screenElement = doc.createElement('screen')
                    File srcFile = new File(sourceFile.parentFile, "../../../src/samples/userguideOutput/${outputFile}").canonicalFile
                    screenElement.appendChild(doc.createTextNode("> gradle $args\n" + normalise(srcFile.text)))
                    exampleElement.appendChild(screenElement)
                } else if (child.name() == 'test') {
                    String args = child.'@args'
                    samplesXml << { sample(id: sampleId, dir: srcDir, args: args) }
                } else if (child.name() == 'layout') {
                    String args = child.'@after'
                    Element sampleElement = samplesXml << { sample(id: sampleId, dir: srcDir, args: args) }
                    layoutHandler.handle(child.text(), exampleElement, sampleElement)
                }

                locationHandler.processSampleLocation(exampleElement)
            }
            element.parentNode.insertBefore(exampleElement, element)
            element.parentNode.removeChild(element)
        }

        File samplesFile = new File(destFile.parentFile, 'samples.xml')
        samplesXmlProvider.write(samplesFile, true)
    }

    void applyConditionalChunks(Document doc) {
        doc.documentElement.depthFirst().findAll { it.'@condition' }.each {Element element ->
            if (!tags.contains(element.'@condition')) {
                element.parentNode.removeChild(element)
            }
        }
    }
}

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

import org.gradle.api.DefaultTask
import org.gradle.api.InvalidUserDataException
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.build.docs.dsl.links.ClassLinkMetaData
import org.gradle.build.docs.dsl.links.LinkMetaData
import org.gradle.build.docs.model.ClassMetaDataRepository
import org.gradle.build.docs.model.SimpleClassMetaDataRepository
import org.w3c.dom.Document
import org.w3c.dom.Element

/**
 * Transforms userguide source into docbook, replacing custom XML elements.
 *
 * Takes the following as input:
 * <ul>
 * <li>A source docbook XML file.</li>
 * <li>A directory containing the snippets for the samples to be included in the document, as produced by {@link ExtractSnippetsTask}.</li>
 * <li>Meta-info about the canonical documentation for each class referenced in the document, as produced by {@link org.gradle.build.docs.dsl.docbook.AssembleDslDocTask}.</li>
 * </ul>
 *
 */
@CacheableTask
class UserGuideTransformTask extends DefaultTask {

    @Input
    String getVersion() { return project.version.toString() }

    def javadocUrl
    def dsldocUrl
    def websiteUrl

    @PathSensitive(PathSensitivity.NONE)
    @InputFile
    File sourceFile

    @PathSensitive(PathSensitivity.NONE)
    @InputFile
    File linksFile

    @OutputFile
    File destFile

    @PathSensitive(PathSensitivity.RELATIVE)
    @InputDirectory
    File snippetsDir

    @PathSensitive(PathSensitivity.RELATIVE)
    @InputFiles
    @Optional
    FileCollection includes

    @Input
    Set<String> tags = new LinkedHashSet()

    final SampleElementValidator validator = new SampleElementValidator()

    @Input String getJavadocUrl() {
        javadocUrl
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
        transformImpl(provider.document)
        provider.write(destFile)
    }

    private def transformImpl(Document doc) {
        use(BuildableDOMCategory) {
            addVersionInfo(doc)
            applyConditionalChunks(doc)
            transformSamples(doc)
            transformApiLinks(doc)
            transformWebsiteLinks(doc)
            fixProgramListings(doc)
        }
    }

    def addVersionInfo(Document doc) {
        Element releaseInfo = doc.createElement('releaseinfo')
        releaseInfo.appendChild(doc.createTextNode(version.toString()))
        doc.documentElement.bookinfo[0]?.appendChild(releaseInfo)
    }

    def fixProgramListings(Document doc) {
        doc.documentElement.depthFirst().findAll { it.name() == 'programlisting' || it.name() == 'screen' }.each {Element element ->
            element.setTextContent(normalise(element.getTextContent()))
        }
    }

    static String normalise(String content) {
        content.replace('\t', '    ').stripIndent().replace('\r\n', '\n')
    }

    def transformApiLinks(Document doc) {
        ClassMetaDataRepository<ClassLinkMetaData> linkRepository = new SimpleClassMetaDataRepository<ClassLinkMetaData>()
        linkRepository.load(linksFile)

        findAll(doc, 'apilink').each { Element element ->
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
            } else if (style == "javadoc") {
                def base = javadocUrl
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
        findAll(doc, 'ulink').each { Element element ->
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
        String lastTitle
        String lastId
        Element lastExampleElement
        findAll(doc, 'sample').each { Element sampleElement ->

            validator.validate(sampleElement)

            String sampleId = sampleElement.'@id'
            String srcDir = sampleElement.'@dir'

            // This class handles the responsibility of adding the location tips to the first child of first
            // example defined in the sample.
            SampleElementLocationHandler locationHandler = new SampleElementLocationHandler(doc, sampleElement, srcDir)
            SampleLayoutHandler layoutHandler = new SampleLayoutHandler(srcDir)

            String title = sampleElement.'@title'

            Element exampleElement = lastExampleElement

            if (lastId!=sampleId || lastTitle!=title) {
                Element titleElement = doc.createElement('title')
                titleElement.appendChild(doc.createTextNode(title))
                exampleElement = doc.createElement('example')
                exampleElement.setAttribute('id', sampleId)
                exampleElement.appendChild(titleElement)
            }
            lastId = sampleId
            lastTitle = title
            lastExampleElement = exampleElement

            sampleElement.children().each { Element child ->
                if (child.name() == 'sourcefile') {
                    String file = child.'@file'

                    Element sourceFileTitle = doc.createElement("para")
                    Element commandElement = doc.createElement('filename')
                    commandElement.appendChild(doc.createTextNode(file))
                    sourceFileTitle.appendChild(commandElement)
                    exampleElement.appendChild(sourceFileTitle)

                    Element programListingElement = doc.createElement('programlisting')
                    if (file.endsWith('.gradle') || file.endsWith('.groovy') || file.endsWith('.java')) {
                        programListingElement.setAttribute('language', 'java')
                    } else if (file.endsWith('.xml')) {
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
                    boolean hidden = child.'@hidden' ?: false

                    if (!hidden) {
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
                    }
                } else if (child.name() == 'layout') {
                    layoutHandler.handle(child.text(), exampleElement)
                }

                locationHandler.processSampleLocation(exampleElement)
            }
            sampleElement.parentNode.insertBefore(exampleElement, sampleElement)
            sampleElement.parentNode.removeChild(sampleElement)
        }
    }

    void applyConditionalChunks(Document doc) {
        doc.documentElement.depthFirst().findAll { it.'@condition' }.each {Element element ->
            if (!tags.contains(element.'@condition')) {
                element.parentNode.removeChild(element)
            }
        }
    }

    static def findAll(Document doc, String byName) {
        doc.documentElement.depthFirst().findAll { it.name() == byName }
    }
}

/*
 * Copyright 2020 the original author or authors.
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


package gradlebuild.docs

import org.gradle.api.DefaultTask
import org.gradle.api.InvalidUserDataException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import gradlebuild.docs.dsl.links.ClassLinkMetaData
import gradlebuild.docs.dsl.links.LinkMetaData
import gradlebuild.docs.model.ClassMetaDataRepository
import gradlebuild.docs.model.SimpleClassMetaDataRepository
import org.w3c.dom.Document
import org.w3c.dom.Element
/**
 * Transforms userguide source into docbook, replacing custom XML elements.
 *
 * Takes the following as input:
 * <ul>
 * <li>A source docbook XML file.</li>
 * <li>Meta-info about the canonical documentation for each class referenced in the document, as produced by {@link gradlebuild.docs.dsl.docbook.AssembleDslDocTask}.</li>
 * </ul>
 *
 */
@CacheableTask
abstract class UserGuideTransformTask extends DefaultTask {

    @PathSensitive(PathSensitivity.NONE)
    @InputFile
    abstract RegularFileProperty getSourceFile();

    @PathSensitive(PathSensitivity.NONE)
    @InputFile
    abstract RegularFileProperty getLinksFile();

    @OutputFile
    abstract RegularFileProperty getDestFile();

    @Input
    abstract Property<String> getJavadocUrl();
    @Input
    abstract Property<String> getDsldocUrl();
    @Input
    abstract Property<String> getWebsiteUrl();
    @Input
    abstract Property<String> getVersion();

    @TaskAction
    def transform() {
        XIncludeAwareXmlProvider provider = new XIncludeAwareXmlProvider()
        provider.parse(sourceFile.get().asFile)
        transformImpl(provider.document)
        provider.write(destFile.get().asFile)
    }

    private def transformImpl(Document doc) {
        use(BuildableDOMCategory) {
            addVersionInfo(doc)
            transformApiLinks(doc)
            transformWebsiteLinks(doc)
            fixProgramListings(doc)
        }
    }

    def addVersionInfo(Document doc) {
        Element releaseInfo = doc.createElement('releaseinfo')
        releaseInfo.appendChild(doc.createTextNode(version.get().toString()))
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
        linkRepository.load(linksFile.get().asFile)

        findAll(doc, 'apilink').each { Element element ->
            String className = element.'@class'
            if (!className) {
                throw new RuntimeException('No "class" attribute specified for <apilink> element.')
            }
            String methodName = element.'@method'

            def classMetaData = linkRepository.get(className)
            LinkMetaData linkMetaData = methodName ? classMetaData.getMethod(methodName) : classMetaData.classLink
            String style = element.'@style' ?: linkMetaData.style.toString().toLowerCase(Locale.ROOT)

            Element ulinkElement = doc.createElement('ulink')

            String href
            if (style == 'dsldoc') {
                href = "${dsldocUrl.get()}/${className}.html"
            } else if (style == "javadoc") {
                def base = javadocUrl.get()
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
                url = "${websiteUrl.get()}/${url}"
                element.setAttribute('url', url)
            }
        }
    }

    static def findAll(Document doc, String byName) {
        doc.documentElement.depthFirst().findAll { it.name() == byName }
    }
}

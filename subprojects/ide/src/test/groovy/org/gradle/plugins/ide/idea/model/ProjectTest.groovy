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
package org.gradle.plugins.ide.idea.model
import org.gradle.api.JavaVersion
import org.gradle.internal.xml.XmlTransformer
import spock.lang.Specification

class ProjectTest extends Specification {
    final PathFactory pathFactory = new PathFactory().addPathVariable("PROJECT_DIR", new File("root"))
    final customModules = [path('file://$PROJECT_DIR$/gradle-idea-plugin.iml')]
    final customWildcards = ["?*.gradle", "?*.grails"] as Set

    Project project = new Project(new XmlTransformer(), pathFactory)

    def loadFromReader() {
        when:
        project.load(customProjectReader)

        then:
        project.modulePaths as Set == customModules as Set
        project.wildcards == customWildcards
        project.jdk == new Jdk(true, false, null, "1.4")
    }

    def calculatesModulePaths() {
        given:
        def ideaModule = Mock(IdeaModule)
        _ * ideaModule.getOutputFile() >> new File("root/other.iml")

        when:
        project.configure([ideaModule], "1.6", new IdeaLanguageLevel("JDK_1_5"), JavaVersion.VERSION_1_8, ['?*.groovy'], [], '')

        then:
        project.modulePaths as Set == [path('file://$PROJECT_DIR$/other.iml')] as Set

    }

    def customJdkAndWildcards_shouldBeMerged() {
        def ideaModule = Mock(IdeaModule)
        _ * ideaModule.getOutputFile() >> new File("root/other.iml")
        def modules = [ideaModule]

        when:
        project.load(customProjectReader)
        project.configure(modules, "1.6", new IdeaLanguageLevel("JDK_1_5"), JavaVersion.VERSION_1_8, ['?*.groovy'], [], '')

        then:
        project.modulePaths as Set == (customModules + [path('file://$PROJECT_DIR$/other.iml')]) as Set
        project.wildcards == customWildcards + ['?*.groovy'] as Set
        project.jdk == new Jdk("1.6", new IdeaLanguageLevel(JavaVersion.VERSION_1_5))
        project.bytecodeVersion == JavaVersion.VERSION_1_8
    }

    def "project libraries are overwritten with generated content"() {
        def libraries = [new ProjectLibrary(name: "newlib", classes: [path("newlib1.jar")])]

        when:
        project.load(customProjectReader)
        project.configure([], "1.6", new IdeaLanguageLevel("JDK_1_5"), JavaVersion.VERSION_1_8, [], libraries, '')

        then:
        project.projectLibraries as List == libraries
    }

    def "project vcs is set"() {
        when:
        project.load(customProjectReader)
        project.configure([], "1.6", new IdeaLanguageLevel("JDK_1_5"), JavaVersion.VERSION_1_8, [], [], 'Git')

        then:
        project.vcs == 'Git'
    }

    def bytecodeLevelNotReadfromXml() {
        when:
        project.loadDefaults()
        project.configure([], "1.6", new IdeaLanguageLevel("JDK_1_5"), JavaVersion.VERSION_1_8, ['?*.groovy'], [], '')
        def xml = toXmlReader
        def other = new Project(new XmlTransformer(), pathFactory)
        other.load(xml)

        then:
        project.bytecodeVersion == JavaVersion.VERSION_1_8
        other.bytecodeVersion == null
    }


    def loadDefaults() {
        when:
        project.loadDefaults()

        then:
        project.modulePaths.size() == 0
        project.wildcards == [] as Set
        project.jdk == new Jdk(true, true, "JDK_1_5", null)
        project.projectLibraries.empty
        project.bytecodeVersion == null
    }

    def toXml_shouldContainCustomValues() {
        when:
        project.loadDefaults()
        project.configure([], "1.6", new IdeaLanguageLevel("JDK_1_5"), JavaVersion.VERSION_1_8, ['?*.groovy'], [], '')
        def xml = toXmlReader
        def other = new Project(new XmlTransformer(), pathFactory)
        other.load(xml)

        then:
        project.wildcards == other.wildcards
        project.modulePaths == other.modulePaths
        project.jdk == other.jdk
    }

    private InputStream getToXmlReader() {
        ByteArrayOutputStream toXmlText = new ByteArrayOutputStream()
        project.store(toXmlText)
        return new ByteArrayInputStream(toXmlText.toByteArray())
    }

    private InputStream getCustomProjectReader() {
        return getClass().getResourceAsStream('customProject.xml')
    }

    private Path path(String url) {
        pathFactory.path(url)
    }
}

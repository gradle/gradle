/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.plugins.eclipse.model;


import org.gradle.api.Action
import org.gradle.listener.ListenerBroadcast
import org.gradle.util.TemporaryFolder
import org.junit.Rule
import spock.lang.Specification
import org.gradle.plugins.eclipse.EclipseProject
import org.gradle.api.internal.XmlTransformer
import org.gradle.api.artifacts.maven.XmlProvider

/**
 * @author Hans Dockter
 */
public class ProjectTest extends Specification {
    def static final CUSTOM_REFERENCED_PROJECTS = ['refProject'] as LinkedHashSet
    def static final CUSTOM_BUILD_COMMANDS = [new BuildCommand('org.eclipse.jdt.core.scalabuilder', [climate: 'cold'])]
    def static final CUSTOM_NATURES = ['org.eclipse.jdt.core.scalanature'] 
    def static final CUSTOM_LINKS = [new Link('somename', 'sometype', 'somelocation', '')] as Set

    @Rule
    public TemporaryFolder tmpDir = new TemporaryFolder();

    def initWithReader() {
        Project project = createProject(reader: customProjectReader)

        expect:
        project.name == 'test'
        project.comment == 'for testing'
        project.referencedProjects == CUSTOM_REFERENCED_PROJECTS
        project.buildCommands == CUSTOM_BUILD_COMMANDS
        project.natures == CUSTOM_NATURES
        project.links == CUSTOM_LINKS
    }

    def initWithReaderAndValues_shouldBeMerged() {
        def constructorName = 'constructorName'
        def constructorComment = 'constructorComment'
        def constructorReferencedProjects = ['constructorRefProject'] as LinkedHashSet
        def constructorBuildCommands = [new BuildCommand('constructorbuilder')] 
        def constructorNatures = ['constructorNature']
        def constructorLinks = [new Link('constructorName', 'constructorType', 'constructorLocation', '')] as Set

        Project project = createProject(name: constructorName, comment: constructorComment, referencedProjects: constructorReferencedProjects,
                natures: constructorNatures, buildCommands: constructorBuildCommands, links: constructorLinks, reader: customProjectReader)

        expect:
        project.name == constructorName
        project.comment == constructorComment
        project.referencedProjects == constructorReferencedProjects + CUSTOM_REFERENCED_PROJECTS
        project.buildCommands == CUSTOM_BUILD_COMMANDS + constructorBuildCommands
        project.natures == CUSTOM_NATURES + constructorNatures 
        project.links == constructorLinks + CUSTOM_LINKS
    }

    def initWithNullReader() {
        def constructorName = 'constructorName'
        def constructorComment = 'constructorComment'
        def constructorReferencedProjects = ['constructorRefProject'] as Set
        def constructorBuildCommands = [new BuildCommand('constructorbuilder')]
        def constructorNatures = ['constructorNature'] 
        def constructorLinks = [new Link('constructorName', 'constructorType', 'constructorLocation', '')] as Set

        Project project = createProject(name: constructorName, comment: constructorComment, referencedProjects: constructorReferencedProjects,
                natures: constructorNatures, buildCommands: constructorBuildCommands, links: constructorLinks)

        expect:
        project.xml != null
        project.name == constructorName
        project.comment == constructorComment
        project.referencedProjects == constructorReferencedProjects
        project.buildCommands == constructorBuildCommands
        project.natures == constructorNatures
        project.links == constructorLinks
    }

    def toXml() {
        when:
        Project project = createProject(reader: customProjectReader)

        then:
        File eclipseFile = tmpDir.file("eclipse.xml")
        project.toXml(eclipseFile)
        StringWriter stringWriterFileXml = new StringWriter()
        new XmlNodePrinter(new PrintWriter(stringWriterFileXml)).print(new XmlParser().parse(eclipseFile))
        StringWriter stringWriterWrittenXml = new StringWriter()
        new XmlNodePrinter(new PrintWriter(stringWriterWrittenXml)).print(new XmlParser().parse(getToXmlReader(project)))
        StringWriter stringWriterInternalXml = new StringWriter()
        new XmlNodePrinter(new PrintWriter(stringWriterInternalXml)).print(project.xml)

        stringWriterWrittenXml.toString() == stringWriterInternalXml.toString()
        stringWriterWrittenXml.toString() == stringWriterFileXml.toString()
    }

    def toXml_shouldContainCustomValues() {
        def constructorName = 'constructorName'
        def constructorComment = 'constructorComment'
        def constructorReferencedProjects = ['constructorRefProject'] as LinkedHashSet

        when:
        Project project = createProject(name: constructorName, comment: constructorComment,
                referencedProjects: constructorReferencedProjects, reader: customProjectReader)
        def projectFromXml = createProject(reader: getToXmlReader(project))

        then:
        project == projectFromXml
    }

    def beforeConfigured() {
        def constructorNatures = ['constructorNature'] 
        ListenerBroadcast beforeConfiguredActions = new ListenerBroadcast(Action)
        beforeConfiguredActions.add("execute") { Project project ->
            project.natures.clear()
        }

        when:
        Project project = createProject(natures: constructorNatures, reader: customProjectReader, beforeConfiguredActions: beforeConfiguredActions)

        then:
        createProject(reader: getToXmlReader(project)).natures == constructorNatures
    }

    def whenConfigured() {
        def constructorNature = 'constructorNature'
        def configureActionNature = 'configureNature'

        ListenerBroadcast whenConfiguredActions = new ListenerBroadcast(Action)
        whenConfiguredActions.add("execute") { Project project ->
            assert project.natures.contains(CUSTOM_NATURES[0])
            assert project.natures.contains(constructorNature)
            project.natures.add(configureActionNature)
        }

        when:
        Project project = createProject(natures: [constructorNature], reader: customProjectReader,
                whenConfiguredActions: whenConfiguredActions)

        then:
        createProject(reader: getToXmlReader(project)).natures == CUSTOM_NATURES + ([constructorNature, configureActionNature] as LinkedHashSet)
    }

    def withXml() {
        XmlTransformer withXmlActions = new XmlTransformer()
        Project project = createProject(reader: customProjectReader, withXmlActions: withXmlActions)

        when:
        def newName
        withXmlActions.addAction { XmlProvider provider ->
            def xml = provider.asNode()
            newName = xml.name.text() + 'x'
            xml.remove(xml.name)
            xml.appendNode('name', newName)
        }

        then:
        new XmlParser().parse(getToXmlReader(project)).name.text() == newName
    }

    private InputStreamReader getCustomProjectReader() {
        return new InputStreamReader(getClass().getResourceAsStream('customProject.xml'))
    }

    private Project createProject(Map customArgs) {
        ListenerBroadcast dummyBroadcast = new ListenerBroadcast(Action)
        XmlTransformer transformer = new XmlTransformer()
        Map args = [name: null, comment: null, referencedProjects: [] as Set, natures: [], buildCommands: [],
                links: [] as Set, reader: null, beforeConfiguredActions: dummyBroadcast, whenConfiguredActions: dummyBroadcast, withXmlActions: transformer] + customArgs
        EclipseProject eclipseProjectStub = Mock()
        eclipseProjectStub.getProjectName() >> args.name
        eclipseProjectStub.getComment() >> args.comment
        eclipseProjectStub.getReferencedProjects() >> args.referencedProjects
        eclipseProjectStub.getNatures() >> args.natures
        eclipseProjectStub.getBuildCommands() >> args.buildCommands
        eclipseProjectStub.getLinks() >> args.links
        eclipseProjectStub.getBeforeConfiguredActions() >> args.beforeConfiguredActions
        eclipseProjectStub.getWhenConfiguredActions() >> args.whenConfiguredActions
        eclipseProjectStub.getWithXmlActions() >> args.withXmlActions
        return new Project(eclipseProjectStub, args.reader)
    }

    private StringReader getToXmlReader(Project project) {
        StringWriter toXmlText = new StringWriter()
        project.toXml(toXmlText)
        return new StringReader(toXmlText.toString())
    }
}

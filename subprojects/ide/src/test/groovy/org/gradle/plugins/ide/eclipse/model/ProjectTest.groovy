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
package org.gradle.plugins.ide.eclipse.model;


import org.gradle.api.internal.xml.XmlTransformer
import org.gradle.util.TemporaryFolder
import org.junit.Rule
import spock.lang.Specification

/**
 * @author Hans Dockter
 */
public class ProjectTest extends Specification {
    def static final CUSTOM_REFERENCED_PROJECTS = ['refProject'] as LinkedHashSet
    def static final CUSTOM_BUILD_COMMANDS = [new BuildCommand('org.eclipse.jdt.core.scalabuilder', [climate: 'cold'])]
    def static final CUSTOM_NATURES = ['org.eclipse.jdt.core.scalanature'] 
    def static final CUSTOM_LINKED_RESOURCES = [new Link('somename', 'sometype', 'somelocation', '')] as Set

    @Rule
    public TemporaryFolder tmpDir = new TemporaryFolder();
    final Project project = new Project(new XmlTransformer())

    def loadFromReader() {
        when:
        project.load(customProjectReader)

        then:
        project.name == 'test'
        project.comment == 'for testing'
        project.referencedProjects == CUSTOM_REFERENCED_PROJECTS
        project.buildCommands == CUSTOM_BUILD_COMMANDS
        project.natures == CUSTOM_NATURES
        project.linkedResources == CUSTOM_LINKED_RESOURCES
    }

    def configureMergesValues() {
        EclipseProject eclipseProject = new EclipseProject()
        eclipseProject.name = 'constructorName'
        eclipseProject.comment = 'constructorComment'
        eclipseProject.referencedProjects = ['constructorRefProject'] as LinkedHashSet
        eclipseProject.buildCommands = [new BuildCommand('constructorbuilder')]
        eclipseProject.natures = ['constructorNature']
        eclipseProject.linkedResources = [new Link('constructorName', 'constructorType', 'constructorLocation', '')] as Set

        when:
        project.load(customProjectReader)
        project.configure(eclipseProject)

        then:
        project.name == eclipseProject.name
        project.comment == eclipseProject.comment
        project.referencedProjects == eclipseProject.referencedProjects + CUSTOM_REFERENCED_PROJECTS
        project.buildCommands == CUSTOM_BUILD_COMMANDS + eclipseProject.buildCommands
        project.natures == CUSTOM_NATURES + eclipseProject.natures
        project.linkedResources == eclipseProject.linkedResources + CUSTOM_LINKED_RESOURCES
    }

    def loadDefaults() {
        when:
        project.loadDefaults()

        then:
        project.name == ""
        project.comment == ""
        project.referencedProjects == [] as Set
        project.buildCommands == []
        project.natures == []
        project.linkedResources == [] as Set
    }

    def toXml_shouldContainCustomValues() {
        EclipseProject eclipseProject = new EclipseProject()
        eclipseProject.name = 'constructorName'
        eclipseProject.comment = 'constructorComment'
        eclipseProject.referencedProjects = ['constructorRefProject'] as LinkedHashSet

        when:
        project.load(customProjectReader)
        project.configure(eclipseProject)
        def xml = getToXmlReader()
        def other = new Project(new XmlTransformer())
        other.load(xml)

        then:
        project == other
    }

    private InputStream getCustomProjectReader() {
        return getClass().getResourceAsStream('customProject.xml')
    }

    private InputStream getToXmlReader() {
        ByteArrayOutputStream toXmlText = new ByteArrayOutputStream()
        project.store(toXmlText)
        return new ByteArrayInputStream(toXmlText.toByteArray())
    }
}

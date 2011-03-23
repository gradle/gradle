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


import org.gradle.api.internal.XmlTransformer
import org.gradle.plugins.ide.eclipse.EclipseProject
import org.gradle.util.HelperUtil
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
    def static final CUSTOM_LINKS = [new Link('somename', 'sometype', 'somelocation', '')] as Set

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
        project.links == CUSTOM_LINKS
    }

    def configureMergesValues() {
        EclipseProject task = HelperUtil.createTask(EclipseProject)
        task.projectName = 'constructorName'
        task.comment = 'constructorComment'
        task.referencedProjects = ['constructorRefProject'] as LinkedHashSet
        task.buildCommands = [new BuildCommand('constructorbuilder')]
        task.natures = ['constructorNature']
        task.links = [new Link('constructorName', 'constructorType', 'constructorLocation', '')] as Set

        when:
        project.load(customProjectReader)
        project.configure(task)

        then:
        project.name == task.projectName
        project.comment == task.comment
        project.referencedProjects == task.referencedProjects + CUSTOM_REFERENCED_PROJECTS
        project.buildCommands == CUSTOM_BUILD_COMMANDS + task.buildCommands
        project.natures == CUSTOM_NATURES + task.natures
        project.links == task.links + CUSTOM_LINKS
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
        project.links == [] as Set
    }

    def toXml_shouldContainCustomValues() {
        EclipseProject task = HelperUtil.createTask(EclipseProject)
        task.projectName = 'constructorName'
        task.comment = 'constructorComment'
        task.referencedProjects = ['constructorRefProject'] as LinkedHashSet

        when:
        project.load(customProjectReader)
        project.configure(task)
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

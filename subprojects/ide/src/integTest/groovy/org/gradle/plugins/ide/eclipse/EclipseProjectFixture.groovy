/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.plugins.ide.eclipse

import groovy.xml.XmlParser
import groovy.xml.XmlUtil
import org.custommonkey.xmlunit.XMLUnit
import org.gradle.plugins.ide.fixtures.IdeProjectFixture
import org.gradle.test.fixtures.file.TestFile

class EclipseProjectFixture extends IdeProjectFixture {
    private final Node project

    private EclipseProjectFixture(Node project) {
        this.project = project
    }

    static EclipseProjectFixture create(TestFile projectDir) {
        TestFile file = projectDir.file('.project')
        file.assertIsFile()
        new EclipseProjectFixture(new XmlParser().parse(file))

    }

    String getProjectName() {
        return this.project.'name'.text()
    }

    String getComment() {
        return this.project.'comment'.text()
    }

    Node getFilteredResourcesNode() {
        def r = this.project.filteredResources
        return r ? r[0] : null
    }

    void assertHasReferencedProjects(String... referencedProjects) {
        assert this.project.projects.project*.text() == referencedProjects as List
    }

    void assertHasResourceFilterXml(String expectedFilteredResourcesXml) {
        def actualFilteredResourcesXml = XmlUtil.serialize(this.project.filteredResources[0])
        def xmlUnitIgnoreWhitespaceOriginal = XMLUnit.getIgnoreWhitespace()
        XMLUnit.setIgnoreWhitespace(true)
        try {
            assert XMLUnit.compareXML(expectedFilteredResourcesXml, actualFilteredResourcesXml).similar()
        } finally {
            XMLUnit.setIgnoreWhitespace(xmlUnitIgnoreWhitespaceOriginal)
        }
    }

    void assertHasJavaFacetNatures() {
        assertHasNatures("org.eclipse.jdt.core.javanature",
            "org.eclipse.wst.common.project.facet.core.nature",
            "org.eclipse.wst.common.modulecore.ModuleCoreNature",
            "org.eclipse.jem.workbench.JavaEMFNature")
    }

    void assertHasNatures(String... natures) {
        assert this.project.natures.nature*.text() == natures as List
    }

    void assertHasJavaFacetBuilders() {
        assertHasBuilders("org.eclipse.jdt.core.javabuilder",
            "org.eclipse.wst.common.project.facet.core.builder",
            "org.eclipse.wst.validation.validationbuilder"
        )
    }

    void assertHasBuilders(String... builders) {
        assert this.project.buildSpec.buildCommand.name*.text() == builders as List
    }

    void assertHasLinkedResources(String... names) {
        assert this.project.linkedResources.link.name*.text() == names as List
    }

    void assertHasBuilder(String builderName, Map args) {
        assert this.project.buildSpec.buildCommand.name*.text().contains(builderName)
        args.each { key, value ->
            def argument = this.project.buildSpec.buildCommand.find { it.name.text() == builderName }.arguments.dictionary.find { it.key.text() == key }
            assert argument != null
            assert argument.value.text() == value
        }
    }

    void assertHasLinkedResource(String name, String type, String location) {
        assert null != this.project.linkedResources.link.findAll { it.name.text() == name && it.type.text() == type && it.location.text() == location }
    }
}

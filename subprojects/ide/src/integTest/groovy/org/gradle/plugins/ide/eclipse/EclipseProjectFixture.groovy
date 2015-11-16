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

import org.gradle.test.fixtures.file.TestFile

class EclipseProjectFixture {
    private final TestFile projectDir
    private Node project

    EclipseProjectFixture(TestFile projectDir) {
        this.projectDir = projectDir
    }

    private Node getProject() {
        if (project == null) {
            TestFile file = projectDir.file('.project')
            file.assertIsFile()
            project = new XmlParser().parse(file)
        }
        return project
    }

    String getProjectName() {
        getProject().'name'.text()
    }

    String getComment() {
        getProject().'comment'.text()
    }

    void assertHasReferencedProjects(String... referencedProjects) {
        assert getProject().projects.project*.text() == referencedProjects as List
    }


    void assertHasJavaFacetNatures() {
        assertHasNatures("org.eclipse.jdt.core.javanature",
            "org.eclipse.wst.common.project.facet.core.nature",
            "org.eclipse.wst.common.modulecore.ModuleCoreNature",
            "org.eclipse.jem.workbench.JavaEMFNature")
    }

    void assertHasNatures(String... natures) {
        assert getProject().natures.nature*.text() == natures as List
    }

    void assertHasJavaFacetBuilders() {
        assertHasBuilders("org.eclipse.jdt.core.javabuilder",
            "org.eclipse.wst.common.project.facet.core.builder",
            "org.eclipse.wst.validation.validationbuilder"
        )
    }

    void assertHasBuilders(String... builders) {
        assert getProject().buildSpec.buildCommand.name*.text() == builders as List
    }

    void assertHasLinkedResources(String... names) {
        assert getProject().linkedResources.link.name*.text() == names as List
    }

    void assertHasBuilder(String builderName, Map args) {
        assert getProject().buildSpec.buildCommand.name*.text().contains(builderName)
        args.each { key, value ->
            def argument = getProject().buildSpec.buildCommand.find { it.name.text() == builderName }.arguments.dictionary.find { it.key.text() == key }
            assert argument != null
            assert argument.value.text() == value
        }
    }

    void assertHasLinkedResource(String name, String type, String location) {
        assert null != getProject().linkedResources.link.findAll { it.name.text() == name && it.type.text() == type && it.location.text() == location }
    }
}

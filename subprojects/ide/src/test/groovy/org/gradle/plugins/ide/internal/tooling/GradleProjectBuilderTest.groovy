/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.plugins.ide.internal.tooling

import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.internal.artifacts.ivyservice.projectmodule.ProjectPublication
import org.gradle.api.internal.artifacts.ivyservice.projectmodule.ProjectPublicationRegistry
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.TestUtil

import org.junit.Rule
import spock.lang.Specification

class GradleProjectBuilderTest extends Specification {
    @Rule TestNameTestDirectoryProvider tmpDir
    def publicationRegistry = Stub(ProjectPublicationRegistry) {
        getPublications(":") >> [Stub(ProjectPublication) {
            getId() >> Stub(ModuleVersionIdentifier) {
                getGroup() >> "group"
                getName() >> "name"
                getVersion() >> "version"
            }
        }]
    }
    def builder = new GradleProjectBuilder(publicationRegistry)

    def "builds basics for project"() {
        def buildFile = tmpDir.file("build.gradle") << "//empty"
        def project = TestUtil.builder().withName("test").withProjectDir(tmpDir.testDirectory).build()
        project.description = 'a test project'

        when:
        def model = builder.buildAll(project)

        then:
        model.path == ':'
        model.name == 'test'
        model.description == 'a test project'
        model.buildScript.sourceFile == buildFile

        and:
        def publication = model.publications.iterator().next()
        publication.id.group == "group"
        publication.id.name == "name"
        publication.id.version == "version"
    }
}

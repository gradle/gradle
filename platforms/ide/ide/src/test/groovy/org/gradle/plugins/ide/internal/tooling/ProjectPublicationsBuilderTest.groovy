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

package org.gradle.plugins.ide.internal.tooling


import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.internal.artifacts.ivyservice.projectmodule.ProjectComponentPublication
import org.gradle.api.internal.artifacts.ivyservice.projectmodule.ProjectPublicationRegistry
import org.gradle.test.fixtures.AbstractProjectBuilderSpec
import org.gradle.tooling.model.gradle.ProjectPublications
import org.gradle.util.Path

class ProjectPublicationsBuilderTest extends AbstractProjectBuilderSpec {

    def publicationRegistry = Stub(ProjectPublicationRegistry) {
        getPublications(ProjectComponentPublication, Path.ROOT) >> [Stub(ProjectComponentPublication) {
            getCoordinates(ModuleVersionIdentifier) >> Stub(ModuleVersionIdentifier) {
                getGroup() >> "group"
                getName() >> "name"
                getVersion() >> "version"
            }
        }]
    }
    def builder = new PublicationsBuilder(publicationRegistry)

    def "builds basics for project"() {
        project.description = 'a test project'

        when:
        def model = builder.buildAll(ProjectPublications.name, project)

        then:
        def publication = model.publications.iterator().next()
        publication.id.group == "group"
        publication.id.name == "name"
        publication.id.version == "version"
    }

    def "can build model"() {
        expect:
        builder.canBuild(ProjectPublications.name)
    }
}

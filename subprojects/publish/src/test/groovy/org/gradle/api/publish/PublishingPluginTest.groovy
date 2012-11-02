/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.api.publish

import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Project
import org.gradle.api.Transformer
import org.gradle.api.artifacts.repositories.ArtifactRepository
import org.gradle.api.publish.plugins.PublishingPlugin
import org.gradle.util.HelperUtil
import spock.lang.Specification

class PublishingPluginTest extends Specification {

    Project project = HelperUtil.createRootProject()
    PublishingExtension extension

    def setup() {
        project.plugins.apply(PublishingPlugin)
        extension = project.extensions.getByType(PublishingExtension)
    }

    def "publishing extension is installed"() {
        expect:
        extension.publications != null
        extension.publications instanceof PublicationContainer

        extension.repositories != null
        extension.repositories instanceof NamedDomainObjectContainer
    }

    def "can create repo"() {
        given:
        extension.repositories.factory = new Transformer() {
            def transform(incomingName) {
                new ArtifactRepository() {
                    String name = incomingName
                }
            }
        }

        when:
        extension.repositories {
            foo
            bar {}
        }

        then:
        extension.repositories.size() == 2
        project.repositories.size() == 0 // ensure we didn't somehow create a resolution repo
    }

    def "can add publication"() {
        given:
        def publication = new Publication() {
            String getName() { "foo" }
        }

        when:
        extension.publications.add(publication)

        then:
        extension.publications.size() == 1
        extension.publications.toList().first().is(publication)
    }
}

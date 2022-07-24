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

package org.gradle.api.publish.plugins

import org.gradle.api.artifacts.dsl.RepositoryHandler
import org.gradle.api.publish.PublicationContainer
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.internal.PublicationInternal
import org.gradle.test.fixtures.AbstractProjectBuilderSpec

class PublishingPluginTest extends AbstractProjectBuilderSpec {

    PublishingExtension extension

    def setup() {
        project.pluginManager.apply(PublishingPlugin)
        extension = project.extensions.getByType(PublishingExtension)
    }

    def "publishing extension is installed"() {
        expect:
        extension.publications != null
        extension.publications instanceof PublicationContainer

        extension.repositories != null
        extension.repositories instanceof RepositoryHandler
    }

    def "can create repo"() {
        when:
        extension.repositories {
            mavenCentral()
        }

        then:
        extension.repositories.size() == 1
        project.repositories.size() == 0 // ensure we didn't somehow create a resolution repo
    }

    def "can add publication"() {
        given:
        def publication = Stub(PublicationInternal)

        when:
        extension.publications.add(publication)

        then:
        extension.publications.size() == 1
        extension.publications.toList().first().is(publication)
    }

    def "lifecycle task created"() {
        expect:
        project.tasks[PublishingPlugin.PUBLISH_LIFECYCLE_TASK_NAME] != null
    }
}

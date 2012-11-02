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

package org.gradle.api.publish.ivy.internal

import org.gradle.api.Transformer
import org.gradle.api.publish.Publication
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.ivy.IvyPublication
import org.gradle.api.publish.ivy.tasks.IvyPublish
import org.gradle.api.publish.ivy.tasks.internal.DefaultIvyPublishTaskNamer
import org.gradle.api.publish.ivy.tasks.internal.IvyPublishDynamicTaskCreator
import org.gradle.api.publish.plugins.PublishingPlugin
import org.gradle.util.HelperUtil
import spock.lang.Specification

class IvyPublishDynamicTaskCreatorTest extends Specification {

    def project = HelperUtil.createRootProject()
    def creator = new IvyPublishDynamicTaskCreator(project.tasks, new DefaultIvyPublishTaskNamer())

    PublishingExtension publishing

    def setup() {
        project.plugins.apply(PublishingPlugin)
        publishing = project.extensions.getByType(PublishingExtension)
        creator.monitor(publishing.publications, publishing.repositories)
        publishing.repositories.factory = new Transformer() {
            def transform(name) {
                project.repositories.ivy { delegate.name = name }
            }
        }
    }

    def "creates tasks"() {
        expect:
        ivyPublishTasks.size() == 0

        when:
        publishing.repositories { main }
        publishing.publications.add(publication("main"))

        then:
        ivyPublishTasks.size() == 0

        when:
        publishing.publications.add(ivyPublication("pub1"))

        then:
        ivyPublishTasks.size() == 1
        project.tasks["publishPub1ToRepo"] != null
        IvyPublish task = project.tasks.publishPub1ToRepo
        task.group == "publishing"
        task.description != null

        when:
        publishing.publications.add(ivyPublication("pub2"))

        then:
        ivyPublishTasks.size() == 2
        project.tasks["publishPub2ToRepo"] != null

        when:
        publishing.repositories { other }

        then:
        ivyPublishTasks.size() == 4
        project.tasks["publishPub1ToOtherRepo"] != null
        project.tasks["publishPub2ToOtherRepo"] != null
    }

    def getIvyPublishTasks() {
        project.tasks.withType(IvyPublish)
    }

    Publication publication(String name) {
        Mock(Publication) {
            getName() >> name
        }
    }

    IvyPublication ivyPublication(String name) {
        Mock(IvyPublicationInternal) {
            getName() >> name
        }
    }

}

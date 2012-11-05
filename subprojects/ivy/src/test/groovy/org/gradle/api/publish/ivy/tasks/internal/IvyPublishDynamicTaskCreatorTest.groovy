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

package org.gradle.api.publish.ivy.tasks.internal

import org.gradle.api.publish.Publication
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.ivy.IvyPublication
import org.gradle.api.publish.ivy.internal.IvyPublicationInternal
import org.gradle.api.publish.ivy.tasks.IvyPublish
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
    }

    def "creates tasks"() {
        expect:
        ivyPublishTasks.size() == 0

        when:
        publishing.repositories.ivy { }
        publishing.publications.add(publication("foo"))

        then:
        ivyPublishTasks.size() == 0

        when:
        publishing.publications.add(ivyPublication("ivy"))

        then:
        ivyPublishTasks.size() == 1
        project.tasks["publishIvyToIvyRepo"] != null
        IvyPublish task = project.tasks.publishIvyToIvyRepo
        task.group == "publishing"
        task.description != null

        when:
        publishing.publications.add(ivyPublication("ivy2"))

        then:
        ivyPublishTasks.size() == 2
        project.tasks["publishIvy2ToIvyRepo"] != null

        when:
        publishing.repositories.ivy {}

        then:
        ivyPublishTasks.size() == 4
        project.tasks["publishIvyToIvy2Repo"] != null
        project.tasks["publishIvy2ToIvy2Repo"] != null
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

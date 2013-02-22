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

package org.gradle.api.publish.ivy.internal.plugins
import org.gradle.api.Task
import org.gradle.api.publish.Publication
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.ivy.IvyPublication
import org.gradle.api.publish.ivy.internal.publication.IvyPublicationInternal
import org.gradle.api.publish.ivy.tasks.PublishToIvyRepository
import org.gradle.api.publish.plugins.PublishingPlugin
import org.gradle.util.HelperUtil
import spock.lang.Specification

class IvyPublishDynamicTaskCreatorTest extends Specification {

    def project = HelperUtil.createRootProject()
    def lifecycleTask = project.task("pl")
    def creator = new IvyPublishDynamicTaskCreator(project.tasks, lifecycleTask)

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
        lifecycleTaskDependencies.empty

        when:
        publishing.publications.add(ivyPublication("ivy"))

        then:
        ivyPublishTasks.size() == 1
        project.tasks["publishIvyPublicationToIvyRepository"] != null
        PublishToIvyRepository task = project.tasks.publishIvyPublicationToIvyRepository
        task.group == "publishing"
        task.description != null

        lifecycleTaskDependencies == [task] as Set

        when:
        publishing.publications.add(ivyPublication("ivy2"))

        then:
        ivyPublishTasks.size() == 2
        project.tasks["publishIvy2PublicationToIvyRepository"] != null
        lifecycleTaskDependencies.size() == 2

        when:
        publishing.repositories.ivy {}

        then:
        lifecycleTaskDependencies.size() == 4
        ivyPublishTasks.size() == 4
        project.tasks["publishIvyPublicationToIvy2Repository"] != null
        project.tasks["publishIvy2PublicationToIvy2Repository"] != null
    }

    protected Set<? extends Task> getLifecycleTaskDependencies() {
        lifecycleTask.taskDependencies.getDependencies(lifecycleTask)
    }

    def getIvyPublishTasks() {
        project.tasks.withType(PublishToIvyRepository)
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

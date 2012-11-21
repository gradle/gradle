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

package org.gradle.api.publish.ivy.tasks

import org.gradle.api.InvalidUserDataException
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.repositories.IvyArtifactRepository
import org.gradle.api.publish.ivy.IvyPublication
import org.gradle.api.publish.ivy.internal.IvyNormalizedPublication
import org.gradle.api.publish.ivy.internal.IvyPublicationInternal
import org.gradle.api.tasks.TaskDependency
import org.gradle.util.HelperUtil
import spock.lang.Specification

class PublishToIvyRepositoryTest extends Specification {

    Project project
    PublishToIvyRepository publish

    def normalizedPublication = Mock(IvyNormalizedPublication)

    def publication = Mock(IvyPublicationInternal) {
        asNormalisedPublication() >> normalizedPublication
    }

    def repository = Mock(IvyArtifactRepository) {}

    def setup() {
        project = HelperUtil.createRootProject()
        publish = createPublish("publish")
    }

    def "publication must implement the internal interface"() {
        when:
        publish.publication = [:] as IvyPublication

        then:
        thrown(InvalidUserDataException)

        when:
        publish.publication = [:] as IvyPublicationInternal

        then:
        notThrown(Exception)
    }

    def "the dependencies of the publication are dependencies of the task"() {
        given:
        Task otherTask = project.task("other")
        def publishableFiles = project.files("a", "b", "c")

        publication.getPublishableFiles() >> publishableFiles
        publication.getBuildDependencies() >> {
            new TaskDependency() {
                Set<? extends Task> getDependencies(Task task) {
                    [otherTask] as Set
                }
            }
        }

        when:
        publish.publication = publication

        then:
        publish.inputs.files.files == publishableFiles.files
        publish.taskDependencies.getDependencies(publish) == [otherTask] as Set
    }

    PublishToIvyRepository createPublish(String name) {
        project.tasks.add(name, PublishToIvyRepository)
    }

}

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
import org.gradle.api.internal.artifacts.repositories.IvyArtifactRepositoryInternal
import org.gradle.api.publish.ivy.IvyPublication
import org.gradle.api.publish.ivy.internal.IvyNormalizedPublication
import org.gradle.api.publish.ivy.internal.IvyPublicationInternal
import org.gradle.api.publish.ivy.internal.IvyPublisher
import org.gradle.api.tasks.TaskDependency
import org.gradle.api.tasks.TaskExecutionException
import org.gradle.util.HelperUtil
import spock.lang.Specification

class IvyPublishTest extends Specification {

    Project project
    IvyPublish publish

    def normalizedPublication = Mock(IvyNormalizedPublication)

    def publication = Mock(IvyPublicationInternal) {
        asNormalisedPublication() >> normalizedPublication
    }

    def publisher = Mock(IvyPublisher) {}

    def repository = Mock(IvyArtifactRepositoryInternal) {
        createPublisher() >> publisher
    }

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

    def "repository must implement the internal interface"() {
        when:
        publish.to = [:] as IvyArtifactRepository

        then:
        thrown(InvalidUserDataException)

        when:
        publish.to = [:] as IvyArtifactRepositoryInternal

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

    def "repository and publication are required"() {
        given:
        repository = Mock(IvyArtifactRepositoryInternal) {
            1 * createPublisher() >> publisher
        }

        when:
        publish.execute()

        then:
        def e = thrown(TaskExecutionException)
        e.cause instanceof InvalidUserDataException
        e.cause.message == "The 'publication' property is required"

        when:
        publish = createPublish("publish2")
        publish.publication = publication
        publish.execute()

        then:
        e = thrown(TaskExecutionException)
        e.cause instanceof InvalidUserDataException
        e.cause.message == "The 'repository' property is required"

        when:
        publish = createPublish("publish3")
        publish.publication = publication
        publish.to = repository
        publish.execute()

        then:
        true
    }

    IvyPublish createPublish(String name) {
        project.tasks.add(name, IvyPublish)
    }

}

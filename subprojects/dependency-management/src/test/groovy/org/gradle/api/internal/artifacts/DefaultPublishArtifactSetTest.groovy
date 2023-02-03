/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.api.internal.artifacts

import org.gradle.api.Task
import org.gradle.api.artifacts.PublishArtifact
import org.gradle.api.internal.CollectionCallbackActionDecorator
import org.gradle.api.internal.DefaultDomainObjectSet
import org.gradle.api.internal.file.TestFiles
import org.gradle.api.tasks.TaskDependency
import spock.lang.Specification

import static org.gradle.api.internal.file.TestFiles.fileCollectionFactory

class DefaultPublishArtifactSetTest extends Specification {
    final store = new DefaultDomainObjectSet<PublishArtifact>(PublishArtifact, CollectionCallbackActionDecorator.NOOP)
    final set = new DefaultPublishArtifactSet('artifacts', store, fileCollectionFactory(), TestFiles.taskDependencyFactory())

    def "set is built by the union of the tasks that build the publish artifacts"() {
        PublishArtifact artifact1 = Mock()
        PublishArtifact artifact2 = Mock()
        Task task1 = Mock()
        Task task2 = Mock()
        Task task3 = Mock()

        given:
        store.add(artifact1)
        store.add(artifact2)
        builtBy(artifact1, task1, task2)
        builtBy(artifact2, task1, task3)

        expect:
        set.buildDependencies.getDependencies(null) == [task1, task2, task3] as Set
    }

    def "file collection contains the files from each publish artifact"() {
        PublishArtifact artifact1 = Mock()
        PublishArtifact artifact2 = Mock()
        File file1 = Mock()
        File file2 = Mock()

        given:
        store.add(artifact1)
        store.add(artifact2)
        _ * artifact1.file >> file1
        _ * artifact2.file >> file2

        expect:
        set.files.files == [file1, file2] as Set
    }

    def "files are built by the union of the tasks that build the publish artifacts"() {
        PublishArtifact artifact1 = Mock()
        PublishArtifact artifact2 = Mock()
        Task task1 = Mock()
        Task task2 = Mock()
        Task task3 = Mock()

        given:
        store.add(artifact1)
        store.add(artifact2)
        builtBy(artifact1, task1, task2)
        builtBy(artifact2, task1, task3)

        expect:
        set.files.buildDependencies.getDependencies(null) == [task1, task2, task3] as Set
    }

    def builtBy(PublishArtifact artifact, Task... tasks) {
        TaskDependency taskDependency = Mock()
        _ * artifact.buildDependencies >> taskDependency
        _ * taskDependency.getDependencies(_) >> (tasks as Set)
    }
}

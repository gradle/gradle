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

package org.gradle.api.plugins.buildcomparison.outcome.internal.tooling

import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.PublishArtifact
import org.gradle.api.internal.artifacts.publish.ArchivePublishArtifact
import org.gradle.api.plugins.buildcomparison.outcome.internal.FileOutcomeIdentifier
import org.gradle.api.tasks.TaskDependency
import org.gradle.api.tasks.bundling.AbstractArchiveTask
import org.gradle.api.tasks.bundling.Tar
import org.gradle.api.tasks.bundling.War
import org.gradle.api.tasks.bundling.Zip
import org.gradle.jvm.tasks.Jar
import org.gradle.plugins.ear.Ear
import org.gradle.tooling.model.internal.outcomes.GradleFileBuildOutcome
import org.gradle.util.TestUtil
import spock.lang.Specification
import spock.lang.Unroll

import static org.gradle.api.plugins.buildcomparison.outcome.internal.FileOutcomeIdentifier.*

class PublishArtifactToFileBuildOutcomeTransformerTest extends Specification {

    def transformer = new PublishArtifactToFileBuildOutcomeTransformer()

    Project project = TestUtil.createRootProject()

    @Unroll
    "can create outcome for #taskClass archive artifact"(Class<? extends AbstractArchiveTask> taskClass, FileOutcomeIdentifier typeIdentifier) {
        given:
        AbstractArchiveTask task = Mock(taskClass)
        PublishArtifact artifact = new ArchivePublishArtifact(task)

        and:
        _ * task.getArchivePath() >> project.file("file")

        when:
        GradleFileBuildOutcome outcome = transformer.transform(artifact, project)

        then:
        outcome.typeIdentifier == typeIdentifier.typeIdentifier
        outcome.id == "file"

        where:
        taskClass           | typeIdentifier
        Zip                 | ZIP_ARTIFACT
        Jar                 | JAR_ARTIFACT
        Ear                 | EAR_ARTIFACT
        Tar                 | TAR_ARTIFACT
        War                 | WAR_ARTIFACT
        AbstractArchiveTask | ARCHIVE_ARTIFACT
    }

    def "can handle generic publish artifact"() {
        given:
        def task = Mock(Task)
        def taskDependency = Mock(TaskDependency)
        def artifact = Mock(PublishArtifact)

        1 * taskDependency.getDependencies(null) >>> [[task] as Set]
        1 * task.getPath() >> "path"
        _ * artifact.getFile() >> project.file("file")
        1 * artifact.getBuildDependencies() >> taskDependency

        when:
        def outcome = transformer.transform(artifact, project)

        then:
        outcome.typeIdentifier == UNKNOWN_ARTIFACT.typeIdentifier
        outcome.taskPath == "path"
        outcome.id == "file"
    }


}

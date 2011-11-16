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
package org.gradle.api.publication.maven.internal.ant

import org.apache.tools.ant.Project
import org.gradle.api.artifacts.repositories.MavenArtifactRepository
import org.gradle.api.internal.file.TemporaryFileProvider
import org.gradle.api.publication.maven.MavenPublication
import org.gradle.api.publication.maven.MavenPublisher
import org.apache.maven.artifact.ant.*

class DefaultMavenPublisher implements MavenPublisher {
    private final File localRepoDir
    private final TemporaryFileProvider temporaryFileProvider

    DefaultMavenPublisher(TemporaryFileProvider temporaryFileProvider) {
        this(null, temporaryFileProvider)
    }

    DefaultMavenPublisher(File localRepoDir, TemporaryFileProvider temporaryFileProvider) {
        this.localRepoDir = localRepoDir
        this.temporaryFileProvider = temporaryFileProvider
    }

    void install(MavenPublication publication) {
        def task = new InstallTask()
        if (localRepoDir) {
            def repository = new LocalRepository()
            repository.path = localRepoDir
            task.addLocalRepository(repository)
        }
        execute(publication, task)
    }

    void deploy(MavenPublication publication, MavenArtifactRepository repository) {
        def task = new DeployTask()
        task.addRemoteRepository(new RemoteRepository())
        task.remoteRepository.url = repository.url
        execute(publication, task)
    }

    private def execute(MavenPublication publication, InstallDeployTaskSupport task) {
        Project project = new Project()
        task.setProject(project)

        File pomFile = temporaryFileProvider.newTemporaryFile("${publication.artifactId}.pom")
        pomFile.text = """
<project xmlns="http://maven.apache.org/POM/4.0.0">
  <modelVersion>4.0.0</modelVersion>
  <groupId>$publication.groupId</groupId>
  <artifactId>$publication.artifactId</artifactId>
  <packaging>jar</packaging>
  <version>$publication.version</version>
</project>
"""

        Pom pom = new Pom();
        pom.project = task.project;
        pom.file = pomFile
        task.addPom(pom);

        if (publication.mainArtifact.classifier) {
            AttachedArtifact mainArtifact = task.createAttach()
            mainArtifact.classifier = publication.mainArtifact.classifier
            mainArtifact.file = publication.mainArtifact.file
            mainArtifact.type = publication.mainArtifact.extension
        } else {
            task.file = publication.mainArtifact.file
        }

        publication.subArtifacts.each { artifact ->
            AttachedArtifact attachedArtifact = task.createAttach()
            attachedArtifact.classifier = artifact.classifier
            attachedArtifact.file = artifact.file
            attachedArtifact.type = artifact.extension
        }

        task.execute()
    }
}

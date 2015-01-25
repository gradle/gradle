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

package org.gradle.api.publish.maven.internal.publisher;

import org.apache.maven.artifact.ant.RemoteRepository;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.repository.DefaultArtifactRepository;
import org.apache.maven.artifact.repository.layout.ArtifactRepositoryLayout;
import org.gradle.api.artifacts.repositories.MavenArtifactRepository;
import org.gradle.api.publication.maven.internal.ant.MavenDeployTask;
import org.gradle.internal.Factory;
import org.gradle.logging.LoggingManagerInternal;

import java.io.File;

public class AntTaskBackedMavenPublisher extends AbstractAntTaskBackedMavenPublisher<MavenDeployTask> {
    public AntTaskBackedMavenPublisher(Factory<LoggingManagerInternal> loggingManagerFactory, Factory<File> temporaryDirFactory) {
        super(loggingManagerFactory, temporaryDirFactory);
    }

    protected void postConfigure(MavenDeployTask task, MavenArtifactRepository artifactRepository) {
        addRepository(task, artifactRepository);
    }

    protected MavenDeployTask createDeployTask(File pomFile) {
        MavenDeployTask deployTask = new MavenPublishTask(pomFile, temporaryDirFactory);
        deployTask.setUniqueVersion(true);
        return deployTask;
    }

    private void addRepository(MavenDeployTask deployTask, MavenArtifactRepository artifactRepository) {
        RemoteRepository mavenRepository = new MavenRemoteRepositoryFactory(artifactRepository).create();
        deployTask.setRepositories(mavenRepository, null);
    }

    private static class MavenPublishTask extends MavenDeployTask {
        private final Factory<File> tmpDirFactory;

        public MavenPublishTask(File pomFile, Factory<File> tmpDirFactory) {
            super(pomFile);
            this.tmpDirFactory = tmpDirFactory;
        }

        @Override
        protected ArtifactRepository createLocalArtifactRepository() {
            ArtifactRepositoryLayout repositoryLayout = (ArtifactRepositoryLayout) lookup(ArtifactRepositoryLayout.ROLE, getLocalRepository().getLayout());
            return new DefaultArtifactRepository("local", tmpDirFactory.create().toURI().toString(), repositoryLayout);
        }
    }
}

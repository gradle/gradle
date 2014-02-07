/*
 * Copyright 2013 the original author or authors.
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
import org.gradle.api.publication.maven.internal.ant.CustomInstallTask;
import org.gradle.internal.Factory;
import org.gradle.logging.LoggingManagerInternal;

import java.io.File;

public class AntTaskBackedMavenLocalPublisher extends AbstractAntTaskBackedMavenPublisher<AntTaskBackedMavenLocalPublisher.MavenLocalInstallTask> {
    public AntTaskBackedMavenLocalPublisher(Factory<LoggingManagerInternal> loggingManagerFactory, Factory<File> temporaryDirFactory) {
        super(loggingManagerFactory, temporaryDirFactory);
    }

    @Override
    protected void postConfigure(MavenLocalInstallTask task, MavenArtifactRepository artifactRepository) {
        task.setRepoLocation(artifactRepository.getUrl().toString());
    }

    @Override
    protected MavenLocalInstallTask createDeployTask() {
        return new MavenLocalInstallTask();
    }

    public static class MavenLocalInstallTask extends CustomInstallTask {

        private String repoLocation;

        private void setRepoLocation(String repoLocation) {
            this.repoLocation = repoLocation;
        }

        @Override
        protected ArtifactRepository createLocalArtifactRepository() {
            ArtifactRepositoryLayout repositoryLayout = (ArtifactRepositoryLayout) lookup(ArtifactRepositoryLayout.ROLE, getLocalRepository().getLayout());
            return new DefaultArtifactRepository("local", repoLocation, repositoryLayout);
        }

        @Override
        protected void updateRepositoryWithSettings(RemoteRepository repository) {
            // Do nothing
        }
    }
}

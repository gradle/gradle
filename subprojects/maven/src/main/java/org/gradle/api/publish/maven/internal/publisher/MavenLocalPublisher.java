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

import org.gradle.api.artifacts.repositories.MavenArtifactRepository;
import org.gradle.api.internal.artifacts.mvnsettings.LocalMavenRepositoryLocator;
import org.gradle.api.publication.maven.internal.action.MavenInstallAction;
import org.gradle.internal.Factory;
import org.gradle.internal.logging.LoggingManagerInternal;

import java.io.File;

public class MavenLocalPublisher extends AbstractMavenPublisher {
    public MavenLocalPublisher(Factory<LoggingManagerInternal> loggingManagerFactory, LocalMavenRepositoryLocator mavenRepositoryLocator) {
        super(loggingManagerFactory, mavenRepositoryLocator);
    }

    @Override
    protected MavenInstallAction createDeployTask(File pomFile, LocalMavenRepositoryLocator mavenRepositoryLocator, MavenArtifactRepository artifactRepository) {
        MavenInstallAction mavenInstallTask = new MavenInstallAction(pomFile);
        mavenInstallTask.setLocalMavenRepositoryLocation(mavenRepositoryLocator.getLocalMavenRepository());
        return mavenInstallTask;
    }
}

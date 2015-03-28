/*
 * Copyright 2007-2008 the original author or authors.
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
package org.gradle.api.publication.maven.internal.action;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.installer.ArtifactInstallationException;
import org.apache.maven.artifact.installer.ArtifactInstaller;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.gradle.api.GradleException;

import java.io.File;

public class MavenInstallAction extends AbstractMavenPublishAction {
    public MavenInstallAction(File pomFile) {
        super(pomFile);
    }

    @Override
    protected void publishArtifact(Artifact artifact, File artifactFile, ArtifactRepository localRepo) {
        ArtifactInstaller installer = lookup(ArtifactInstaller.class);
        try {
            installer.install(artifactFile, artifact, localRepo);
        } catch (ArtifactInstallationException e) {
            throw new GradleException("Error installing artifact '" + artifact.getDependencyConflictId() + "': " + e.getMessage(), e);
        }
    }
}

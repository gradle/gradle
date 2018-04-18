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

import org.gradle.api.publish.maven.internal.publisher.MavenProjectIdentity;
import org.sonatype.aether.RepositorySystem;
import org.sonatype.aether.RepositorySystemSession;
import org.sonatype.aether.artifact.Artifact;
import org.sonatype.aether.installation.InstallRequest;
import org.sonatype.aether.installation.InstallationException;

import java.util.Collection;

public class MavenInstallAction extends AbstractMavenPublishAction {

    public MavenInstallAction(String packaging, MavenProjectIdentity projectIdentity) {
        super(packaging, projectIdentity, null);
    }

    @Override
    protected void publishArtifacts(Collection<Artifact> artifacts, RepositorySystem repositorySystem, RepositorySystemSession session) throws InstallationException {
        InstallRequest request = new InstallRequest();
        for (Artifact artifact : artifacts) {
            request.addArtifact(artifact);
        }
        repositorySystem.install(session, request);
    }
}

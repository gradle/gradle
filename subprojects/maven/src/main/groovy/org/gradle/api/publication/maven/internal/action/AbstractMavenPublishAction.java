/*
 * Copyright 2015 the original author or authors.
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

import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.apache.maven.repository.internal.MavenRepositorySystemSession;
import org.codehaus.plexus.DefaultPlexusContainer;
import org.codehaus.plexus.PlexusContainer;
import org.codehaus.plexus.PlexusContainerException;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;
import org.gradle.api.GradleException;
import org.gradle.internal.UncheckedException;
import org.sonatype.aether.RepositoryException;
import org.sonatype.aether.RepositorySystem;
import org.sonatype.aether.RepositorySystemSession;
import org.sonatype.aether.artifact.Artifact;
import org.sonatype.aether.artifact.ArtifactType;
import org.sonatype.aether.impl.internal.SimpleLocalRepositoryManager;
import org.sonatype.aether.util.DefaultRepositorySystemSession;
import org.sonatype.aether.util.artifact.DefaultArtifact;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

abstract class AbstractMavenPublishAction implements MavenPublishAction {
    private final PlexusContainer container;
    private final DefaultRepositorySystemSession session;

    private final List<Artifact> attached = new ArrayList<Artifact>();
    private final Artifact pomArtifact;
    private Artifact mainArtifact;

    protected AbstractMavenPublishAction(File pomFile) {
        container = newPlexusContainer();
        session = new MavenRepositorySystemSession();
        session.setTransferListener(new LoggingMavenTransferListener());
        session.getConfigProperties().put("maven.metadata.legacy", "true");

        Model pom = parsePom(pomFile);
        pomArtifact = new DefaultArtifact(pom.getGroupId(), pom.getArtifactId(), "pom", pom.getVersion()).setFile(pomFile);
        mainArtifact = createTypedArtifact(pom.getPackaging(), null);
    }

    public void setLocalMavenRepositoryLocation(File localMavenRepository) {
        session.setLocalRepositoryManager(new SimpleLocalRepositoryManager(localMavenRepository));
    }

    public void setMainArtifact(File file) {
        mainArtifact = mainArtifact.setFile(file);
    }

    @Override
    public void addAdditionalArtifact(File file, String type, String classifier) {
        attached.add(createTypedArtifact(type, classifier).setFile(file));
    }

    public void publish() {
        List<Artifact> artifacts = new ArrayList<Artifact>();
        if (mainArtifact.getFile() != null) {
            artifacts.add(mainArtifact);
        }
        artifacts.add(pomArtifact);
        artifacts.addAll(attached);

        try {
            publishArtifacts(artifacts, newRepositorySystem(), session);
        } catch (RepositoryException e) {
            throw new GradleException(e.getMessage(), e);
        }
    }

    protected abstract void publishArtifacts(Collection<Artifact> artifact, RepositorySystem repositorySystem, RepositorySystemSession session) throws RepositoryException;

    protected PlexusContainer getContainer() {
        return container;
    }

    private PlexusContainer newPlexusContainer() {
        try {
            return new DefaultPlexusContainer();
        } catch (PlexusContainerException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }

    private RepositorySystem newRepositorySystem() {
        try {
            return container.lookup(RepositorySystem.class);
        } catch (ComponentLookupException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }

    private Model parsePom(File pomFile) {
        FileReader reader = null;
        try {
            reader = new FileReader(pomFile);
            return new MavenXpp3Reader().read(reader, false);
        } catch (Exception e) {
            throw UncheckedException.throwAsUncheckedException(e);
        } finally {
            try {
                reader.close();
            } catch (IOException e) {
                throw UncheckedException.throwAsUncheckedException(e);
            }
        }
    }

    private Artifact createTypedArtifact(String type, String classifier) {
        String extension = type;
        ArtifactType stereotype = session.getArtifactTypeRegistry().get(type);
        if (stereotype != null) {
            extension = stereotype.getExtension();
            if (classifier == null) {
                classifier = stereotype.getClassifier();
            }
        }
        return new DefaultArtifact(pomArtifact.getGroupId(), pomArtifact.getArtifactId(), classifier, extension, pomArtifact.getVersion());
    }
}

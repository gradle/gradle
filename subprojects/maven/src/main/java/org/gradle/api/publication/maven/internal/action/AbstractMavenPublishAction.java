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

import org.apache.maven.repository.internal.MavenRepositorySystemSession;
import org.apache.maven.repository.internal.SnapshotMetadataGeneratorFactory;
import org.apache.maven.repository.internal.VersionsMetadataGeneratorFactory;
import org.codehaus.plexus.DefaultContainerConfiguration;
import org.codehaus.plexus.DefaultPlexusContainer;
import org.codehaus.plexus.PlexusContainer;
import org.codehaus.plexus.PlexusContainerException;
import org.codehaus.plexus.classworlds.ClassWorld;
import org.codehaus.plexus.classworlds.realm.ClassRealm;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;
import org.gradle.api.GradleException;
import org.gradle.api.publish.maven.internal.publisher.MavenProjectIdentity;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.operations.BuildOperationRef;
import org.gradle.internal.operations.CurrentBuildOperationRef;
import org.gradle.internal.resource.UriTextResource;
import org.sonatype.aether.ConfigurationProperties;
import org.sonatype.aether.RepositoryException;
import org.sonatype.aether.RepositorySystem;
import org.sonatype.aether.RepositorySystemSession;
import org.sonatype.aether.artifact.Artifact;
import org.sonatype.aether.artifact.ArtifactType;
import org.sonatype.aether.impl.Deployer;
import org.sonatype.aether.impl.internal.DefaultDeployer;
import org.sonatype.aether.impl.internal.SimpleLocalRepositoryManager;
import org.sonatype.aether.util.DefaultRepositorySystemSession;
import org.sonatype.aether.util.artifact.DefaultArtifact;

import java.io.File;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

abstract class AbstractMavenPublishAction implements MavenPublishAction {
    private final PlexusContainer container;
    private final DefaultRepositorySystemSession session;
    private final MavenProjectIdentity projectIdentity;

    private final List<Artifact> attached = new ArrayList<Artifact>();
    private Artifact pomArtifact;
    private Artifact mainArtifact;

    protected AbstractMavenPublishAction(String packaging, MavenProjectIdentity projectIdentity, List<File> wagonJars) {
        container = newPlexusContainer(wagonJars);
        session = new MavenRepositorySystemSession();
        this.projectIdentity = projectIdentity;

        CurrentBuildOperationRef currentBuildOperationRef = CurrentBuildOperationRef.instance();
        BuildOperationRef currentBuildOperation = currentBuildOperationRef.get();
        session.getConfigProperties().put(ConfigurationProperties.USER_AGENT, UriTextResource.getUserAgentString());
        session.setTransferListener(new LoggingMavenTransferListener(currentBuildOperationRef, currentBuildOperation));

        pomArtifact = new DefaultArtifact(projectIdentity.getGroupId().get(), projectIdentity.getArtifactId().get(), "pom", projectIdentity.getVersion().get());
        mainArtifact = createTypedArtifact(packaging, null);
    }

    public void setLocalMavenRepositoryLocation(File localMavenRepository) {
        session.setLocalRepositoryManager(new SimpleLocalRepositoryManager(localMavenRepository));
    }

    @Override
    public void setPomArtifact(File file) {
        pomArtifact = pomArtifact.setFile(file);
    }

    @Override
    public void setMainArtifact(File file) {
        mainArtifact = mainArtifact.setFile(file);
    }

    @Override
    public void addAdditionalArtifact(File file, String type, String classifier) {
        attached.add(createTypedArtifact(type, classifier).setFile(file));
    }

    @Override
    public void publish() {
        List<Artifact> artifacts = new ArrayList<Artifact>();
        if (mainArtifact.getFile() != null) {
            artifacts.add(mainArtifact);
        }
        artifacts.add(pomArtifact);
        for (Artifact artifact : attached) {
            File file = artifact.getFile();
            if (file != null && file.isFile()) {
                artifacts.add(artifact);
            }
        }

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

    private PlexusContainer newPlexusContainer(List<File> wagonJars) {
        try {
            ClassWorld world = new ClassWorld("plexus.core", ClassWorld.class.getClassLoader());
            ClassRealm classRealm = new ClassRealm(world, "plexus.core", ClassWorld.class.getClassLoader());
            if (wagonJars != null) {
                for (File jar : wagonJars) {
                    classRealm.addURL(jar.toURI().toURL());
                }
            }
            return new DefaultPlexusContainer(new DefaultContainerConfiguration().setRealm(classRealm));
        } catch (PlexusContainerException | MalformedURLException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }

    private RepositorySystem newRepositorySystem() {
        try {
            DefaultDeployer deployer = (DefaultDeployer) getContainer().lookup(Deployer.class);
            // This is a workaround for https://issues.gradle.org/browse/GRADLE-3324.
            // Somehow the ArrayList 'result' in `org.sonatype.aether.impl.internal.Utils#sortMetadataGeneratorFactories` ends up
            // being a list of nulls on windows and IBM's 1.6 JDK.
            deployer.setMetadataFactories(null);
            deployer.addMetadataGeneratorFactory(new VersionsMetadataGeneratorFactory());
            deployer.addMetadataGeneratorFactory(new SnapshotMetadataGeneratorFactory());
            return container.lookup(RepositorySystem.class);
        } catch (ComponentLookupException e) {
            throw UncheckedException.throwAsUncheckedException(e);
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
        return new DefaultArtifact(projectIdentity.getGroupId().get(), projectIdentity.getArtifactId().get(), classifier, extension, projectIdentity.getVersion().get());
    }
}

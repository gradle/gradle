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
package org.gradle.api.publication.maven.internal.ant;

import org.apache.maven.artifact.ant.DeployTask;
import org.apache.maven.artifact.ant.InstallDeployTaskSupport;
import org.apache.maven.artifact.ant.RemoteRepository;
import org.apache.tools.ant.Project;
import org.codehaus.plexus.PlexusContainer;
import org.codehaus.plexus.PlexusContainerException;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.maven.MavenDeployer;
import org.gradle.api.artifacts.maven.PomFilterContainer;
import org.gradle.api.publication.maven.internal.ArtifactPomContainer;
import org.gradle.logging.LoggingManagerInternal;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class BaseMavenDeployer extends AbstractMavenResolver implements MavenDeployer {
    private RemoteRepository remoteRepository;

    private RemoteRepository remoteSnapshotRepository;

    private Configuration configuration;

    // todo remove this property once configuration can handle normal file system dependencies
    private List<File> protocolProviderJars = new ArrayList<File>();

    private boolean uniqueVersion = true;

    public BaseMavenDeployer(PomFilterContainer pomFilterContainer, ArtifactPomContainer artifactPomContainer, LoggingManagerInternal loggingManager) {
        super(pomFilterContainer, artifactPomContainer, loggingManager);
    }

    protected InstallDeployTaskSupport createPreConfiguredTask(Project project) {
        CustomDeployTask deployTask = createTask();
        deployTask.setProject(project);
        deployTask.setUniqueVersion(isUniqueVersion());
        addProtocolProvider(deployTask);
        addRemoteRepositories(deployTask);
        return deployTask;
    }

    protected CustomDeployTask createTask() {
        return new CustomDeployTask();
    }

    private void addProtocolProvider(CustomDeployTask deployTask) {
        PlexusContainer plexusContainer = deployTask.getContainer();
        for (File wagonProviderJar : getJars()) {
            try {
                plexusContainer.addJarResource(wagonProviderJar);
            } catch (PlexusContainerException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private List<File> getJars() {
        return configuration != null ? new ArrayList<File>(configuration.resolve()) : protocolProviderJars;
    }

    private void addRemoteRepositories(DeployTask deployTask) {
        deployTask.addRemoteRepository(remoteRepository);
        deployTask.addRemoteSnapshotRepository(remoteSnapshotRepository);
    }

    public RemoteRepository getRepository() {
        return remoteRepository;
    }

    public void setRepository(Object remoteRepository) {
        this.remoteRepository = (RemoteRepository) remoteRepository;
    }

    public RemoteRepository getSnapshotRepository() {
        return remoteSnapshotRepository;
    }

    public void setSnapshotRepository(Object remoteSnapshotRepository) {
        this.remoteSnapshotRepository = (RemoteRepository) remoteSnapshotRepository;
    }

    public void addProtocolProviderJars(Collection<File> jars) {
        protocolProviderJars.addAll(jars);
    }

    public Configuration getConfiguration() {
        return configuration;
    }

    public void setConfiguration(Configuration configuration) {
        this.configuration = configuration;
    }

    public boolean isUniqueVersion() {
        return uniqueVersion;
    }

    public void setUniqueVersion(boolean uniqueVersion) {
        this.uniqueVersion = uniqueVersion;
    }
}

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
package org.gradle.api.internal.dependencies.maven.deploy;

import org.apache.maven.artifact.ant.DeployTask;
import org.apache.maven.artifact.ant.InstallDeployTaskSupport;
import org.apache.maven.artifact.ant.RemoteRepository;
import org.apache.tools.ant.Project;
import org.codehaus.plexus.PlexusContainer;
import org.codehaus.plexus.PlexusContainerException;
import org.gradle.api.DependencyManager;
import org.gradle.api.dependencies.maven.MavenDeployer;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Hans Dockter
 */
public class BaseMavenDeployer extends AbstractMavenResolver implements MavenDeployer {
    private RemoteRepository remoteRepository;

    private RemoteRepository remoteSnapshotRepository;

    private DeployTaskFactory deployTaskFactory = new DefaultDeployTaskFactory();

    private List<File> protocolProviderJars = new ArrayList<File>();

    private boolean uniqueVersion;

    public BaseMavenDeployer(String name, ArtifactPomContainer artifactPomContainer, DependencyManager dependencyManager) {
        super(name, artifactPomContainer, dependencyManager);
    }

    protected InstallDeployTaskSupport createPreConfiguredTask(Project project) {
        CustomDeployTask deployTask = deployTaskFactory.createDeployTask();
        deployTask.setProject(project);
        deployTask.setUniqueVersion(isUniqueVersion());
        addProtocolProvider(deployTask);
        addRemoteRepositories(deployTask);
        return deployTask;
    }

    private void addProtocolProvider(CustomDeployTask deployTask) {
        PlexusContainer plexusContainer = deployTask.getContainer();
        for (File wagonProviderJar : protocolProviderJars) {
            try {
                plexusContainer.addJarResource(wagonProviderJar);
            } catch (PlexusContainerException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private void addRemoteRepositories(DeployTask deployTask) {
        deployTask.addRemoteRepository(remoteRepository);
        deployTask.addRemoteSnapshotRepository(remoteSnapshotRepository);
    }

    public RemoteRepository getRepository() {
        return remoteRepository;
    }

    public void setRepository(RemoteRepository remoteRepository) {
        this.remoteRepository = remoteRepository;
    }

    public RemoteRepository getSnapshotRepository() {
        return remoteSnapshotRepository;
    }

    public void setSnapshotRepository(RemoteRepository remoteSnapshotRepository) {
        this.remoteSnapshotRepository = remoteSnapshotRepository;
    }

    public DeployTaskFactory getDeployTaskFactory() {
        return deployTaskFactory;
    }

    public void setDeployTaskFactory(DeployTaskFactory deployTaskFactory) {
        this.deployTaskFactory = deployTaskFactory;
    }

    public void addProtocolProviderJars(List<File> jars) {
        protocolProviderJars.addAll(jars);
    }

    public boolean isUniqueVersion() {
        return uniqueVersion;
    }

    public void setUniqueVersion(boolean uniqueVersion) {
        this.uniqueVersion = uniqueVersion;
    }
}

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

package org.gradle.api.publish.maven.internal;

import org.apache.maven.artifact.ant.AttachedArtifact;
import org.apache.maven.artifact.ant.InstallDeployTaskSupport;
import org.apache.maven.artifact.ant.Pom;
import org.apache.maven.artifact.ant.RemoteRepository;
import org.gradle.api.artifacts.repositories.MavenArtifactRepository;
import org.gradle.api.logging.LogLevel;
import org.gradle.api.publication.maven.internal.ant.CustomDeployTask;
import org.gradle.api.publication.maven.internal.ant.EmptyMavenSettingsSupplier;
import org.gradle.api.publication.maven.internal.ant.MavenSettingsSupplier;
import org.gradle.api.publication.maven.internal.ant.NoInstallDeployTaskFactory;
import org.gradle.api.publish.maven.MavenArtifact;
import org.gradle.internal.Factory;
import org.gradle.logging.LoggingManagerInternal;
import org.gradle.util.AntUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.Set;

public class MavenPublisher {
    private final Factory<LoggingManagerInternal> loggingManagerFactory;

    private static Logger logger = LoggerFactory.getLogger(MavenPublisher.class);
    private final Factory<File> temporaryDirFactory;

    public MavenPublisher(Factory<LoggingManagerInternal> loggingManagerFactory, Factory<File> temporaryDirFactory) {
        this.loggingManagerFactory = loggingManagerFactory;
        this.temporaryDirFactory = temporaryDirFactory;
    }

    public void publish(MavenNormalizedPublication publication, MavenArtifactRepository artifactRepository) {
        publication.validateArtifacts();

        logger.info("Publishing to repository {}", artifactRepository);
        CustomDeployTask deployTask = createDeployTask();

        MavenSettingsSupplier mavenSettingsSupplier = new EmptyMavenSettingsSupplier();
        mavenSettingsSupplier.supply(deployTask);

        addRepository(deployTask, artifactRepository);
        addPomAndArtifact(deployTask, publication.getPomFile(), publication.getMainArtifact(), publication.getAdditionalArtifacts());
        execute(deployTask);

        mavenSettingsSupplier.done();
    }

    private CustomDeployTask createDeployTask() {
        Factory<CustomDeployTask> deployTaskFactory = new NoInstallDeployTaskFactory(temporaryDirFactory);
        CustomDeployTask deployTask = deployTaskFactory.create();
        deployTask.setProject(AntUtil.createProject());
        deployTask.setUniqueVersion(true);
        return deployTask;
    }

    private void addRepository(CustomDeployTask deployTask, MavenArtifactRepository artifactRepository) {
        RemoteRepository mavenRepository = new MavenDeployerConfigurer(artifactRepository).createRepository();
        deployTask.addRemoteRepository(mavenRepository);
    }

    private void addPomAndArtifact(InstallDeployTaskSupport installOrDeployTask, File pomFile, MavenArtifact mainArtifact, Set<MavenArtifact> attachedArtifacts) {
        Pom pom = new Pom();
        pom.setProject(installOrDeployTask.getProject());
        pom.setFile(pomFile);
        installOrDeployTask.addPom(pom);

        File artifactFile = mainArtifact == null ? pomFile : mainArtifact.getFile();
        installOrDeployTask.setFile(artifactFile);

        for (MavenArtifact classifierArtifact : attachedArtifacts) {
            AttachedArtifact attachedArtifact = installOrDeployTask.createAttach();
            attachedArtifact.setClassifier(classifierArtifact.getClassifier());
            attachedArtifact.setFile(classifierArtifact.getFile());
            attachedArtifact.setType(classifierArtifact.getExtension());
        }
    }

    private void execute(InstallDeployTaskSupport deployTask) {
            LoggingManagerInternal loggingManager = loggingManagerFactory.create();
            loggingManager.captureStandardOutput(LogLevel.INFO).start();
            try {
                deployTask.execute();
            } finally {
                loggingManager.stop();
            }
        }

}

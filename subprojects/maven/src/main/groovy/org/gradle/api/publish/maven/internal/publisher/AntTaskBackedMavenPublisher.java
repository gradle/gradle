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
import org.gradle.api.publish.maven.InvalidMavenPublicationException;
import org.gradle.api.publish.maven.MavenArtifact;
import org.gradle.api.specs.Spec;
import org.gradle.internal.Factory;
import org.gradle.logging.LoggingManagerInternal;
import org.gradle.util.AntUtil;
import org.gradle.util.CollectionUtils;
import org.gradle.util.GUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.Set;

public class AntTaskBackedMavenPublisher implements MavenPublisher {
    private final Factory<LoggingManagerInternal> loggingManagerFactory;

    private static Logger logger = LoggerFactory.getLogger(AntTaskBackedMavenPublisher.class);
    private final Factory<File> temporaryDirFactory;

    public AntTaskBackedMavenPublisher(Factory<LoggingManagerInternal> loggingManagerFactory, Factory<File> temporaryDirFactory) {
        this.loggingManagerFactory = loggingManagerFactory;
        this.temporaryDirFactory = temporaryDirFactory;
    }

    public void publish(MavenNormalizedPublication publication, MavenArtifactRepository artifactRepository) {
        logger.info("Publishing to repository {}", artifactRepository);
        CustomDeployTask deployTask = createDeployTask();

        MavenSettingsSupplier mavenSettingsSupplier = new EmptyMavenSettingsSupplier();
        mavenSettingsSupplier.supply(deployTask);

        addRepository(deployTask, artifactRepository);
        addPomAndArtifacts(deployTask, publication);
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

    private void addPomAndArtifacts(InstallDeployTaskSupport installOrDeployTask, MavenNormalizedPublication publication) {
        Pom pom = new Pom();
        pom.setProject(installOrDeployTask.getProject());
        pom.setFile(publication.getPomFile());
        installOrDeployTask.addPom(pom);

        MavenArtifact mainArtifact = determineMainArtifact(publication.getName(), publication.getArtifacts());
        installOrDeployTask.setFile(mainArtifact == null ? publication.getPomFile() : mainArtifact.getFile());

        for (MavenArtifact mavenArtifact : publication.getArtifacts()) {
            if (mavenArtifact == mainArtifact) {
                continue;
            }
            AttachedArtifact attachedArtifact = installOrDeployTask.createAttach();
            attachedArtifact.setClassifier(GUtil.elvis(mavenArtifact.getClassifier(), ""));
            attachedArtifact.setType(GUtil.elvis(mavenArtifact.getExtension(), ""));
            attachedArtifact.setFile(mavenArtifact.getFile());
        }
    }

    private MavenArtifact determineMainArtifact(String publicationName, Set<MavenArtifact> mavenArtifacts) {
        Set<MavenArtifact> candidateMainArtifacts = CollectionUtils.filter(mavenArtifacts, new Spec<MavenArtifact>() {
            public boolean isSatisfiedBy(MavenArtifact element) {
                return element.getClassifier() == null || element.getClassifier().length() == 0;
            }
        });
        if (candidateMainArtifacts.isEmpty()) {
            return null;
        }
        if (candidateMainArtifacts.size() > 1) {
            throw new InvalidMavenPublicationException(publicationName, "Cannot determine main artifact - multiple artifacts found with empty classifier.");
        }
        return candidateMainArtifacts.iterator().next();
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

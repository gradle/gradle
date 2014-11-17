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

import org.apache.maven.artifact.ant.AttachedArtifact;
import org.apache.maven.artifact.ant.InstallDeployTaskSupport;
import org.apache.maven.artifact.ant.Pom;
import org.gradle.api.artifacts.repositories.MavenArtifactRepository;
import org.gradle.api.logging.LogLevel;
import org.gradle.api.publication.maven.internal.ant.EmptyMavenSettingsSupplier;
import org.gradle.api.publication.maven.internal.ant.MavenSettingsSupplier;
import org.gradle.api.publish.maven.MavenArtifact;
import org.gradle.internal.Factory;
import org.gradle.logging.LoggingManagerInternal;
import org.gradle.util.AntUtil;
import org.gradle.util.GUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

abstract public class AbstractAntTaskBackedMavenPublisher<T extends InstallDeployTaskSupport> implements MavenPublisher {
    private final Factory<LoggingManagerInternal> loggingManagerFactory;

    private static Logger logger = LoggerFactory.getLogger(AbstractAntTaskBackedMavenPublisher.class);
    protected final Factory<File> temporaryDirFactory;

    public AbstractAntTaskBackedMavenPublisher(Factory<LoggingManagerInternal> loggingManagerFactory, Factory<File> temporaryDirFactory) {
        this.loggingManagerFactory = loggingManagerFactory;
        this.temporaryDirFactory = temporaryDirFactory;
    }

    public void publish(MavenNormalizedPublication publication, MavenArtifactRepository artifactRepository) {
        logger.info("Publishing to repository {}", artifactRepository);
        T deployTask = createDeployTask();
        deployTask.setProject(AntUtil.createProject());

        MavenSettingsSupplier mavenSettingsSupplier = new EmptyMavenSettingsSupplier();
        mavenSettingsSupplier.supply(deployTask);

        postConfigure(deployTask, artifactRepository);
        addPomAndArtifacts(deployTask, publication);
        execute(deployTask);

        mavenSettingsSupplier.done();
    }

    abstract protected void postConfigure(T task, MavenArtifactRepository artifactRepository);

    abstract protected T createDeployTask();

    private void addPomAndArtifacts(InstallDeployTaskSupport installOrDeployTask, MavenNormalizedPublication publication) {
        Pom pom = new Pom();
        pom.setProject(installOrDeployTask.getProject());
        pom.setFile(publication.getPomFile());
        installOrDeployTask.addPom(pom);

        MavenArtifact mainArtifact = publication.getMainArtifact();
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

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
import org.gradle.api.GradleException;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.ProjectDependency;
import org.gradle.api.artifacts.maven.Conf2ScopeMappingContainer;
import org.gradle.api.artifacts.maven.MavenPom;
import org.gradle.api.artifacts.repositories.MavenArtifactRepository;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.logging.LogLevel;
import org.gradle.api.publication.maven.internal.DefaultConf2ScopeMappingContainer;
import org.gradle.api.publication.maven.internal.DefaultMavenFactory;
import org.gradle.api.publication.maven.internal.MavenPomMetaInfoProvider;
import org.gradle.api.publication.maven.internal.PomDependenciesConverter;
import org.gradle.api.publication.maven.internal.ant.*;
import org.gradle.api.publish.maven.MavenArtifact;
import org.gradle.internal.Factory;
import org.gradle.logging.LoggingManagerInternal;
import org.gradle.util.AntUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class MavenPublisher {
    private final Factory<Configuration> configurationFactory;
    private final PomDependenciesConverter pomDependenciesConverter = new MavenPublishPomDependenciesConverter();
    private final Factory<LoggingManagerInternal> loggingManagerFactory;

    private static Logger logger = LoggerFactory.getLogger(MavenPublisher.class);
    private final FileResolver fileResolver;
    private final ConfigurationContainer configurations;
    private final Factory<File> temporaryDirFactory;
    private final MavenPomMetaInfoProvider pomMetaInfoProvider;

    public MavenPublisher(Factory<Configuration> configurationFactory, Factory<LoggingManagerInternal> loggingManagerFactory, FileResolver fileResolver, ConfigurationContainer configurations, Factory<File> temporaryDirFactory, MavenPomMetaInfoProvider pomMetaInfoProvider) {
        this.configurationFactory = configurationFactory;
        this.loggingManagerFactory = loggingManagerFactory;
        this.fileResolver = fileResolver;

        this.configurations = configurations;
        this.temporaryDirFactory = temporaryDirFactory;
        this.pomMetaInfoProvider = pomMetaInfoProvider;
    }

    public void publish(MavenNormalizedPublication publication, MavenArtifactRepository artifactRepository) {
        File pomFile = createPomFile(publication);

        logger.info("Publishing to repository {}", artifactRepository);
        doPublish(artifactRepository, pomFile, publication.getMainArtifact(), publication.getAdditionalArtifacts());
    }

    private File createPomFile(MavenNormalizedPublication publication) {
        Factory<MavenPom> mavenPomFactory = new DefaultMavenFactory().createMavenPomFactory(configurations, new DefaultConf2ScopeMappingContainer(), fileResolver);
        MavenPom mavenPom = mavenPomFactory.create();
        copyIdentity(publication, mavenPom);
        copyDependencies(publication, mavenPom);
        mavenPom.withXml(publication.getPomWithXmlAction());

        File pomFile = new File(pomMetaInfoProvider.getMavenPomDir(), "pom-default.xml");
        mavenPom.writeTo(pomFile);
        return pomFile;
    }

    private void copyDependencies(MavenNormalizedPublication publication, MavenPom deployerPom) {
        Configuration configuration = configurationFactory.create();
        for (Dependency runtimeDependency : publication.getRuntimeDependencies()) {
            configuration.getDependencies().add(runtimeDependency);
        }
        Conf2ScopeMappingContainer mappingContainer = new DefaultConf2ScopeMappingContainer();
        mappingContainer.addMapping(0, configuration, "runtime");
        List<?> mavenDependencies = pomDependenciesConverter.convert(mappingContainer, Collections.singleton(configuration));
        deployerPom.setDependencies(mavenDependencies);
    }

    private void copyIdentity(MavenProjectIdentity projectIdentity, MavenPom pom) {
        pom.setArtifactId(projectIdentity.getArtifactId());
        pom.setGroupId(projectIdentity.getGroupId());
        pom.setVersion(projectIdentity.getVersion());
        pom.setPackaging(projectIdentity.getPackaging());
    }

    private static class MavenPublishPomDependenciesConverter extends DefaultPomDependenciesConverter {
        public MavenPublishPomDependenciesConverter() {
            super(new DefaultExcludeRuleConverter());
        }

        @Override
        protected String determineProjectDependencyArtifactId(ProjectDependency dependency) {
            return dependency.getDependencyProject().getName();
        }
    }

    private void doPublish(MavenArtifactRepository artifactRepository, File pomFile, MavenArtifact mainArtifact, Set<MavenArtifact> additionalArtifacts) {
        CustomDeployTask deployTask = createDeployTask();

        MavenSettingsSupplier mavenSettingsSupplier = new EmptyMavenSettingsSupplier();
        mavenSettingsSupplier.supply(deployTask);

        addRepository(deployTask, artifactRepository);
        addPomAndArtifact(deployTask, pomFile, mainArtifact, additionalArtifacts);
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

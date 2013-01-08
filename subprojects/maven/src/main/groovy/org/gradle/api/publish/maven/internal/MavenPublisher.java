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

import org.apache.ivy.core.module.descriptor.Artifact;
import org.apache.ivy.core.module.descriptor.DefaultArtifact;
import org.apache.ivy.core.module.id.ModuleRevisionId;
import org.apache.maven.artifact.ant.AttachedArtifact;
import org.apache.maven.artifact.ant.InstallDeployTaskSupport;
import org.apache.maven.artifact.ant.Pom;
import org.apache.maven.artifact.ant.RemoteRepository;
import org.apache.tools.ant.Project;
import org.codehaus.plexus.PlexusContainer;
import org.codehaus.plexus.PlexusContainerException;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.artifacts.*;
import org.gradle.api.artifacts.maven.Conf2ScopeMappingContainer;
import org.gradle.api.artifacts.maven.MavenDeployment;
import org.gradle.api.artifacts.maven.MavenPom;
import org.gradle.api.artifacts.repositories.MavenArtifactRepository;
import org.gradle.api.internal.artifacts.ivyservice.DefaultIvyDependencyPublisher;
import org.gradle.api.internal.artifacts.ivyservice.IvyUtil;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.logging.LogLevel;
import org.gradle.api.publication.maven.internal.*;
import org.gradle.api.publication.maven.internal.ant.*;
import org.gradle.api.publish.maven.plugins.MavenPublishPlugin;
import org.gradle.internal.Factory;
import org.gradle.logging.LoggingManagerInternal;
import org.gradle.util.AntUtil;
import org.gradle.util.DeprecationLogger;
import org.gradle.util.GUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MavenPublisher {
    private final Factory<Configuration> configurationFactory;
    private final PomDependenciesConverter pomDependenciesConverter = new MavenPublishPomDependenciesConverter();
    private final Factory<LoggingManagerInternal> loggingManagerFactory;

    private static Logger logger = LoggerFactory.getLogger(DefaultIvyDependencyPublisher.class);
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
        RemoteRepository mavenRepository = new MavenDeployerConfigurer(artifactRepository).createRepository();

        Factory<MavenPom> mavenPomFactory = new DefaultMavenFactory().createMavenPomFactory(configurations, new DefaultConf2ScopeMappingContainer(), fileResolver);
        MavenPom deployerPom = mavenPomFactory.create();

        MavenDeploymentFactory mavenDeploymentFactory = new MavenDeploymentFactory(pomMetaInfoProvider, deployerPom);

        copyIdentity(publication, deployerPom);
        copyDependencies(publication, deployerPom);
        deployerPom.withXml(publication.getPomWithXmlAction());

        Module module = new MavenProjectIdentityModuleAdapter(publication);

        MavenPublishAntTaskAdapter mavenPublishAntTaskAdapter = new MavenPublishAntTaskAdapter(loggingManagerFactory.create());
        ModuleRevisionId moduleRevisionId = IvyUtil.createModuleRevisionId(module);
        doPublish(mavenPublishAntTaskAdapter, publication.getArtifacts(), moduleRevisionId, mavenRepository, mavenDeploymentFactory);
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

    public void doPublish(MavenPublishAntTaskAdapter publishResolver, Iterable<PublishArtifact> publishArtifacts, ModuleRevisionId moduleRevisionId, RemoteRepository mavenRepository, MavenDeploymentFactory artifactPomContainer) {
        logger.info("Publishing to repository {}", publishResolver);

        for (PublishArtifact publishArtifact : publishArtifacts) {
            checkArtifactFileExists(publishArtifact, moduleRevisionId);
        }

        // for each declared published artifact in this descriptor, do:
        for (PublishArtifact publishArtifact : publishArtifacts) {
            Artifact artifact = createIvyArtifact(publishArtifact, moduleRevisionId);
            File artifactFile = publishArtifact.getFile();
            if (!artifact.getType().equals("ivy")) {
                artifactPomContainer.addArtifact(artifact, artifactFile);
            }
        }
        MavenDeployment mavenDeployment = artifactPomContainer.createMavenDeployment();
        publishResolver.publishToMavenRepository(mavenRepository, mavenDeployment);
    }

    public Artifact createIvyArtifact(PublishArtifact publishArtifact, ModuleRevisionId moduleRevisionId) {
        Map<String, String> extraAttributes = new HashMap<String, String>();
        if (GUtil.isTrue(publishArtifact.getClassifier())) {
            extraAttributes.put(Dependency.CLASSIFIER, publishArtifact.getClassifier());
        }
        return new DefaultArtifact(
                moduleRevisionId,
                publishArtifact.getDate(),
                publishArtifact.getName(),
                publishArtifact.getType(),
                publishArtifact.getExtension(),
                extraAttributes);
    }

    private void checkArtifactFileExists(PublishArtifact publishArtifact, ModuleRevisionId moduleRevisionId) {
        if (!publishArtifact.getFile().exists()) {
            // TODO:DAZ This hack is required so that we don't log a warning when the Signing plugin is used. We need to allow conditional configurations so we can remove this.
            if (isSigningArtifact(publishArtifact)) {
                return;
            }
            String message = String.format("Attempted to publish an artifact '%s' that does not exist '%s'", moduleRevisionId, publishArtifact.getFile());
            DeprecationLogger.nagUserOfDeprecatedBehaviour(message);
        }
    }

    private boolean isSigningArtifact(PublishArtifact artifact) {
        return artifact.getType().endsWith(".asc") || artifact.getType().endsWith(".sig");
    }

    private class MavenDeploymentFactory {
        private final MavenPomMetaInfoProvider pomMetaInfoProvider;
        private ArtifactPom artifactPom;
        private boolean hasArtifacts;

        public MavenDeploymentFactory(MavenPomMetaInfoProvider pomMetaInfoProvider,
                                      MavenPom pom) {
            this.pomMetaInfoProvider = pomMetaInfoProvider;
            this.artifactPom = new DefaultArtifactPom(pom);
        }

        public void addArtifact(Artifact artifact, File src) {
            if (artifact == null || src == null) {
                throw new InvalidUserDataException("Artifact or source file must not be null!");
            }
            artifactPom.addArtifact(artifact, src);
            hasArtifacts = true;
        }

        public MavenDeployment createMavenDeployment() {
            if (!hasArtifacts) {
                artifactPom.getPom().setPackaging("pom");
            }

            File pomFile = createPomFile();
            PublishArtifact pomPublishArtifact = artifactPom.writePom(pomFile);
            return new DefaultMavenDeployment(pomPublishArtifact, artifactPom.getArtifact(), artifactPom.getAttachedArtifacts());
        }

        private File createPomFile() {
            return new File(pomMetaInfoProvider.getMavenPomDir(), "pom-default.xml");
        }
    }


    private static class MavenPublishPomDependenciesConverter extends DefaultPomDependenciesConverter {
        public MavenPublishPomDependenciesConverter() {
            super(new DefaultExcludeRuleConverter());
        }

        @Override
        protected String determineProjectDependencyArtifactId(ProjectDependency dependency) {
            // Don't use artifact id hacks when the target project has the maven-publish plugin applied
            if (dependency.getDependencyProject().getPlugins().hasPlugin(MavenPublishPlugin.class)) {
                return dependency.getDependencyProject().getName();
            }
            return new ProjectDependencyArtifactIdExtractorHack(dependency).extract();
        }
    }

    private class MavenPublishAntTaskAdapter {
        private final LoggingManagerInternal loggingManager;
        protected MavenSettingsSupplier mavenSettingsSupplier = new EmptyMavenSettingsSupplier();
        private Factory<CustomDeployTask> deployTaskFactory = new NoInstallDeployTaskFactory(temporaryDirFactory);

        public MavenPublishAntTaskAdapter(LoggingManagerInternal loggingManager) {
            this.loggingManager = loggingManager;
        }

        public void publishToMavenRepository(RemoteRepository repository, MavenDeployment mavenDeployment) {
            InstallDeployTaskSupport installDeployTaskSupport = createPreConfiguredTask(AntUtil.createProject(), repository);
            mavenSettingsSupplier.supply(installDeployTaskSupport);
            ((CustomInstallDeployTaskSupport) installDeployTaskSupport).clearAttachedArtifactsList();
            addPomAndArtifact(installDeployTaskSupport, mavenDeployment);
            execute(installDeployTaskSupport);
            mavenSettingsSupplier.done();
        }

        private void execute(InstallDeployTaskSupport deployTask) {
            loggingManager.captureStandardOutput(LogLevel.INFO).start();
            try {
                deployTask.execute();
            } finally {
                loggingManager.stop();
            }
        }

        private void addPomAndArtifact(InstallDeployTaskSupport installOrDeployTask, MavenDeployment mavenDeployment) {
            Pom pom = new Pom();
            pom.setProject(installOrDeployTask.getProject());
            pom.setFile(mavenDeployment.getPomArtifact().getFile());
            installOrDeployTask.addPom(pom);

            File artifactFile = mavenDeployment.getMainArtifact() == null ? mavenDeployment.getPomArtifact().getFile() : mavenDeployment.getMainArtifact().getFile();
            installOrDeployTask.setFile(artifactFile);

            for (PublishArtifact classifierArtifact : mavenDeployment.getAttachedArtifacts()) {
                AttachedArtifact attachedArtifact = installOrDeployTask.createAttach();
                attachedArtifact.setClassifier(classifierArtifact.getClassifier());
                attachedArtifact.setFile(classifierArtifact.getFile());
                attachedArtifact.setType(classifierArtifact.getType());
            }
        }

        protected InstallDeployTaskSupport createPreConfiguredTask(Project project, RemoteRepository repository) {
            CustomDeployTask deployTask = deployTaskFactory.create();
            deployTask.setProject(project);
            deployTask.setUniqueVersion(true);
            addProtocolProvider(deployTask);
            deployTask.addRemoteRepository(repository);
            return deployTask;
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
            return Collections.emptyList();
        }
    }


}

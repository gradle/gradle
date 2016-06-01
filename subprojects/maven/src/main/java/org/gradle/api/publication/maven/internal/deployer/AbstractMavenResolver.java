/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.api.publication.maven.internal.deployer;

import groovy.lang.Closure;
import org.apache.ivy.core.module.descriptor.Artifact;
import org.apache.ivy.core.module.descriptor.DefaultArtifact;
import org.apache.ivy.core.module.id.ModuleRevisionId;
import org.apache.maven.settings.building.SettingsBuildingException;
import org.gradle.api.Action;
import org.gradle.api.GradleException;
import org.gradle.api.artifacts.PublishArtifact;
import org.gradle.api.artifacts.maven.MavenDeployment;
import org.gradle.api.artifacts.maven.MavenPom;
import org.gradle.api.artifacts.maven.MavenResolver;
import org.gradle.api.artifacts.maven.PomFilterContainer;
import org.gradle.api.artifacts.maven.PublishFilter;
import org.gradle.api.internal.artifacts.ModuleVersionPublisher;
import org.gradle.api.internal.artifacts.ivyservice.IvyUtil;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ConfiguredModuleComponentRepository;
import org.gradle.api.internal.artifacts.mvnsettings.LocalMavenRepositoryLocator;
import org.gradle.api.internal.artifacts.mvnsettings.MavenSettingsProvider;
import org.gradle.api.internal.artifacts.repositories.AbstractArtifactRepository;
import org.gradle.api.internal.artifacts.repositories.PublicationAwareRepository;
import org.gradle.api.internal.artifacts.repositories.ResolutionAwareRepository;
import org.gradle.api.logging.LogLevel;
import org.gradle.api.publication.maven.internal.ArtifactPomContainer;
import org.gradle.api.publication.maven.internal.PomFilter;
import org.gradle.api.publication.maven.internal.action.MavenPublishAction;
import org.gradle.internal.component.external.model.IvyModuleArtifactPublishMetadata;
import org.gradle.internal.component.external.model.IvyModulePublishMetadata;
import org.gradle.internal.component.model.IvyArtifactName;
import org.gradle.internal.logging.LoggingManagerInternal;
import org.gradle.listener.ActionBroadcast;
import org.gradle.util.ConfigureUtil;

import java.io.File;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

abstract class AbstractMavenResolver extends AbstractArtifactRepository implements MavenResolver, ModuleVersionPublisher, ResolutionAwareRepository, PublicationAwareRepository {

    private ArtifactPomContainer artifactPomContainer;

    private PomFilterContainer pomFilterContainer;

    private LoggingManagerInternal loggingManager;

    private final ActionBroadcast<MavenDeployment> beforeDeploymentActions = new ActionBroadcast<MavenDeployment>();

    private final MavenSettingsProvider mavenSettingsProvider;
    private final LocalMavenRepositoryLocator mavenRepositoryLocator;

    public AbstractMavenResolver(PomFilterContainer pomFilterContainer, ArtifactPomContainer artifactPomContainer,
                                 LoggingManagerInternal loggingManager, MavenSettingsProvider mavenSettingsProvider, LocalMavenRepositoryLocator mavenRepositoryLocator) {
        this.pomFilterContainer = pomFilterContainer;
        this.artifactPomContainer = artifactPomContainer;
        this.loggingManager = loggingManager;
        this.mavenSettingsProvider = mavenSettingsProvider;
        this.mavenRepositoryLocator = mavenRepositoryLocator;
    }

    public ConfiguredModuleComponentRepository createResolver() {
        throw new UnsupportedOperationException("A Maven deployer cannot be used to resolve dependencies. It can only be used to publish artifacts.");
    }

    public ModuleVersionPublisher createPublisher() {
        return this;
    }

    protected abstract MavenPublishAction createPublishAction(File pomFile, LocalMavenRepositoryLocator mavenRepositoryLocator);

    public void publish(IvyModulePublishMetadata moduleVersion) {
        for (IvyModuleArtifactPublishMetadata artifactMetadata : moduleVersion.getArtifacts()) {
            IvyArtifactName artifact = artifactMetadata.getArtifactName();
            ModuleRevisionId moduleRevisionId = IvyUtil.createModuleRevisionId(artifactMetadata.getId().getComponentIdentifier());
            Map<String, String> attributes = Collections.singletonMap("classifier", artifact.getClassifier());
            Artifact ivyArtifact = new DefaultArtifact(moduleRevisionId, null, artifact.getName(), artifact.getType(), artifact.getExtension(), attributes);
            collectArtifact(ivyArtifact, artifactMetadata.getFile());
        }
        publish();
    }

    private void collectArtifact(Artifact artifact, File src) {
        if (isIgnorable(artifact)) {
            return;
        }
        getArtifactPomContainer().addArtifact(artifact, src);
    }

    private boolean isIgnorable(Artifact artifact) {
        return artifact.getType().equals("ivy");
    }

    private void publish() {
        Set<MavenDeployment> mavenDeployments = getArtifactPomContainer().createDeployableFilesInfos();
        for (MavenDeployment mavenDeployment : mavenDeployments) {
            File pomFile = mavenDeployment.getPomArtifact().getFile();
            MavenPublishAction publishAction = createPublishAction(pomFile, mavenRepositoryLocator);
            beforeDeploymentActions.execute(mavenDeployment);
            addArtifacts(publishAction, mavenDeployment);
            execute(publishAction);
        }
    }

    private void execute(MavenPublishAction publishAction) {
        loggingManager.captureStandardOutput(LogLevel.INFO).start();
        try {
            publishAction.publish();
        } finally {
            loggingManager.stop();
        }
    }

    private void addArtifacts(MavenPublishAction publishAction, MavenDeployment mavenDeployment) {
        if (mavenDeployment.getMainArtifact() != null) {
            publishAction.setMainArtifact(mavenDeployment.getMainArtifact().getFile());
        }
        for (PublishArtifact classifierArtifact : mavenDeployment.getAttachedArtifacts()) {
            publishAction.addAdditionalArtifact(classifierArtifact.getFile(), classifierArtifact.getType(), classifierArtifact.getClassifier());
        }
    }

    public ArtifactPomContainer getArtifactPomContainer() {
        return artifactPomContainer;
    }

    public Object getSettings() {
        try {
            return mavenSettingsProvider.buildSettings();
        } catch (SettingsBuildingException e) {
            throw new GradleException("Could not load Maven Settings", e);
        }
    }

    public PublishFilter getFilter() {
        return pomFilterContainer.getFilter();
    }

    public void setFilter(PublishFilter defaultFilter) {
        pomFilterContainer.setFilter(defaultFilter);
    }

    public MavenPom getPom() {
        return pomFilterContainer.getPom();
    }

    public void setPom(MavenPom defaultPom) {
        pomFilterContainer.setPom(defaultPom);
    }

    public MavenPom addFilter(String name, PublishFilter publishFilter) {
        return pomFilterContainer.addFilter(name, publishFilter);
    }

    public MavenPom addFilter(String name, Closure filter) {
        return pomFilterContainer.addFilter(name, filter);
    }

    public void filter(Closure filter) {
        pomFilterContainer.filter(filter);
    }

    public PublishFilter filter(String name) {
        return pomFilterContainer.filter(name);
    }

    public MavenPom pom(String name) {
        return pomFilterContainer.pom(name);
    }

    public MavenPom pom(Closure configureClosure) {
        return pomFilterContainer.pom(configureClosure);
    }

    public MavenPom pom(String name, Closure configureClosure) {
        return pomFilterContainer.pom(name, configureClosure);
    }

    public Iterable<PomFilter> getActivePomFilters() {
        return pomFilterContainer.getActivePomFilters();
    }

    public PomFilterContainer getPomFilterContainer() {
        return pomFilterContainer;
    }

    public void beforeDeployment(Action<? super MavenDeployment> action) {
        beforeDeploymentActions.add(action);
    }

    public void beforeDeployment(Closure action) {
        beforeDeploymentActions.add(ConfigureUtil.configureUsing(action));
    }

}

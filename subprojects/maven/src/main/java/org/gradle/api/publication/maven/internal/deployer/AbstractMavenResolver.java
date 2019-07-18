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
import org.gradle.api.internal.artifacts.repositories.descriptor.RepositoryDescriptor;
import org.gradle.api.logging.LogLevel;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.publication.maven.internal.ArtifactPomContainer;
import org.gradle.api.publication.maven.internal.PomFilter;
import org.gradle.api.publication.maven.internal.action.MavenPublishAction;
import org.gradle.api.publish.maven.internal.publication.ReadableMavenProjectIdentity;
import org.gradle.api.publish.maven.internal.publisher.MavenProjectIdentity;
import org.gradle.internal.MutableActionSet;
import org.gradle.internal.component.external.ivypublish.IvyModuleArtifactPublishMetadata;
import org.gradle.internal.component.external.ivypublish.IvyModulePublishMetadata;
import org.gradle.internal.component.model.IvyArtifactName;
import org.gradle.internal.logging.LoggingManagerInternal;
import org.gradle.util.ConfigureUtil;

import java.io.File;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

abstract class AbstractMavenResolver extends AbstractArtifactRepository implements MavenResolver, ModuleVersionPublisher, ResolutionAwareRepository, PublicationAwareRepository {

    private ArtifactPomContainer artifactPomContainer;

    private PomFilterContainer pomFilterContainer;

    private LoggingManagerInternal loggingManager;

    private final MutableActionSet<MavenDeployment> beforeDeploymentActions = new MutableActionSet<MavenDeployment>();

    private final MavenSettingsProvider mavenSettingsProvider;
    private final LocalMavenRepositoryLocator mavenRepositoryLocator;

    public AbstractMavenResolver(PomFilterContainer pomFilterContainer, ArtifactPomContainer artifactPomContainer,
                                 LoggingManagerInternal loggingManager, MavenSettingsProvider mavenSettingsProvider,
                                 LocalMavenRepositoryLocator mavenRepositoryLocator, ObjectFactory objectFactory) {
        super(objectFactory);
        this.pomFilterContainer = pomFilterContainer;
        this.artifactPomContainer = artifactPomContainer;
        this.loggingManager = loggingManager;
        this.mavenSettingsProvider = mavenSettingsProvider;
        this.mavenRepositoryLocator = mavenRepositoryLocator;
    }

    @Override
    public ConfiguredModuleComponentRepository createResolver() {
        throw new UnsupportedOperationException("A Maven deployer cannot be used to resolve dependencies. It can only be used to publish artifacts.");
    }

    @Override
    public RepositoryDescriptor getDescriptor() {
        throw new UnsupportedOperationException("A Maven deployer cannot be used to resolve dependencies, and don't have repository details available.");
    }

    @Override
    public ModuleVersionPublisher createPublisher() {
        return this;
    }

    protected abstract MavenPublishAction createPublishAction(String packaging, MavenProjectIdentity projectIdentity, LocalMavenRepositoryLocator mavenRepositoryLocator);

    @Override
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
            MavenProjectIdentity projectIdentity = new ReadableMavenProjectIdentity(mavenDeployment.getGroupId(), mavenDeployment.getArtifactId(), mavenDeployment.getVersion());
            MavenPublishAction publishAction = createPublishAction(mavenDeployment.getPackaging(), projectIdentity, mavenRepositoryLocator);
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
        publishAction.setPomArtifact(mavenDeployment.getPomArtifact().getFile());
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

    @Override
    public Object getSettings() {
        try {
            return mavenSettingsProvider.buildSettings();
        } catch (SettingsBuildingException e) {
            throw new GradleException("Could not load Maven Settings", e);
        }
    }

    @Override
    public PublishFilter getFilter() {
        return pomFilterContainer.getFilter();
    }

    @Override
    public void setFilter(PublishFilter defaultFilter) {
        pomFilterContainer.setFilter(defaultFilter);
    }

    @Override
    public MavenPom getPom() {
        return pomFilterContainer.getPom();
    }

    @Override
    public void setPom(MavenPom defaultPom) {
        pomFilterContainer.setPom(defaultPom);
    }

    @Override
    public MavenPom addFilter(String name, PublishFilter publishFilter) {
        return pomFilterContainer.addFilter(name, publishFilter);
    }

    @Override
    public MavenPom addFilter(String name, Closure filter) {
        return pomFilterContainer.addFilter(name, filter);
    }

    @Override
    public void filter(Closure filter) {
        pomFilterContainer.filter(filter);
    }

    @Override
    public PublishFilter filter(String name) {
        return pomFilterContainer.filter(name);
    }

    @Override
    public MavenPom pom(String name) {
        return pomFilterContainer.pom(name);
    }

    @Override
    public MavenPom pom(Closure configureClosure) {
        return pomFilterContainer.pom(configureClosure);
    }

    @Override
    public MavenPom pom(String name, Closure configureClosure) {
        return pomFilterContainer.pom(name, configureClosure);
    }

    @Override
    public MavenPom pom(Action<? super MavenPom> configureAction) {
        return pomFilterContainer.pom(configureAction);
    }

    @Override
    public MavenPom pom(String name, Action<? super MavenPom> configureAction) {
        return pomFilterContainer.pom(name, configureAction);
    }

    @Override
    public Iterable<PomFilter> getActivePomFilters() {
        return pomFilterContainer.getActivePomFilters();
    }

    public PomFilterContainer getPomFilterContainer() {
        return pomFilterContainer;
    }

    @Override
    public void beforeDeployment(Action<? super MavenDeployment> action) {
        beforeDeploymentActions.add(action);
    }

    @Override
    public void beforeDeployment(Closure action) {
        beforeDeploymentActions.add(ConfigureUtil.configureUsing(action));
    }

}

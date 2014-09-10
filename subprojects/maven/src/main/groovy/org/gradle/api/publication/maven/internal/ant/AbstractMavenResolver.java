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
package org.gradle.api.publication.maven.internal.ant;

import groovy.lang.Closure;
import org.apache.ivy.core.module.descriptor.Artifact;
import org.apache.maven.artifact.ant.AttachedArtifact;
import org.apache.maven.artifact.ant.InstallDeployTaskSupport;
import org.apache.maven.artifact.ant.Pom;
import org.apache.maven.settings.Settings;
import org.apache.tools.ant.Project;
import org.gradle.api.Action;
import org.gradle.api.artifacts.PublishArtifact;
import org.gradle.api.artifacts.maven.*;
import org.gradle.api.internal.ClosureBackedAction;
import org.gradle.api.internal.artifacts.ModuleVersionPublisher;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ConfiguredModuleComponentRepository;
import org.gradle.internal.component.external.model.IvyModuleArtifactPublishMetaData;
import org.gradle.internal.component.external.model.IvyModulePublishMetaData;
import org.gradle.api.internal.artifacts.repositories.AbstractArtifactRepository;
import org.gradle.api.internal.artifacts.repositories.PublicationAwareRepository;
import org.gradle.api.internal.artifacts.repositories.ResolutionAwareRepository;
import org.gradle.api.logging.LogLevel;
import org.gradle.api.publication.maven.internal.ArtifactPomContainer;
import org.gradle.api.publication.maven.internal.PomFilter;
import org.gradle.listener.ActionBroadcast;
import org.gradle.logging.LoggingManagerInternal;
import org.gradle.util.AntUtil;

import java.io.File;
import java.util.Set;

public abstract class AbstractMavenResolver extends AbstractArtifactRepository implements MavenResolver, ModuleVersionPublisher, ResolutionAwareRepository, PublicationAwareRepository {
    
    private ArtifactPomContainer artifactPomContainer;

    private PomFilterContainer pomFilterContainer;

    private Settings settings;

    private LoggingManagerInternal loggingManager;

    private final ActionBroadcast<MavenDeployment> beforeDeploymentActions = new ActionBroadcast<MavenDeployment>();

    protected MavenSettingsSupplier mavenSettingsSupplier = new EmptyMavenSettingsSupplier();

    public AbstractMavenResolver(PomFilterContainer pomFilterContainer, ArtifactPomContainer artifactPomContainer, LoggingManagerInternal loggingManager) {
        this.pomFilterContainer = pomFilterContainer;
        this.artifactPomContainer = artifactPomContainer;
        this.loggingManager = loggingManager;
    }

    public ConfiguredModuleComponentRepository createResolver() {
        throw new UnsupportedOperationException("A Maven deployer cannot be used to resolve dependencies. It can only be used to publish artifacts.");
    }

    public ModuleVersionPublisher createPublisher() {
        return this;
    }

    protected abstract InstallDeployTaskSupport createPreConfiguredTask(Project project);

    public void publish(IvyModulePublishMetaData moduleVersion) {
        for (IvyModuleArtifactPublishMetaData artifact : moduleVersion.getArtifacts()) {
            collectArtifact(artifact.toIvyArtifact(), artifact.getFile());
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
        InstallDeployTaskSupport installDeployTaskSupport = createPreConfiguredTask(AntUtil.createProject());
        Set<MavenDeployment> mavenDeployments = getArtifactPomContainer().createDeployableFilesInfos();
        mavenSettingsSupplier.supply(installDeployTaskSupport);
        for (MavenDeployment mavenDeployment : mavenDeployments) {
            ((CustomInstallDeployTaskSupport) installDeployTaskSupport).clearAttachedArtifactsList();
            beforeDeploymentActions.execute(mavenDeployment);
            addPomAndArtifact(installDeployTaskSupport, mavenDeployment);
            execute(installDeployTaskSupport);
        }
        mavenSettingsSupplier.done();
        settings = ((CustomInstallDeployTaskSupport) installDeployTaskSupport).getSettings();
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
        if (mavenDeployment.getMainArtifact() != null) {
            installOrDeployTask.setFile(mavenDeployment.getMainArtifact().getFile());
        }
        for (PublishArtifact classifierArtifact : mavenDeployment.getAttachedArtifacts()) {
            AttachedArtifact attachedArtifact = installOrDeployTask.createAttach();
            attachedArtifact.setClassifier(classifierArtifact.getClassifier());
            attachedArtifact.setFile(classifierArtifact.getFile());
            attachedArtifact.setType(classifierArtifact.getType());
        }
    }

    public ArtifactPomContainer getArtifactPomContainer() {
        return artifactPomContainer;
    }

    public Settings getSettings() {
        return settings;
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
        beforeDeploymentActions.add(new ClosureBackedAction<MavenDeployment>(action));
    }

}

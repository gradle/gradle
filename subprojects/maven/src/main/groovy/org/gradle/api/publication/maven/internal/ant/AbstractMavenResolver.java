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
import org.apache.ivy.core.cache.ArtifactOrigin;
import org.apache.ivy.core.cache.RepositoryCacheManager;
import org.apache.ivy.core.module.descriptor.Artifact;
import org.apache.ivy.core.module.descriptor.DependencyDescriptor;
import org.apache.ivy.core.module.id.ModuleRevisionId;
import org.apache.ivy.core.report.ArtifactDownloadReport;
import org.apache.ivy.core.report.DownloadReport;
import org.apache.ivy.core.resolve.DownloadOptions;
import org.apache.ivy.core.resolve.ResolveData;
import org.apache.ivy.core.resolve.ResolvedModuleRevision;
import org.apache.ivy.core.search.ModuleEntry;
import org.apache.ivy.core.search.OrganisationEntry;
import org.apache.ivy.core.search.RevisionEntry;
import org.apache.ivy.core.settings.IvySettings;
import org.apache.ivy.plugins.namespace.Namespace;
import org.apache.ivy.plugins.resolver.DependencyResolver;
import org.apache.ivy.plugins.resolver.ResolverSettings;
import org.apache.ivy.plugins.resolver.util.ResolvedResource;
import org.apache.maven.artifact.ant.AttachedArtifact;
import org.apache.maven.artifact.ant.InstallDeployTaskSupport;
import org.apache.maven.artifact.ant.Pom;
import org.apache.maven.settings.Settings;
import org.apache.tools.ant.Project;
import org.gradle.api.Action;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.PublishArtifact;
import org.gradle.api.artifacts.maven.*;
import org.gradle.api.internal.ClosureBackedAction;
import org.gradle.api.internal.artifacts.ModuleVersionPublisher;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ConfiguredModuleVersionRepository;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.NoOpRepositoryCacheManager;
import org.gradle.api.internal.artifacts.metadata.ModuleVersionArtifactPublishMetaData;
import org.gradle.api.internal.artifacts.metadata.ModuleVersionPublishMetaData;
import org.gradle.api.internal.artifacts.repositories.AbstractArtifactRepository;
import org.gradle.api.logging.LogLevel;
import org.gradle.api.publication.maven.internal.ArtifactPomContainer;
import org.gradle.api.publication.maven.internal.PomFilter;
import org.gradle.listener.ActionBroadcast;
import org.gradle.logging.LoggingManagerInternal;
import org.gradle.util.AntUtil;

import java.io.File;
import java.io.IOException;
import java.text.ParseException;
import java.util.Map;
import java.util.Set;

public abstract class AbstractMavenResolver extends AbstractArtifactRepository implements MavenResolver, DependencyResolver, ModuleVersionPublisher {
    
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

    public ConfiguredModuleVersionRepository createResolver() {
        throw new UnsupportedOperationException("A Maven deployer cannot be used to resolve dependencies. It can only be used to publish artifacts.");
    }

    public ModuleVersionPublisher createPublisher() {
        return this;
    }

    public DependencyResolver createLegacyDslObject() {
        return this;
    }

    protected abstract InstallDeployTaskSupport createPreConfiguredTask(Project project);

    public ResolvedModuleRevision getDependency(DependencyDescriptor dd, ResolveData data) throws ParseException {
        throw new UnsupportedOperationException("A Maven deployer cannot be used to resolve dependencies. It can only be used to publish artifacts.");
    }

    public ResolvedResource findIvyFileRef(DependencyDescriptor dd, ResolveData data) {
        throw new UnsupportedOperationException("A Maven deployer cannot be used to resolve dependencies. It can only be used to publish artifacts.");
    }

    public DownloadReport download(Artifact[] artifacts, DownloadOptions options) {
        throw new UnsupportedOperationException("A Maven deployer cannot be used to resolve dependencies. It can only be used to publish artifacts.");
    }

    public ArtifactDownloadReport download(ArtifactOrigin artifact, DownloadOptions options) {
        throw new UnsupportedOperationException("A Maven deployer cannot be used to resolve dependencies. It can only be used to publish artifacts.");
    }

    public boolean exists(Artifact artifact) {
        throw new UnsupportedOperationException("A Maven deployer cannot be used to resolve dependencies. It can only be used to publish artifacts.");
    }

    public ArtifactOrigin locate(Artifact artifact) {
        throw new UnsupportedOperationException("A Maven deployer cannot be used to resolve dependencies. It can only be used to publish artifacts.");
    }

    public void reportFailure() {
        throw new UnsupportedOperationException("A Maven deployer cannot be used to resolve dependencies. It can only be used to publish artifacts.");
    }

    public void reportFailure(Artifact art) {
        throw new UnsupportedOperationException("A Maven deployer cannot be used to resolve dependencies. It can only be used to publish artifacts.");
    }

    public String[] listTokenValues(String token, Map otherTokenValues) {
        throw new UnsupportedOperationException("A Maven deployer cannot be used to resolve dependencies. It can only be used to publish artifacts.");
    }

    public Map[] listTokenValues(String[] tokens, Map criteria) {
        throw new UnsupportedOperationException("A Maven deployer cannot be used to resolve dependencies. It can only be used to publish artifacts.");
    }

    public OrganisationEntry[] listOrganisations() {
        throw new UnsupportedOperationException("A Maven deployer cannot be used to resolve dependencies. It can only be used to publish artifacts.");
    }

    public ModuleEntry[] listModules(OrganisationEntry org) {
        throw new UnsupportedOperationException("A Maven deployer cannot be used to resolve dependencies. It can only be used to publish artifacts.");
    }

    public RevisionEntry[] listRevisions(ModuleEntry module) {
        throw new UnsupportedOperationException("A Maven deployer cannot be used to resolve dependencies. It can only be used to publish artifacts.");
    }

    public Namespace getNamespace() {
        throw new UnsupportedOperationException("A Maven deployer cannot be used to resolve dependencies. It can only be used to publish artifacts.");
    }

    public void dumpSettings() {
        throw new UnsupportedOperationException("A Maven deployer cannot be used to resolve dependencies. It can only be used to publish artifacts.");
    }

    public void publish(Artifact artifact, File src, boolean overwrite) throws IOException {
        throw new UnsupportedOperationException("not implemented");
    }

    public void beginPublishTransaction(ModuleRevisionId module, boolean overwrite) throws IOException {
        throw new UnsupportedOperationException("not implemented");
    }

    public void abortPublishTransaction() throws IOException {
        throw new UnsupportedOperationException("not implemented");
    }

    public void commitPublishTransaction() throws IOException {
        throw new UnsupportedOperationException("not implemented");
    }

    public void publish(ModuleVersionPublishMetaData moduleVersion) {
        ModuleVersionIdentifier id = moduleVersion.getId();
        for (ModuleVersionArtifactPublishMetaData artifact : moduleVersion.getArtifacts()) {
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

    public void setSettings(ResolverSettings settings) {
        // do nothing
    }

    public void setSettings(IvySettings settings) {
        // do nothing
    }

    public RepositoryCacheManager getRepositoryCacheManager() {
        return new NoOpRepositoryCacheManager(getName());
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

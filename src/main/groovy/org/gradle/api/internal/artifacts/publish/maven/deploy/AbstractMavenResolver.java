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
package org.gradle.api.internal.artifacts.publish.maven.deploy;

import org.apache.ivy.core.cache.ArtifactOrigin;
import org.apache.ivy.core.cache.DefaultRepositoryCacheManager;
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
import org.apache.ivy.plugins.namespace.Namespace;
import org.apache.ivy.plugins.resolver.ResolverSettings;
import org.apache.ivy.plugins.resolver.util.ResolvedResource;
import org.apache.maven.artifact.ant.InstallDeployTaskSupport;
import org.apache.maven.artifact.ant.Pom;
import org.apache.maven.settings.Settings;
import org.apache.tools.ant.Project;
import org.gradle.api.artifacts.maven.MavenPom;
import org.gradle.api.artifacts.maven.MavenResolver;
import org.gradle.api.artifacts.maven.PomFilterContainer;
import org.gradle.api.artifacts.maven.PublishFilter;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.logging.DefaultStandardOutputCapture;
import org.gradle.api.logging.LogLevel;
import org.gradle.api.logging.StandardOutputCapture;
import org.gradle.util.AntUtil;

import java.io.File;
import java.io.IOException;
import java.text.ParseException;
import java.util.Map;

/**
 * @author Hans Dockter
 */
public abstract class AbstractMavenResolver implements MavenResolver {
    private String name;
    
    private ArtifactPomContainer artifactPomContainer;

    private PomFilterContainer pomFilterContainer;

    private ConfigurationContainer configurationContainer;

    private Settings settings;

    public AbstractMavenResolver(String name, PomFilterContainer pomFilterContainer, ArtifactPomContainer artifactPomContainer, ConfigurationContainer configurationContainer) {
        this.name = name;
        this.pomFilterContainer = pomFilterContainer;
        this.artifactPomContainer = artifactPomContainer;
        this.configurationContainer = configurationContainer;
    }

    protected abstract InstallDeployTaskSupport createPreConfiguredTask(Project project);

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public ConfigurationContainer getConfigurationContainer() {
        return configurationContainer;
    }

    public void setConfigurationContainer(ConfigurationContainer configurationContainer) {
        this.configurationContainer = configurationContainer;
    }
    
    public ResolvedModuleRevision getDependency(DependencyDescriptor dd, ResolveData data) throws ParseException {
        throw new UnsupportedOperationException("A MavenPublishOnlyResolver can only publish artifacts.");
    }

    public ResolvedResource findIvyFileRef(DependencyDescriptor dd, ResolveData data) {
        throw new UnsupportedOperationException("A MavenPublishOnlyResolver can only publish artifacts.");
    }

    public DownloadReport download(Artifact[] artifacts, DownloadOptions options) {
        throw new UnsupportedOperationException("A MavenPublishOnlyResolver can only publish artifacts.");
    }

    public ArtifactDownloadReport download(ArtifactOrigin artifact, DownloadOptions options) {
        throw new UnsupportedOperationException("A MavenPublishOnlyResolver can only publish artifacts.");
    }

    public boolean exists(Artifact artifact) {
        throw new UnsupportedOperationException("A MavenPublishOnlyResolver can only publish artifacts.");
    }

    public ArtifactOrigin locate(Artifact artifact) {
        throw new UnsupportedOperationException("A MavenPublishOnlyResolver can only publish artifacts.");
    }

    public void reportFailure() {
        throw new UnsupportedOperationException("A MavenPublishOnlyResolver can only publish artifacts.");
    }

    public void reportFailure(Artifact art) {
        throw new UnsupportedOperationException("A MavenPublishOnlyResolver can only publish artifacts.");
    }

    public String[] listTokenValues(String token, Map otherTokenValues) {
        throw new UnsupportedOperationException("A MavenPublishOnlyResolver can only publish artifacts.");
    }

    public Map[] listTokenValues(String[] tokens, Map criteria) {
        throw new UnsupportedOperationException("A MavenPublishOnlyResolver can only publish artifacts.");
    }

    public OrganisationEntry[] listOrganisations() {
        throw new UnsupportedOperationException("A MavenPublishOnlyResolver can only publish artifacts.");
    }

    public ModuleEntry[] listModules(OrganisationEntry org) {
        throw new UnsupportedOperationException("A MavenPublishOnlyResolver can only publish artifacts.");
    }

    public RevisionEntry[] listRevisions(ModuleEntry module) {
        throw new UnsupportedOperationException("A MavenPublishOnlyResolver can only publish artifacts.");
    }

    public Namespace getNamespace() {
        throw new UnsupportedOperationException("A MavenPublishOnlyResolver can only publish artifacts.");
    }

    public void dumpSettings() {
        throw new UnsupportedOperationException("A MavenPublishOnlyResolver can only publish artifacts.");
    }


    public void publish(Artifact artifact, File src, boolean overwrite) throws IOException {
        if (isIgnorable(artifact)) {
            return;
        }
        getArtifactPomContainer().addArtifact(artifact, src);
    }

    private boolean isIgnorable(Artifact artifact) {
        return artifact.getType().equals("ivy");
    }

    public void beginPublishTransaction(ModuleRevisionId module, boolean overwrite) throws IOException {
        // do nothing
    }

    public void abortPublishTransaction() throws IOException {
        // do nothing
    }

    public void commitPublishTransaction() throws IOException {
        InstallDeployTaskSupport installDeployTaskSupport = createPreConfiguredTask(AntUtil.createProject());
        Map<File, File> deployableUnits = getArtifactPomContainer().createDeployableUnits(configurationContainer.getAll());
        for (File pomFile : deployableUnits.keySet()) {
            addPomAndArtifact(installDeployTaskSupport, pomFile, deployableUnits.get(pomFile));
            execute(installDeployTaskSupport);
        }
        settings = ((CustomInstallDeployTaskSupport) installDeployTaskSupport).getSettings();
    }

    private void execute(InstallDeployTaskSupport deployTask) {
        StandardOutputCapture outputCapture = new DefaultStandardOutputCapture(true, LogLevel.INFO).start();
        try {
            deployTask.execute();
        } finally {
            outputCapture.stop();
        }
    }

    private void addPomAndArtifact(InstallDeployTaskSupport deployTask, File pomFile, File artifactFile) {
        Pom pom = new Pom();
        pom.setFile(pomFile);
        deployTask.addPom(pom);
        deployTask.setFile(artifactFile);
    }

    public void setSettings(ResolverSettings settings) {
        // do nothing
    }

    public RepositoryCacheManager getRepositoryCacheManager() {
        return new DefaultRepositoryCacheManager();
    }

    public ArtifactPomContainer getArtifactPomContainer() {
        return artifactPomContainer;
    }

    public void setArtifactPomContainer(ArtifactPomContainer artifactPomContainer) {
        this.artifactPomContainer = artifactPomContainer;
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

    public PublishFilter filter(String name) {
        return pomFilterContainer.filter(name);
    }

    public MavenPom pom(String name) {
        return pomFilterContainer.pom(name);
    }

    public Iterable<PomFilter> getActivePomFilters() {
        return pomFilterContainer.getActivePomFilters();
    }

    public PomFilterContainer getPomFilterContainer() {
        return pomFilterContainer;
    }

    public void setPomFilterContainer(PomFilterContainer pomFilterContainer) {
        this.pomFilterContainer = pomFilterContainer;
    }
}

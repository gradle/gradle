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

package org.gradle.api.internal.artifacts.repositories.resolver;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import org.apache.ivy.core.module.descriptor.DependencyArtifactDescriptor;
import org.apache.ivy.core.module.descriptor.DependencyDescriptor;
import org.apache.ivy.core.settings.IvySettings;
import org.apache.ivy.plugins.matcher.PatternMatcher;
import org.gradle.api.Nullable;
import org.gradle.api.Transformer;
import org.gradle.api.artifacts.ModuleIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.artifacts.result.Artifact;
import org.gradle.api.artifacts.result.jvm.JavadocArtifact;
import org.gradle.api.artifacts.result.jvm.SourcesArtifact;
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier;
import org.gradle.api.internal.artifacts.ModuleVersionPublisher;
import org.gradle.api.internal.artifacts.ivyservice.ArtifactType;
import org.gradle.api.internal.artifacts.ivyservice.BuildableArtifactResolveResult;
import org.gradle.api.internal.artifacts.ivyservice.BuildableArtifactSetResolveResult;
import org.gradle.api.internal.artifacts.ivyservice.ComponentUsage;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.*;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser.DescriptorParseContext;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser.MetaDataParseException;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.ResolverStrategy;
import org.gradle.api.internal.artifacts.metadata.*;
import org.gradle.api.internal.externalresource.LocallyAvailableExternalResource;
import org.gradle.api.internal.externalresource.local.LocallyAvailableResourceFinder;
import org.gradle.api.internal.externalresource.transfer.CacheAwareExternalResourceAccessor;
import org.gradle.api.internal.externalresource.transport.ExternalResourceRepository;
import org.gradle.internal.SystemProperties;
import org.gradle.internal.filestore.FileStore;
import org.gradle.util.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public abstract class ExternalResourceResolver implements ModuleVersionPublisher, ConfiguredModuleComponentRepository {
    private static final Logger LOGGER = LoggerFactory.getLogger(ExternalResourceResolver.class);

    private List<ResourcePattern> ivyPatterns = new ArrayList<ResourcePattern>();
    private List<ResourcePattern> artifactPatterns = new ArrayList<ResourcePattern>();
    private boolean checkConsistency = true;
    private boolean allowMissingDescriptor = true;
    private boolean force;
    private String name;
    private String changingMatcherName;
    private String changingPattern;
    private RepositoryChain repositoryChain;

    private final ExternalResourceRepository repository;
    private final boolean local;
    private final CacheAwareExternalResourceAccessor cachingResourceAccessor;
    private final LocallyAvailableResourceFinder<ModuleVersionArtifactMetaData> locallyAvailableResourceFinder;
    private final ResolverStrategy resolverStrategy;
    private final FileStore<ModuleVersionArtifactMetaData> artifactFileStore;

    protected VersionLister versionLister;

    public ExternalResourceResolver(String name,
                                    boolean local,
                                    ExternalResourceRepository repository,
                                    CacheAwareExternalResourceAccessor cachingResourceAccessor,
                                    VersionLister versionLister,
                                    LocallyAvailableResourceFinder<ModuleVersionArtifactMetaData> locallyAvailableResourceFinder,
                                    ResolverStrategy resolverStrategy,
                                    FileStore<ModuleVersionArtifactMetaData> artifactFileStore) {
        this.name = name;
        this.local = local;
        this.cachingResourceAccessor = cachingResourceAccessor;
        this.versionLister = versionLister;
        this.repository = repository;
        this.locallyAvailableResourceFinder = locallyAvailableResourceFinder;
        this.resolverStrategy = resolverStrategy;
        this.artifactFileStore = artifactFileStore;
    }

    public String getId() {
        return DependencyResolverIdentifier.forExternalResourceResolver(this);
    }

    public String getName() {
        return name;
    }

    public boolean canListModuleVersions() {
        return true;
    }

    public void setName(String name) {
        this.name = name;
    }

    public boolean isDynamicResolveMode() {
        return false;
    }

    public String toString() {
        return String.format("Repository '%s'", getName());
    }

    public void setRepositoryChain(RepositoryChain resolver) {
        this.repositoryChain = resolver;
    }

    protected ExternalResourceRepository getRepository() {
        return repository;
    }

    public boolean isLocal() {
        return local;
    }

    private void doListModuleVersions(DependencyMetaData dependency, BuildableModuleVersionSelectionResolveResult result) {
        ModuleIdentifier module  = new DefaultModuleIdentifier(dependency.getRequested().getGroup(), dependency.getRequested().getName());
        VersionList versionList = versionLister.getVersionList(module);

        // List modules based on metadata files (artifact version is not considered in listVersionsForAllPatterns())
        IvyArtifactName metaDataArtifact = getMetaDataArtifactName(dependency.getRequested().getName());
        if (metaDataArtifact != null) {
            listVersionsForAllPatterns(ivyPatterns, metaDataArtifact, versionList);
        }

        // List modules with missing metadata files
        if (isAllownomd()) {
            for (IvyArtifactName otherArtifact : getDependencyArtifactNames(dependency)) {
                listVersionsForAllPatterns(artifactPatterns, otherArtifact, versionList);
            }
        }
        DefaultModuleVersionListing moduleVersions = new DefaultModuleVersionListing();
        for (VersionList.ListedVersion listedVersion : versionList.getVersions()) {
            moduleVersions.add(listedVersion.getVersion());
        }
        result.listed(moduleVersions);
    }

    private void listVersionsForAllPatterns(List<ResourcePattern> patternList, IvyArtifactName ivyArtifactName, VersionList versionList) {
        for (ResourcePattern resourcePattern : patternList) {
            versionList.visit(resourcePattern, ivyArtifactName);
        }
    }

    protected void doResolveComponentMetaData(DependencyMetaData dependency, ModuleComponentIdentifier moduleComponentIdentifier, BuildableModuleVersionMetaDataResolveResult result) {
        resolveStaticDependency(dependency, moduleComponentIdentifier, result, createArtifactResolver());
    }

    protected final void resolveStaticDependency(DependencyMetaData dependency, ModuleComponentIdentifier moduleVersionIdentifier, BuildableModuleVersionMetaDataResolveResult result, ExternalResourceArtifactResolver artifactResolver) {
        MutableModuleVersionMetaData metaDataArtifactMetaData = parseMetaDataFromArtifact(moduleVersionIdentifier, artifactResolver);
        if (metaDataArtifactMetaData != null) {
            LOGGER.debug("Metadata file found for module '{}' in repository '{}'.", moduleVersionIdentifier, getName());
            result.resolved(metaDataArtifactMetaData, null);
            return;
        }

        if (isAllownomd()) {
            MutableModuleVersionMetaData metaDataFromDefaultArtifact = createMetaDataFromDefaultArtifact(moduleVersionIdentifier, dependency, artifactResolver);
            if (metaDataFromDefaultArtifact != null) {
                LOGGER.debug("Found artifact but no meta-data for module '{}' in repository '{}', using default meta-data.", moduleVersionIdentifier, getName());
                result.resolved(metaDataFromDefaultArtifact, null);
                return;
            }
        }

        LOGGER.debug("No meta-data file or artifact found for module '{}' in repository '{}'.", moduleVersionIdentifier, getName());
        result.missing();
    }

    protected MutableModuleVersionMetaData parseMetaDataFromArtifact(ModuleComponentIdentifier moduleVersionIdentifier, ExternalResourceArtifactResolver artifactResolver) {
        ModuleVersionArtifactMetaData artifact = getMetaDataArtifactFor(moduleVersionIdentifier);
        if (artifact == null) {
            return null;
        }
        LocallyAvailableExternalResource metaDataResource = artifactResolver.resolveMetaDataArtifact(artifact);
        if (metaDataResource == null) {
            return null;
        }

        ExternalResourceResolverDescriptorParseContext context = new ExternalResourceResolverDescriptorParseContext(repositoryChain);
        MutableModuleVersionMetaData metaData = parseMetaDataFromResource(metaDataResource, context);
        metaData = processMetaData(metaData);

        if (isCheckconsistency()) {
            checkMetadataConsistency(moduleVersionIdentifier, metaData);
        }
        return metaData;
    }

    private MutableModuleVersionMetaData createMetaDataFromDefaultArtifact(ModuleComponentIdentifier moduleVersionIdentifier, DependencyMetaData dependency, ExternalResourceArtifactResolver artifactResolver) {
        for (IvyArtifactName artifact : getDependencyArtifactNames(dependency)) {
            if (artifactResolver.artifactExists(new DefaultModuleVersionArtifactMetaData(moduleVersionIdentifier, artifact))) {
                MutableModuleVersionMetaData metaData = createMetaDataForDependency(dependency);
                return processMetaData(metaData);
            }
        }
        return null;
    }

    protected abstract MutableModuleVersionMetaData createMetaDataForDependency(DependencyMetaData dependency);

    protected abstract MutableModuleVersionMetaData parseMetaDataFromResource(LocallyAvailableExternalResource cachedResource, DescriptorParseContext context);

    private Set<IvyArtifactName> getDependencyArtifactNames(DependencyMetaData dependency) {
        String moduleName = dependency.getRequested().getName();
        Set<IvyArtifactName> artifactSet = Sets.newLinkedHashSet();
        DependencyDescriptor dependencyDescriptor = dependency.getDescriptor();
        for (DependencyArtifactDescriptor artifact : dependencyDescriptor.getAllDependencyArtifacts()) {
            artifactSet.add(new DefaultIvyArtifactName(moduleName, artifact.getType(), artifact.getExt(), artifact.getExtraAttributes()));
        }

        if (artifactSet.isEmpty()) {
            artifactSet.add(new DefaultIvyArtifactName(moduleName, "jar", "jar", Collections.<String, String>emptyMap()));
        }

        return artifactSet;
    }

    private MutableModuleVersionMetaData processMetaData(MutableModuleVersionMetaData metaData) {
        metaData.setChanging(isChanging(metaData.getId().getVersion()));
        return metaData;
    }

    private void checkMetadataConsistency(ModuleComponentIdentifier expectedId, ModuleVersionMetaData metadata) throws MetaDataParseException {
        List<String> errors = new ArrayList<String>();
        if (!expectedId.getGroup().equals(metadata.getId().getGroup())) {
            errors.add("bad group: expected='" + expectedId.getGroup() + "' found='" + metadata.getId().getGroup() + "'");
        }
        if (!expectedId.getModule().equals(metadata.getId().getName())) {
            errors.add("bad module name: expected='" + expectedId.getModule() + "' found='" + metadata.getId().getName() + "'");
        }
        if (!expectedId.getVersion().equals(metadata.getId().getVersion())) {
            errors.add("bad version: expected='" + expectedId.getVersion() + "' found='" + metadata.getId().getVersion() + "'");
        }
        if (errors.size() > 0) {
            throw new MetaDataParseException(String.format("inconsistent module metadata found. Descriptor: %s Errors: %s",
                    metadata.getId(), Joiner.on(SystemProperties.getLineSeparator()).join(errors)));
        }
    }

    protected abstract boolean isMetaDataArtifact(Class<? extends Artifact> artifactType);

    protected Set<ModuleVersionArtifactMetaData> findOptionalArtifacts(ModuleVersionMetaData module, String type, String classifier) {
        ModuleVersionArtifactMetaData artifact = module.artifact(type, "jar", classifier);
        if (createArtifactResolver(module.getSource()).artifactExists(artifact)) {
            return ImmutableSet.of(artifact);
        }
        return Collections.emptySet();
    }

    private ModuleVersionArtifactMetaData getMetaDataArtifactFor(ModuleComponentIdentifier moduleComponentIdentifier) {
        IvyArtifactName ivyArtifactName = getMetaDataArtifactName(moduleComponentIdentifier.getModule());
        if (ivyArtifactName == null) {
            return null;
        }
        return new DefaultModuleVersionArtifactMetaData(moduleComponentIdentifier, ivyArtifactName);
    }

    // TODO This will no longer be @Nullable in Gradle 2.0 (when we remove the ability to call setUsePoms(false) on MavenResolver)
    @Nullable
    protected abstract IvyArtifactName getMetaDataArtifactName(String moduleName);

    public void resolveArtifact(ComponentArtifactMetaData componentArtifact, ModuleSource moduleSource, BuildableArtifactResolveResult result) {
        ModuleVersionArtifactMetaData artifact = (ModuleVersionArtifactMetaData) componentArtifact;

        File localFile;
        try {
            localFile = download(artifact, moduleSource);
        } catch (Throwable e) {
            result.failed(new ArtifactResolveException(artifact.getId(), e));
            return;
        }

        if (localFile != null) {
            result.resolved(localFile);
        } else {
            result.notFound(artifact.getId());
        }
    }

    protected File download(ModuleVersionArtifactMetaData artifact, ModuleSource moduleSource) {
        return downloadArtifact(artifact, createArtifactResolver(moduleSource));
    }

    protected File downloadArtifact(ModuleVersionArtifactMetaData artifact, ExternalResourceArtifactResolver artifactResolver) {
        LocallyAvailableExternalResource artifactResource = artifactResolver.resolveArtifact(artifact);
        if (artifactResource == null) {
            return null;
        }

        return artifactResource.getLocalResource().getFile();
    }

    protected ExternalResourceArtifactResolver createArtifactResolver() {
        return createArtifactResolver(ivyPatterns, artifactPatterns);
    }

    protected ExternalResourceArtifactResolver createArtifactResolver(List<ResourcePattern> ivyPatterns, List<ResourcePattern> artifactPatterns) {
        return new DefaultExternalResourceArtifactResolver(repository, locallyAvailableResourceFinder, ivyPatterns, artifactPatterns, artifactFileStore, cachingResourceAccessor);
    }

    protected ExternalResourceArtifactResolver createArtifactResolver(ModuleSource moduleSource) {
        return createArtifactResolver();
    }

    public void setSettings(IvySettings settings) {
    }

    public void publish(ModuleVersionPublishMetaData moduleVersion) throws IOException {
        for (ModuleVersionArtifactPublishMetaData artifact : moduleVersion.getArtifacts()) {
            publish(new DefaultModuleVersionArtifactMetaData(artifact.getId()), artifact.getFile());
        }
    }

    private void publish(ModuleVersionArtifactMetaData artifact, File src) throws IOException {
        ResourcePattern destinationPattern;
        if ("ivy".equals(artifact.getName().getType()) && !ivyPatterns.isEmpty()) {
            destinationPattern = ivyPatterns.get(0);
        } else if (!artifactPatterns.isEmpty()) {
            destinationPattern = artifactPatterns.get(0);
        } else {
            throw new IllegalStateException("impossible to publish " + artifact + " using " + this + ": no artifact pattern defined");
        }
        URI destination = destinationPattern.getLocation(artifact);

        put(src, destination);
        LOGGER.info("Published {} to {}", artifact, destination);
    }

    private void put(File src, URI destination) throws IOException {
        repository.put(src, destination);
    }

    protected void addIvyPattern(ResourcePattern pattern) {
        ivyPatterns.add(pattern);
    }

    protected void addArtifactPattern(ResourcePattern pattern) {
        artifactPatterns.add(pattern);
    }

    public List<String> getIvyPatterns() {
        return CollectionUtils.collect(ivyPatterns, new Transformer<String, ResourcePattern>() {
            public String transform(ResourcePattern original) {
                return original.getPattern();
            }
        });
    }

    public List<String> getArtifactPatterns() {
        return CollectionUtils.collect(artifactPatterns, new Transformer<String, ResourcePattern>() {
            public String transform(ResourcePattern original) {
                return original.getPattern();
            }
        });
    }

    protected void setIvyPatterns(Iterable<? extends ResourcePattern> patterns) {
        ivyPatterns.clear();
        CollectionUtils.addAll(ivyPatterns, patterns);
    }

    protected void setArtifactPatterns(List<ResourcePattern> patterns) {
        artifactPatterns = patterns;
    }

    public abstract boolean isM2compatible();

    public boolean isCheckconsistency() {
        return checkConsistency;
    }

    public void setCheckconsistency(boolean checkConsistency) {
        this.checkConsistency = checkConsistency;
    }

    public void setForce(boolean force) {
        this.force = force;
    }

    public boolean isForce() {
        return force;
    }

    public boolean isAllownomd() {
        return allowMissingDescriptor;
    }

    public void setAllownomd(boolean allowMissingDescriptor) {
        this.allowMissingDescriptor = allowMissingDescriptor;
    }

    public String getChangingMatcherName() {
        return changingMatcherName;
    }

    public void setChangingMatcher(String changingMatcherName) {
        this.changingMatcherName = changingMatcherName;
    }

    public String getChangingPattern() {
        return changingPattern;
    }

    public void setChangingPattern(String changingPattern) {
        this.changingPattern = changingPattern;
    }

    private boolean isChanging(String version) {
        if (changingMatcherName == null || changingPattern == null) {
            return false;
        }
        PatternMatcher matcher = resolverStrategy.getPatternMatcher(changingMatcherName);
        if (matcher == null) {
            throw new IllegalStateException("unknown matcher '" + changingMatcherName
                    + "'. It is set as changing matcher in " + this);
        }
        return matcher.getMatcher(changingPattern).matches(version);
    }

    protected abstract class AbstractRepositoryAccess implements ModuleComponentRepositoryAccess {
        public void resolveModuleArtifacts(ComponentMetaData component, ArtifactType artifactType, BuildableArtifactSetResolveResult result) {
            ModuleVersionMetaData moduleMetaData = (ModuleVersionMetaData) component;

            if (artifactType.getType() == JavadocArtifact.class) {
                resolveJavadocArtifacts(moduleMetaData, result);
            } else if (artifactType.getType() == SourcesArtifact.class) {
                resolveSourceArtifacts(moduleMetaData, result);
            } else if (isMetaDataArtifact(artifactType.getType())) {
                resolveMetaDataArtifacts(moduleMetaData, result);
            }
        }

        public void resolveModuleArtifacts(ComponentMetaData component, ComponentUsage componentUsage, BuildableArtifactSetResolveResult result) {
            String configurationName = componentUsage.getConfigurationName();
             ConfigurationMetaData configuration = component.getConfiguration(configurationName);
             resolveConfigurationArtifacts((ModuleVersionMetaData) component, configuration, result);
        }

        protected abstract void resolveConfigurationArtifacts(ModuleVersionMetaData module, ConfigurationMetaData configuration, BuildableArtifactSetResolveResult result);

        protected abstract void resolveMetaDataArtifacts(ModuleVersionMetaData module, BuildableArtifactSetResolveResult result);

        protected abstract void resolveJavadocArtifacts(ModuleVersionMetaData module, BuildableArtifactSetResolveResult result);

        protected abstract void resolveSourceArtifacts(ModuleVersionMetaData module, BuildableArtifactSetResolveResult result);

    }

    protected abstract class LocalRepositoryAccess extends AbstractRepositoryAccess {
        public final void listModuleVersions(DependencyMetaData dependency, BuildableModuleVersionSelectionResolveResult result) {
        }

        public final void resolveComponentMetaData(DependencyMetaData dependency, ModuleComponentIdentifier moduleComponentIdentifier, BuildableModuleVersionMetaDataResolveResult result) {
        }

        protected final void resolveMetaDataArtifacts(ModuleVersionMetaData module, BuildableArtifactSetResolveResult result) {
            ModuleVersionArtifactMetaData artifact = getMetaDataArtifactFor(module.getComponentId());
            if (artifact != null) {
                result.resolved(Collections.singleton(artifact));
            } else {
                result.resolved(Collections.<ComponentArtifactMetaData>emptySet());
            }
        }
    }

    protected abstract class RemoteRepositoryAccess extends AbstractRepositoryAccess {
        public final void listModuleVersions(DependencyMetaData dependency, BuildableModuleVersionSelectionResolveResult result) {
            doListModuleVersions(dependency, result);
        }

        public final void resolveComponentMetaData(DependencyMetaData dependency, ModuleComponentIdentifier moduleComponentIdentifier, BuildableModuleVersionMetaDataResolveResult result) {
            doResolveComponentMetaData(dependency, moduleComponentIdentifier, result);
        }

        @Override
        public void resolveModuleArtifacts(ComponentMetaData component, ArtifactType artifactType, BuildableArtifactSetResolveResult result) {
            super.resolveModuleArtifacts(component, artifactType, result);
            checkArtifactsResolved(component, artifactType, result);
        }

        @Override
        public void resolveModuleArtifacts(ComponentMetaData component, ComponentUsage componentUsage, BuildableArtifactSetResolveResult result) {
            super.resolveModuleArtifacts(component, componentUsage, result);
            checkArtifactsResolved(component, componentUsage, result);
        }

        private void checkArtifactsResolved(ComponentMetaData component, Object context, BuildableArtifactSetResolveResult result) {
            if (!result.hasResult()) {
                result.failed(new ArtifactResolveException(component.getComponentId(),
                        String.format("Cannot locate %s for '%s' in repository '%s'", context, component, name)));
            }
        }

        protected final void resolveMetaDataArtifacts(ModuleVersionMetaData module, BuildableArtifactSetResolveResult result) {
            // Meta data  artifacts are determined locally
        }
    }
}

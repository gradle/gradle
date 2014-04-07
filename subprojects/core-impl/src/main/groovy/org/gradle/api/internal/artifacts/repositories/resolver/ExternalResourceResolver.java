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
import org.gradle.api.UncheckedIOException;
import org.gradle.api.artifacts.ModuleIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.artifacts.result.Artifact;
import org.gradle.api.artifacts.result.jvm.JvmLibraryJavadocArtifact;
import org.gradle.api.artifacts.result.jvm.JvmLibrarySourcesArtifact;
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier;
import org.gradle.api.internal.artifacts.ModuleVersionPublisher;
import org.gradle.api.internal.artifacts.ivyservice.*;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.*;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser.MetaDataParseException;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser.MetaDataParser;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.ResolverStrategy;
import org.gradle.api.internal.artifacts.metadata.*;
import org.gradle.api.internal.artifacts.repositories.cachemanager.RepositoryArtifactCache;
import org.gradle.api.internal.externalresource.ExternalResource;
import org.gradle.api.internal.externalresource.LocallyAvailableExternalResource;
import org.gradle.api.internal.externalresource.local.LocallyAvailableResourceFinder;
import org.gradle.api.internal.externalresource.transport.ExternalResourceRepository;
import org.gradle.internal.SystemProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.gradle.api.internal.artifacts.repositories.cachemanager.RepositoryArtifactCache.ExternalResourceDownloader;

public abstract class ExternalResourceResolver implements ModuleVersionPublisher, ConfiguredModuleComponentRepository {
    private static final Logger LOGGER = LoggerFactory.getLogger(ExternalResourceResolver.class);

    private final MetaDataParser metaDataParser;

    private List<String> ivyPatterns = new ArrayList<String>();
    private List<String> artifactPatterns = new ArrayList<String>();
    private boolean m2Compatible;
    private boolean checkConsistency = true;
    private boolean allowMissingDescriptor = true;
    private boolean force;
    private String checksums;
    private String name;
    private RepositoryArtifactCache repositoryCacheManager;
    private String changingMatcherName;
    private String changingPattern;
    private RepositoryChain repositoryChain;

    private final ExternalResourceRepository repository;
    private final LocallyAvailableResourceFinder<ModuleVersionArtifactMetaData> locallyAvailableResourceFinder;
    private final ResolverStrategy resolverStrategy;

    protected VersionLister versionLister;


    public ExternalResourceResolver(String name,
                                    ExternalResourceRepository repository,
                                    VersionLister versionLister,
                                    LocallyAvailableResourceFinder<ModuleVersionArtifactMetaData> locallyAvailableResourceFinder,
                                    MetaDataParser metaDataParser,
                                    ResolverStrategy resolverStrategy) {
        this.name = name;
        this.versionLister = versionLister;
        this.repository = repository;
        this.locallyAvailableResourceFinder = locallyAvailableResourceFinder;
        this.metaDataParser = metaDataParser;
        this.resolverStrategy = resolverStrategy;
    }

    public String getId() {
        return DependencyResolverIdentifier.forExternalResourceResolver(this);
    }

    public String getName() {
        return name;
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
        return repositoryCacheManager.isLocal();
    }

    private void doListModuleVersions(DependencyMetaData dependency, BuildableModuleVersionSelectionResolveResult result) {
        ModuleIdentifier module  = new DefaultModuleIdentifier(dependency.getRequested().getGroup(), dependency.getRequested().getName());
        VersionList versionList = versionLister.getVersionList(module);

        // List modules based on metadata files (artifact version is not considered in listVersionsForAllPatterns())
        IvyArtifactName metaDataArtifact = getMetaDataArtifactName(dependency.getRequested().getName());
        listVersionsForAllPatterns(getIvyPatterns(), metaDataArtifact, versionList);

        // List modules with missing metadata files
        if (isAllownomd()) {
            for (IvyArtifactName otherArtifact : getDependencyArtifactNames(dependency)) {
                listVersionsForAllPatterns(getArtifactPatterns(), otherArtifact, versionList);
            }
        }
        DefaultModuleVersionListing moduleVersions = new DefaultModuleVersionListing();
        for (VersionList.ListedVersion listedVersion : versionList.getVersions()) {
            moduleVersions.add(listedVersion.getVersion());
        }
        result.listed(moduleVersions);
    }

    private void listVersionsForAllPatterns(List<String> patternList, IvyArtifactName ivyArtifactName, VersionList versionList) {
        for (String pattern : patternList) {
            ResourcePattern resourcePattern = toResourcePattern(pattern);
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
        ExternalResource metaDataResource = artifactResolver.resolveMetaDataArtifact(artifact);
        if (metaDataResource == null) {
            return null;
        }
        MutableModuleVersionMetaData moduleVersionMetaData = downloadAndParseMetaDataArtifact(artifact, metaDataResource);

        if (isCheckconsistency()) {
            checkMetadataConsistency(moduleVersionIdentifier, moduleVersionMetaData);
        }
        return moduleVersionMetaData;
    }

    private MutableModuleVersionMetaData downloadAndParseMetaDataArtifact(ModuleVersionArtifactMetaData artifact, ExternalResource resource) {
        ExternalResourceResolverDescriptorParseContext context = new ExternalResourceResolverDescriptorParseContext(repositoryChain);
        LocallyAvailableExternalResource cachedResource;
        try {
            cachedResource = downloadAndCacheResource(artifact, resource);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        MutableModuleVersionMetaData metaData =  metaDataParser.parseMetaData(context, cachedResource);
        return processMetaData(metaData);
    }

    private MutableModuleVersionMetaData createMetaDataFromDefaultArtifact(ModuleComponentIdentifier moduleVersionIdentifier, DependencyMetaData dependency, ExternalResourceArtifactResolver artifactResolver) {
        for (IvyArtifactName artifact : getDependencyArtifactNames(dependency)) {
            if (artifactResolver.artifactExists(new DefaultModuleVersionArtifactMetaData(moduleVersionIdentifier, artifact))) {
                return processMetaData(getDefaultForDependency(dependency));
            }
        }
        return null;
    }

    protected MutableModuleVersionMetaData getDefaultForDependency(DependencyMetaData dependencyMetaData) {
        return ModuleDescriptorAdapter.defaultForDependency(dependencyMetaData);
    }

    private Set<IvyArtifactName> getDependencyArtifactNames(DependencyMetaData dependency) {
        String moduleName = dependency.getRequested().getName();
        Set<IvyArtifactName> artifactSet = Sets.newLinkedHashSet();
        DependencyDescriptor dependencyDescriptor = dependency.getDescriptor();
        for (DependencyArtifactDescriptor artifact : dependencyDescriptor.getAllDependencyArtifacts()) {
            artifactSet.add(new DefaultIvyArtifactName(moduleName, artifact.getType(), artifact.getExt(), artifact.getExtraAttributes()));
        }

        // TODO:DAZ This logic should be within the DependencyMetaData
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
    
    @Nullable
    protected abstract IvyArtifactName getMetaDataArtifactName(String moduleName);

    private LocallyAvailableExternalResource downloadAndCacheResource(ModuleVersionArtifactMetaData artifact, ExternalResource resource) throws IOException {
        final ExternalResourceDownloader resourceDownloader = new VerifyingExternalResourceDownloader(getChecksumAlgorithms(), getRepository());
        return repositoryCacheManager.downloadAndCacheArtifactFile(artifact, resourceDownloader, resource);
    }

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

    protected File download(ModuleVersionArtifactMetaData artifact, ModuleSource moduleSource) throws IOException {
        return downloadArtifact(artifact, createArtifactResolver(moduleSource));
    }

    protected File downloadArtifact(ModuleVersionArtifactMetaData artifact, ExternalResourceArtifactResolver artifactResolver) throws IOException {
        ExternalResource artifactResource = artifactResolver.resolveArtifact(artifact);
        if (artifactResource == null) {
            return null;
        }

        return downloadAndCacheResource(artifact, artifactResource).getLocalResource().getFile();
    }

    protected ExternalResourceArtifactResolver createArtifactResolver() {
        return createArtifactResolver(getIvyPatterns(), getArtifactPatterns());
    }

    protected ExternalResourceArtifactResolver createArtifactResolver(List<String> ivyPatterns, List<String> artifactPatterns) {
        return new DefaultExternalResourceArtifactResolver(getRepository(), locallyAvailableResourceFinder, ivyPatterns, artifactPatterns, isM2compatible());
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
        String destinationPattern;
        if ("ivy".equals(artifact.getName().getType()) && !getIvyPatterns().isEmpty()) {
            destinationPattern = getIvyPatterns().get(0);
        } else if (!getArtifactPatterns().isEmpty()) {
            destinationPattern = getArtifactPatterns().get(0);
        } else {
            throw new IllegalStateException("impossible to publish " + artifact + " using " + this + ": no artifact pattern defined");
        }
        String destination = toResourcePattern(destinationPattern).toPath(artifact);

        put(src, destination);
        LOGGER.info("Published {} to {}", artifact, destination);
    }

    private void put(File src, String destination) throws IOException {
        String[] checksums = getChecksumAlgorithms();
        if (checksums.length != 0) {
            // Should not be reachable for publishing
            throw new UnsupportedOperationException();
        }

        repository.put(src, destination);
    }

    public void addIvyPattern(String pattern) {
        ivyPatterns.add(pattern);
    }

    public void addArtifactPattern(String pattern) {
        artifactPatterns.add(pattern);
    }

    public List<String> getIvyPatterns() {
        return Collections.unmodifiableList(ivyPatterns);
    }

    public List<String> getArtifactPatterns() {
        return Collections.unmodifiableList(artifactPatterns);
    }

    protected void setIvyPatterns(List<String> patterns) {
        ivyPatterns = patterns;
    }

    protected void setArtifactPatterns(List<String> patterns) {
        artifactPatterns = patterns;
    }

    public boolean isM2compatible() {
        return m2Compatible;
    }

    public void setM2compatible(boolean compatible) {
        m2Compatible = compatible;
    }

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

    public String[] getChecksumAlgorithms() {
        if (checksums == null) {
            return new String[0];
        }
        // csDef is a comma separated list of checksum algorithms to use with this resolver
        // we parse and return it as a String[]
        String[] checksums = this.checksums.split(",");
        List<String> algos = new ArrayList<String>();
        for (int i = 0; i < checksums.length; i++) {
            String cs = checksums[i].trim();
            if (!"".equals(cs) && !"none".equals(cs)) {
                algos.add(cs);
            }
        }
        return algos.toArray(new String[algos.size()]);
    }

    public void setChecksums(String checksums) {
        this.checksums = checksums;
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

    public void setRepositoryCacheManager(RepositoryArtifactCache repositoryCacheManager) {
        this.repositoryCacheManager = repositoryCacheManager;
    }

    protected ResourcePattern toResourcePattern(String pattern) {
        return isM2compatible() ? new M2ResourcePattern(pattern) : new IvyResourcePattern(pattern);
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

            if (artifactType.getType() == JvmLibraryJavadocArtifact.class) {
                resolveJavadocArtifacts(moduleMetaData, result);
            } else if (artifactType.getType() == JvmLibrarySourcesArtifact.class) {
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

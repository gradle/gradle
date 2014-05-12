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

import com.google.common.collect.ImmutableSet;
import org.apache.ivy.plugins.matcher.PatternMatcher;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.artifacts.result.Artifact;
import org.gradle.api.internal.artifacts.ivyservice.BuildableArtifactSetResolveResult;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.BuildableModuleVersionMetaDataResolveResult;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ModuleComponentRepositoryAccess;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ModuleSource;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser.DescriptorParseContext;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser.GradlePomModuleDescriptorParser;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser.MetaDataParser;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.ResolverStrategy;
import org.gradle.api.internal.artifacts.metadata.*;
import org.gradle.api.internal.artifacts.repositories.transport.RepositoryTransport;
import org.gradle.api.internal.artifacts.result.metadata.MavenPomArtifact;
import org.gradle.internal.resource.LocallyAvailableExternalResource;
import org.gradle.internal.resource.local.LocallyAvailableResourceFinder;
import org.gradle.internal.resource.ResourceNotFoundException;
import org.gradle.api.resources.ResourceException;
import org.gradle.internal.Transformers;
import org.gradle.internal.resource.local.FileStore;
import org.gradle.util.DeprecationLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.*;

public class MavenResolver extends ExternalResourceResolver {
    private static final Logger LOGGER = LoggerFactory.getLogger(MavenResolver.class);
    private final URI root;
    private final List<URI> artifactRoots = new ArrayList<URI>();
    private String pattern = MavenPattern.M2_PATTERN;
    private boolean usepoms = true;
    private boolean useMavenMetadata = true;
    private final MavenMetadataLoader mavenMetaDataLoader;
    private final MetaDataParser metaDataParser;

    public MavenResolver(String name, URI rootUri, RepositoryTransport transport,
                         LocallyAvailableResourceFinder<ModuleVersionArtifactMetaData> locallyAvailableResourceFinder,
                         ResolverStrategy resolverStrategy, FileStore<ModuleVersionArtifactMetaData> artifactFileStore) {
        super(name, transport.isLocal(),
                transport.getRepository(),
                transport.getResourceAccessor(),
                new ChainedVersionLister(new MavenVersionLister(transport.getRepository()), new ResourceVersionLister(transport.getRepository())),
                locallyAvailableResourceFinder,
                resolverStrategy,
                artifactFileStore);
        this.metaDataParser = new GradlePomModuleDescriptorParser();
        this.mavenMetaDataLoader = new MavenMetadataLoader(transport.getRepository());
        this.root = rootUri;

        // SNAPSHOT revisions are changing revisions
        setChangingMatcher(PatternMatcher.REGEXP);
        setChangingPattern(".*-SNAPSHOT");

        updatePatterns();
    }

    public String getRoot() {
        return root.toString();
    }

    protected void doResolveComponentMetaData(DependencyMetaData dependency, ModuleComponentIdentifier moduleComponentIdentifier, BuildableModuleVersionMetaDataResolveResult result) {
        if (isSnapshotVersion(moduleComponentIdentifier)) {
            final MavenUniqueSnapshotModuleSource uniqueSnapshotVersion = findUniqueSnapshotVersion(moduleComponentIdentifier);
            if (uniqueSnapshotVersion != null) {
                resolveUniqueSnapshotDependency(dependency, moduleComponentIdentifier, result, uniqueSnapshotVersion);
                return;
            }
        }

        resolveStaticDependency(dependency, moduleComponentIdentifier, result, super.createArtifactResolver());
    }

    @Override
    protected boolean isMetaDataArtifact(Class<? extends Artifact> artifactType) {
        return artifactType == MavenPomArtifact.class;
    }

    private void resolveUniqueSnapshotDependency(DependencyMetaData dependency, ModuleComponentIdentifier module, BuildableModuleVersionMetaDataResolveResult result, MavenUniqueSnapshotModuleSource snapshotSource) {
        resolveStaticDependency(dependency, module, result, createArtifactResolver(snapshotSource));
        if (result.getState() == BuildableModuleVersionMetaDataResolveResult.State.Resolved) {
            result.setModuleSource(snapshotSource);
        }
    }

    private boolean isSnapshotVersion(ModuleComponentIdentifier module) {
        return module.getVersion().endsWith("SNAPSHOT");
    }

    @Override
    protected ExternalResourceArtifactResolver createArtifactResolver(ModuleSource moduleSource) {

        if (moduleSource instanceof MavenUniqueSnapshotModuleSource) {
            final String timestamp = ((MavenUniqueSnapshotModuleSource) moduleSource).getTimestamp();
            return new MavenUniqueSnapshotExternalResourceArtifactResolver(super.createArtifactResolver(moduleSource), timestamp);
        }

        return super.createArtifactResolver(moduleSource);
    }

    public void addArtifactLocation(URI baseUri) {
        artifactRoots.add(baseUri);
        updatePatterns();
    }

    private M2ResourcePattern getWholePattern() {
        return new M2ResourcePattern(root, pattern);
    }

    private void updatePatterns() {
        if (isUsepoms()) {
            setIvyPatterns(Collections.singletonList(getWholePattern()));
        } else {
            setIvyPatterns(Collections.<ResourcePattern>emptyList());
        }

        List<ResourcePattern> artifactPatterns = new ArrayList<ResourcePattern>();
        artifactPatterns.add(getWholePattern());
        for (URI artifactRoot : artifactRoots) {
            artifactPatterns.add(new M2ResourcePattern(artifactRoot, pattern));
        }
        setArtifactPatterns(artifactPatterns);
    }

    @Override
    protected IvyArtifactName getMetaDataArtifactName(String moduleName) {
        if (isUsepoms()) {
            return new DefaultIvyArtifactName(moduleName, "pom", "pom");
        }

        return null;
    }

    private MavenUniqueSnapshotModuleSource findUniqueSnapshotVersion(ModuleComponentIdentifier module) {
        String metadataLocation = getWholePattern().toModuleVersionPath(module).resolve("maven-metadata.xml").getUri().toString();
        MavenMetadata mavenMetadata = parseMavenMetadata(metadataLocation);

        if (mavenMetadata.timestamp != null) {
            // we have found a timestamp, so this is a snapshot unique version
            String timestamp = String.format("%s-%s", mavenMetadata.timestamp, mavenMetadata.buildNumber);
            return new MavenUniqueSnapshotModuleSource(timestamp);
        }
        return null;
    }

    private MavenMetadata parseMavenMetadata(String metadataLocation) {
        if (isUseMavenMetadata()) {
            try {
                return mavenMetaDataLoader.load(metadataLocation);
            } catch (ResourceNotFoundException e) {
                return new MavenMetadata();
            } catch (ResourceException e) {
                LOGGER.warn("impossible to access Maven metadata file, ignored.", e);
            }
        }
        return new MavenMetadata();
    }

    // A bunch of configuration properties that we don't (yet) support in our model via the DSL. Users can still tweak these on the resolver using mavenRepo().
    public boolean isUsepoms() {
        return usepoms;
    }

    public void setUsepoms(boolean usepoms) {
        this.usepoms = usepoms;
        updatePatterns();
    }

    public boolean isUseMavenMetadata() {
        return useMavenMetadata;
    }

    @Deprecated
    public void setUseMavenMetadata(boolean useMavenMetadata) {
        DeprecationLogger.nagUserOfDiscontinuedMethod("MavenResolver.setUseMavenMetadata(boolean)");
        this.useMavenMetadata = useMavenMetadata;
        if (useMavenMetadata) {
            this.versionLister = new ChainedVersionLister(
                    new MavenVersionLister(getRepository()),
                    new ResourceVersionLister(getRepository()));
        } else {
            this.versionLister = new ResourceVersionLister(getRepository());
        }
    }

    @Override
    public boolean isM2compatible() {
        return true;
    }

    public ModuleComponentRepositoryAccess getLocalAccess() {
        return new MavenLocalRepositoryAccess();
    }

    public ModuleComponentRepositoryAccess getRemoteAccess() {
        return new MavenRemoteRepositoryAccess();
    }

    @Override
    protected MutableModuleVersionMetaData createMetaDataForDependency(DependencyMetaData dependency) {
        return new DefaultMavenModuleVersionMetaData(dependency);
    }

    protected MutableModuleVersionMetaData parseMetaDataFromResource(LocallyAvailableExternalResource cachedResource, DescriptorParseContext context) {
        return metaDataParser.parseMetaData(context, cachedResource);
    }

    protected static MavenModuleVersionMetaData mavenMetaData(ModuleVersionMetaData metaData) {
        return Transformers.cast(MavenModuleVersionMetaData.class).transform(metaData);
    }

    private class MavenLocalRepositoryAccess extends LocalRepositoryAccess {
        @Override
        protected void resolveConfigurationArtifacts(ModuleVersionMetaData module, ConfigurationMetaData configuration, BuildableArtifactSetResolveResult result) {
            if (mavenMetaData(module).isKnownJarPackaging()) {
                ModuleVersionArtifactMetaData artifact = module.artifact("jar", "jar", null);
                result.resolved(ImmutableSet.of(artifact));
            }
        }

        @Override
        protected void resolveJavadocArtifacts(ModuleVersionMetaData module, BuildableArtifactSetResolveResult result) {
            // Javadoc artifacts are optional, so we need to probe for them remotely
        }

        @Override
        protected void resolveSourceArtifacts(ModuleVersionMetaData module, BuildableArtifactSetResolveResult result) {
            // Javadoc artifacts are optional, so we need to probe for them remotely
        }
    }

    private class MavenRemoteRepositoryAccess extends RemoteRepositoryAccess {
        @Override
        protected void resolveConfigurationArtifacts(ModuleVersionMetaData module, ConfigurationMetaData configuration, BuildableArtifactSetResolveResult result) {
            MavenModuleVersionMetaData mavenMetaData = mavenMetaData(module);
            if (mavenMetaData.isPomPackaging()) {
                Set<ComponentArtifactMetaData> artifacts = new LinkedHashSet<ComponentArtifactMetaData>();
                artifacts.addAll(findOptionalArtifacts(module, "jar", null));
                result.resolved(artifacts);
            } else {
                ModuleVersionArtifactMetaData artifactMetaData = module.artifact(mavenMetaData.getPackaging(), mavenMetaData.getPackaging(), null);

                if (createArtifactResolver(module.getSource()).artifactExists(artifactMetaData)) {
                    DeprecationLogger.nagUserOfDeprecated("Relying on packaging to define the extension of the main artifact");
                    result.resolved(ImmutableSet.of(artifactMetaData));
                } else {
                    ModuleVersionArtifactMetaData artifact = module.artifact("jar", "jar", null);
                    result.resolved(ImmutableSet.of(artifact));
                }
            }
        }

        @Override
        protected void resolveJavadocArtifacts(ModuleVersionMetaData module, BuildableArtifactSetResolveResult result) {
            result.resolved(findOptionalArtifacts(module, "javadoc", "javadoc"));
        }

        @Override
        protected void resolveSourceArtifacts(ModuleVersionMetaData module, BuildableArtifactSetResolveResult result) {
            result.resolved(findOptionalArtifacts(module, "source", "sources"));
        }
    }
}

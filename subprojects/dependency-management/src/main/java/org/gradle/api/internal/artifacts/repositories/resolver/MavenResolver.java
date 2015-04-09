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
import org.gradle.api.Nullable;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ModuleComponentRepositoryAccess;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser.DescriptorParseContext;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser.MetaDataParser;
import org.gradle.api.internal.artifacts.repositories.transport.RepositoryTransport;
import org.gradle.api.internal.component.ArtifactType;
import org.gradle.internal.Transformers;
import org.gradle.internal.component.external.model.*;
import org.gradle.internal.component.model.*;
import org.gradle.internal.resolve.result.BuildableArtifactSetResolveResult;
import org.gradle.internal.resolve.result.BuildableModuleComponentMetaDataResolveResult;
import org.gradle.internal.resolve.result.DefaultResourceAwareResolveResult;
import org.gradle.internal.resolve.result.ResourceAwareResolveResult;
import org.gradle.internal.resource.ExternalResourceName;
import org.gradle.internal.resource.local.LocallyAvailableExternalResource;
import org.gradle.internal.resource.ResourceNotFoundException;
import org.gradle.internal.resource.local.FileStore;
import org.gradle.internal.resource.local.LocallyAvailableResourceFinder;

import java.net.URI;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MavenResolver extends ExternalResourceResolver {
    private final URI root;
    private final List<URI> artifactRoots = new ArrayList<URI>();
    private final MavenMetadataLoader mavenMetaDataLoader;
    private final MetaDataParser<DefaultMavenModuleResolveMetaData> metaDataParser;
    private static final Pattern UNIQUE_SNAPSHOT = Pattern.compile("(?:.+)-(\\d{8}\\.\\d{6}-\\d+)");

    public MavenResolver(String name, URI rootUri, RepositoryTransport transport,
                         LocallyAvailableResourceFinder<ModuleComponentArtifactMetaData> locallyAvailableResourceFinder,
                         FileStore<ModuleComponentArtifactMetaData> artifactFileStore, MetaDataParser<DefaultMavenModuleResolveMetaData> pomParser) {
        super(name, transport.isLocal(),
                transport.getRepository(),
                transport.getResourceAccessor(),
                new ChainedVersionLister(new MavenVersionLister(transport.getRepository()), new ResourceVersionLister(transport.getRepository())),
                locallyAvailableResourceFinder,
                artifactFileStore);
        this.metaDataParser = pomParser;
        this.mavenMetaDataLoader = new MavenMetadataLoader(transport.getRepository());
        this.root = rootUri;

        updatePatterns();
    }

    @Override
    public String toString() {
        return String.format("Maven repository '%s'", getName());
    }

    public URI getRoot() {
        return root;
    }

    protected void doResolveComponentMetaData(DependencyMetaData dependency, ModuleComponentIdentifier moduleComponentIdentifier, BuildableModuleComponentMetaDataResolveResult result) {
        if (isNonUniqueSnapshot(moduleComponentIdentifier)) {
            MavenUniqueSnapshotModuleSource uniqueSnapshotVersion = findUniqueSnapshotVersion(moduleComponentIdentifier, result);
            if (uniqueSnapshotVersion != null) {
                MavenUniqueSnapshotComponentIdentifier snapshotIdentifier = composeSnapshotIdentifier(moduleComponentIdentifier, uniqueSnapshotVersion);
                resolveUniqueSnapshotDependency(dependency, snapshotIdentifier, result, uniqueSnapshotVersion);
                return;
            }
        } else {
            MavenUniqueSnapshotModuleSource uniqueSnapshotVersion = composeUniqueSnapshotVersion(moduleComponentIdentifier);
            if (uniqueSnapshotVersion != null) {
                MavenUniqueSnapshotComponentIdentifier snapshotIdentifier = composeSnapshotIdentifier(moduleComponentIdentifier, uniqueSnapshotVersion);
                resolveUniqueSnapshotDependency(dependency, snapshotIdentifier, result, uniqueSnapshotVersion);
                return;
            }
        }

        resolveStaticDependency(dependency, moduleComponentIdentifier, result, super.createArtifactResolver());
    }

    protected boolean isMetaDataArtifact(ArtifactType artifactType) {
        return artifactType == ArtifactType.MAVEN_POM;
    }

    private MutableModuleComponentResolveMetaData processMetaData(MutableModuleComponentResolveMetaData metaData) {
        if (isNonUniqueSnapshot(metaData.getComponentId())) {
            metaData.setChanging(true);
        }
        return metaData;
    }

    private void resolveUniqueSnapshotDependency(DependencyMetaData dependency, MavenUniqueSnapshotComponentIdentifier module, BuildableModuleComponentMetaDataResolveResult result, MavenUniqueSnapshotModuleSource snapshotSource) {
        resolveStaticDependency(dependency, module, result, createArtifactResolver(snapshotSource));
        if (result.getState() == BuildableModuleComponentMetaDataResolveResult.State.Resolved) {
            result.getMetaData().setSource(snapshotSource);
        }
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
        return new M2ResourcePattern(root, MavenPattern.M2_PATTERN);
    }

    private void updatePatterns() {
        setIvyPatterns(Collections.singletonList(getWholePattern()));

        List<ResourcePattern> artifactPatterns = new ArrayList<ResourcePattern>();
        artifactPatterns.add(getWholePattern());
        for (URI artifactRoot : artifactRoots) {
            artifactPatterns.add(new M2ResourcePattern(artifactRoot, MavenPattern.M2_PATTERN));
        }
        setArtifactPatterns(artifactPatterns);
    }

    @Override
    protected IvyArtifactName getMetaDataArtifactName(String moduleName) {
        return new DefaultIvyArtifactName(moduleName, "pom", "pom");
    }

    private MavenUniqueSnapshotModuleSource findUniqueSnapshotVersion(ModuleComponentIdentifier module, ResourceAwareResolveResult result) {
        ExternalResourceName metadataLocation = getWholePattern().toModuleVersionPath(module).resolve("maven-metadata.xml");
        result.attempted(metadataLocation);
        MavenMetadata mavenMetadata = parseMavenMetadata(metadataLocation.getUri());

        if (mavenMetadata.timestamp != null) {
            // we have found a timestamp, so this is a snapshot unique version
            String timestamp = String.format("%s-%s", mavenMetadata.timestamp, mavenMetadata.buildNumber);
            return new MavenUniqueSnapshotModuleSource(timestamp);
        }
        return null;
    }

    @Nullable
    private MavenUniqueSnapshotModuleSource composeUniqueSnapshotVersion(ModuleComponentIdentifier moduleComponentIdentifier) {
        Matcher matcher = UNIQUE_SNAPSHOT.matcher(moduleComponentIdentifier.getVersion());
        if (!matcher.matches()) {
            return null;
        }
        return new MavenUniqueSnapshotModuleSource(matcher.group(1));
    }

    private MavenMetadata parseMavenMetadata(URI metadataLocation) {
        try {
            return mavenMetaDataLoader.load(metadataLocation);
        } catch (ResourceNotFoundException e) {
            return new MavenMetadata();
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
    protected MutableModuleComponentResolveMetaData createMetaDataForDependency(DependencyMetaData dependency) {
        return processMetaData(new DefaultMavenModuleResolveMetaData(dependency));
    }

    protected MutableModuleComponentResolveMetaData parseMetaDataFromResource(ModuleComponentIdentifier moduleComponentIdentifier, LocallyAvailableExternalResource cachedResource, DescriptorParseContext context) {
        DefaultMavenModuleResolveMetaData metaData = metaDataParser.parseMetaData(context, cachedResource);
        if (moduleComponentIdentifier instanceof MavenUniqueSnapshotComponentIdentifier) {
            // Snapshot POMs use -SNAPSHOT instead of the timestamp as version, so validate against the expected id
            MavenUniqueSnapshotComponentIdentifier snapshotComponentIdentifier = (MavenUniqueSnapshotComponentIdentifier) moduleComponentIdentifier;
            checkMetadataConsistency(snapshotComponentIdentifier.getSnapshotComponent(), metaData);
            // Use the requested id. Currently we're discarding the MavenUniqueSnapshotComponentIdentifier and replacing with DefaultModuleComponentIdentifier as pretty
            // much every consumer of the meta-data is expecting a DefaultModuleComponentIdentifier.
            ModuleComponentIdentifier lossyId = DefaultModuleComponentIdentifier.newId(moduleComponentIdentifier.getGroup(), moduleComponentIdentifier.getModule(), moduleComponentIdentifier.getVersion());
            metaData.setComponentId(lossyId);
            metaData.setSnapshotTimestamp(snapshotComponentIdentifier.getTimestamp());
        } else {
            checkMetadataConsistency(moduleComponentIdentifier, metaData);
        }
        return processMetaData(metaData);
    }

    protected static MavenModuleResolveMetaData mavenMetaData(ModuleComponentResolveMetaData metaData) {
        return Transformers.cast(MavenModuleResolveMetaData.class).transform(metaData);
    }

    private class MavenLocalRepositoryAccess extends LocalRepositoryAccess {
        @Override
        protected void resolveConfigurationArtifacts(ModuleComponentResolveMetaData module, ConfigurationMetaData configuration, BuildableArtifactSetResolveResult result) {
            if (mavenMetaData(module).isKnownJarPackaging()) {
                ModuleComponentArtifactMetaData artifact = module.artifact("jar", "jar", null);
                result.resolved(ImmutableSet.of(artifact));
            }
        }

        @Override
        protected void resolveJavadocArtifacts(ModuleComponentResolveMetaData module, BuildableArtifactSetResolveResult result) {
            // Javadoc artifacts are optional, so we need to probe for them remotely
        }

        @Override
        protected void resolveSourceArtifacts(ModuleComponentResolveMetaData module, BuildableArtifactSetResolveResult result) {
            // Javadoc artifacts are optional, so we need to probe for them remotely
        }
    }

    private class MavenRemoteRepositoryAccess extends RemoteRepositoryAccess {
        @Override
        protected void resolveConfigurationArtifacts(ModuleComponentResolveMetaData module, ConfigurationMetaData configuration, BuildableArtifactSetResolveResult result) {
            MavenModuleResolveMetaData mavenMetaData = mavenMetaData(module);
            if (mavenMetaData.isPomPackaging()) {
                Set<ComponentArtifactMetaData> artifacts = new LinkedHashSet<ComponentArtifactMetaData>();
                artifacts.addAll(findOptionalArtifacts(module, "jar", null));
                result.resolved(artifacts);
            } else {
                ModuleComponentArtifactMetaData artifactMetaData = module.artifact(mavenMetaData.getPackaging(), mavenMetaData.getPackaging(), null);

                if (createArtifactResolver(module.getSource()).artifactExists(artifactMetaData, new DefaultResourceAwareResolveResult())) {
                    result.resolved(ImmutableSet.of(artifactMetaData));
                } else {
                    ModuleComponentArtifactMetaData artifact = module.artifact("jar", "jar", null);
                    result.resolved(ImmutableSet.of(artifact));
                }
            }
        }

        @Override
        protected void resolveJavadocArtifacts(ModuleComponentResolveMetaData module, BuildableArtifactSetResolveResult result) {
            result.resolved(findOptionalArtifacts(module, "javadoc", "javadoc"));
        }

        @Override
        protected void resolveSourceArtifacts(ModuleComponentResolveMetaData module, BuildableArtifactSetResolveResult result) {
            result.resolved(findOptionalArtifacts(module, "source", "sources"));
        }
    }

    private boolean isNonUniqueSnapshot(ModuleComponentIdentifier moduleComponentIdentifier) {
        return moduleComponentIdentifier.getVersion().endsWith("-SNAPSHOT");
    }

    private MavenUniqueSnapshotComponentIdentifier composeSnapshotIdentifier(ModuleComponentIdentifier moduleComponentIdentifier, MavenUniqueSnapshotModuleSource uniqueSnapshotVersion) {
        return new MavenUniqueSnapshotComponentIdentifier(moduleComponentIdentifier.getGroup(),
                moduleComponentIdentifier.getModule(),
                moduleComponentIdentifier.getVersion(),
                uniqueSnapshotVersion.getTimestamp());
    }
}

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

import org.gradle.api.artifacts.ComponentMetadataListerDetails;
import org.gradle.api.artifacts.ComponentMetadataSupplierDetails;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ModuleComponentRepositoryAccess;
import org.gradle.api.internal.artifacts.repositories.descriptor.MavenRepositoryDescriptor;
import org.gradle.api.internal.artifacts.repositories.maven.MavenMetadata;
import org.gradle.api.internal.artifacts.repositories.maven.MavenMetadataLoader;
import org.gradle.api.internal.artifacts.repositories.metadata.ImmutableMetadataSources;
import org.gradle.api.internal.artifacts.repositories.metadata.MetadataArtifactProvider;
import org.gradle.api.internal.artifacts.repositories.transport.RepositoryTransport;
import org.gradle.api.internal.component.ArtifactType;
import org.gradle.api.resources.MissingResourceException;
import org.gradle.internal.action.InstantiatingAction;
import org.gradle.internal.component.external.model.ModuleComponentArtifactIdentifier;
import org.gradle.internal.component.external.model.ModuleComponentArtifactMetadata;
import org.gradle.internal.component.external.model.ModuleComponentResolveMetadata;
import org.gradle.internal.component.external.model.maven.MutableMavenModuleResolveMetadata;
import org.gradle.internal.component.model.ComponentArtifactResolveMetadata;
import org.gradle.internal.component.model.ComponentOverrideMetadata;
import org.gradle.internal.component.model.ModuleSources;
import org.gradle.internal.component.model.MutableModuleSources;
import org.gradle.internal.hash.ChecksumService;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.resolve.result.BuildableArtifactSetResolveResult;
import org.gradle.internal.resolve.result.BuildableModuleComponentMetaDataResolveResult;
import org.gradle.internal.resolve.result.ResourceAwareResolveResult;
import org.gradle.internal.resource.ExternalResourceName;
import org.gradle.internal.resource.local.FileStore;
import org.gradle.internal.resource.local.LocallyAvailableResourceFinder;

import javax.annotation.Nullable;
import java.net.URI;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MavenResolver extends ExternalResourceResolver {
    private final URI root;
    private final MavenMetadataLoader mavenMetaDataLoader;

    private static final Pattern UNIQUE_SNAPSHOT = Pattern.compile("(?:.+)-(\\d{8}\\.\\d{6}-\\d+)");
    private final MavenLocalRepositoryAccess localAccess = new MavenLocalRepositoryAccess();
    private final MavenRemoteRepositoryAccess remoteAccess = new MavenRemoteRepositoryAccess();

    public MavenResolver(
        MavenRepositoryDescriptor descriptor,
        URI rootUri,
        RepositoryTransport transport,
        LocallyAvailableResourceFinder<ModuleComponentArtifactMetadata> locallyAvailableResourceFinder,
        FileStore<ModuleComponentArtifactIdentifier> artifactFileStore,
        ImmutableMetadataSources metadataSources,
        MetadataArtifactProvider metadataArtifactProvider,
        MavenMetadataLoader mavenMetadataLoader,
        @Nullable InstantiatingAction<ComponentMetadataSupplierDetails> componentMetadataSupplierFactory,
        @Nullable InstantiatingAction<ComponentMetadataListerDetails> versionListerFactory,
        Instantiator injector,
        ChecksumService checksumService) {
        super(descriptor, transport.isLocal(),
            transport.getRepository(),
            transport.getResourceAccessor(),
            locallyAvailableResourceFinder,
            artifactFileStore,
            metadataSources,
            metadataArtifactProvider,
            componentMetadataSupplierFactory,
            versionListerFactory,
            injector,
            checksumService);
        this.mavenMetaDataLoader = mavenMetadataLoader;
        this.root = rootUri;
    }

    @Override
    public String toString() {
        return "Maven repository '" + getName() + "'";
    }

    public URI getRoot() {
        return root;
    }

    @Override
    protected void doResolveComponentMetaData(ModuleComponentIdentifier moduleComponentIdentifier, ComponentOverrideMetadata prescribedMetaData, BuildableModuleComponentMetaDataResolveResult<ModuleComponentResolveMetadata> result) {
        MavenUniqueSnapshotModuleSource uniqueSnapshotVersion = isNonUniqueSnapshot(moduleComponentIdentifier)
            ? findUniqueSnapshotVersion(moduleComponentIdentifier, result)
            : composeUniqueSnapshotVersion(moduleComponentIdentifier);

        if (uniqueSnapshotVersion != null) {
            MavenUniqueSnapshotComponentIdentifier snapshotIdentifier = composeSnapshotIdentifier(moduleComponentIdentifier, uniqueSnapshotVersion);
            resolveUniqueSnapshotDependency(snapshotIdentifier, prescribedMetaData, result, uniqueSnapshotVersion);
        } else {
            resolveStaticDependency(moduleComponentIdentifier, prescribedMetaData, result, super.createArtifactResolver());
        }
    }

    @Override
    protected boolean isMetaDataArtifact(ArtifactType artifactType) {
        return artifactType == ArtifactType.MAVEN_POM;
    }

    private void resolveUniqueSnapshotDependency(MavenUniqueSnapshotComponentIdentifier module, ComponentOverrideMetadata prescribedMetaData, BuildableModuleComponentMetaDataResolveResult<ModuleComponentResolveMetadata> result, MavenUniqueSnapshotModuleSource snapshotSource) {
        resolveStaticDependency(module, prescribedMetaData, result, createArtifactResolver(MutableModuleSources.of(snapshotSource)));
    }

    @Override
    protected ExternalResourceArtifactResolver createArtifactResolver(@Nullable ModuleSources moduleSources) {
        if (moduleSources == null) {
            return super.createArtifactResolver(null);
        }

        return moduleSources.withSource(MavenUniqueSnapshotModuleSource.class, source -> {
            if (source.isPresent()) {
                return new MavenUniqueSnapshotExternalResourceArtifactResolver(super.createArtifactResolver(moduleSources), source.get());
            } else {
                return super.createArtifactResolver(moduleSources);
            }
        });
    }

    private M2ResourcePattern getWholePattern() {
        return new M2ResourcePattern(root, MavenPattern.M2_PATTERN);
    }

    @Nullable
    private MavenUniqueSnapshotModuleSource findUniqueSnapshotVersion(ModuleComponentIdentifier module, ResourceAwareResolveResult result) {
        M2ResourcePattern wholePattern = getWholePattern();
        if (!wholePattern.isComplete(module)) {
            //do not attempt to download maven-metadata.xml for incomplete identifiers
            return null;
        }
        ExternalResourceName metadataLocation = wholePattern.toModuleVersionPath(module).resolve("maven-metadata.xml");
        result.attempted(metadataLocation);
        MavenMetadata mavenMetadata = parseMavenMetadata(metadataLocation);

        if (mavenMetadata.timestamp != null) {
            // we have found a timestamp, so this is a snapshot unique version
            String timestamp = mavenMetadata.timestamp + "-" + mavenMetadata.buildNumber;
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

    private MavenMetadata parseMavenMetadata(ExternalResourceName metadataLocation) {
        try {
            return mavenMetaDataLoader.load(metadataLocation);
        } catch (MissingResourceException e) {
            return new MavenMetadata();
        }
    }

    @Override
    public ModuleComponentRepositoryAccess<ModuleComponentResolveMetadata> getLocalAccess() {
        return localAccess;
    }

    @Override
    public ModuleComponentRepositoryAccess<ModuleComponentResolveMetadata> getRemoteAccess() {
        return remoteAccess;
    }

    public static MutableMavenModuleResolveMetadata processMetaData(MutableMavenModuleResolveMetadata metaData) {
        ModuleComponentIdentifier id = metaData.getId();
        if (isNonUniqueSnapshot(id)) {
            metaData.setChanging(true);
        }
        if (isUniqueSnapshot(id)) {
            MavenUniqueSnapshotComponentIdentifier mus = (MavenUniqueSnapshotComponentIdentifier) id;
            metaData.setSnapshotTimestamp(mus.getTimestamp());
        }
        return metaData;
    }

    private class MavenLocalRepositoryAccess extends LocalRepositoryAccess {

        @Override
        protected void resolveJavadocArtifacts(ComponentArtifactResolveMetadata module, BuildableArtifactSetResolveResult result) {
            // Javadoc artifacts are optional, so we need to probe for them remotely
        }

        @Override
        protected void resolveSourceArtifacts(ComponentArtifactResolveMetadata module, BuildableArtifactSetResolveResult result) {
            // Source artifacts are optional, so we need to probe for them remotely
        }
    }

    private class MavenRemoteRepositoryAccess extends RemoteRepositoryAccess {

    }

    private static boolean isUniqueSnapshot(ModuleComponentIdentifier id) {
        return id instanceof MavenUniqueSnapshotComponentIdentifier;
    }

    protected static boolean isNonUniqueSnapshot(ModuleComponentIdentifier moduleComponentIdentifier) {
        return moduleComponentIdentifier.getVersion().endsWith("-SNAPSHOT");
    }

    private MavenUniqueSnapshotComponentIdentifier composeSnapshotIdentifier(ModuleComponentIdentifier moduleComponentIdentifier, MavenUniqueSnapshotModuleSource uniqueSnapshotVersion) {
        return new MavenUniqueSnapshotComponentIdentifier(
            moduleComponentIdentifier.getModuleIdentifier(),
            moduleComponentIdentifier.getVersion(),
            uniqueSnapshotVersion.getTimestamp());
    }
}

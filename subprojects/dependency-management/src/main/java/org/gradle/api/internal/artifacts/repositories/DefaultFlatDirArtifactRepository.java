/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.api.internal.artifacts.repositories;

import com.google.common.base.Joiner;
import org.gradle.api.Action;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.artifacts.repositories.FlatDirectoryArtifactRepository;
import org.gradle.api.artifacts.repositories.RepositoryResourceAccessor;
import org.gradle.api.internal.artifacts.ModuleVersionPublisher;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ConfiguredModuleComponentRepository;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionParser;
import org.gradle.api.internal.artifacts.repositories.descriptor.FlatDirRepositoryDescriptor;
import org.gradle.api.internal.artifacts.repositories.descriptor.RepositoryDescriptor;
import org.gradle.api.internal.artifacts.repositories.metadata.DefaultArtifactMetadataSource;
import org.gradle.api.internal.artifacts.repositories.metadata.DefaultImmutableMetadataSources;
import org.gradle.api.internal.artifacts.repositories.metadata.ImmutableMetadataSources;
import org.gradle.api.internal.artifacts.repositories.metadata.IvyMetadataArtifactProvider;
import org.gradle.api.internal.artifacts.repositories.metadata.IvyMutableModuleMetadataFactory;
import org.gradle.api.internal.artifacts.repositories.metadata.MetadataSource;
import org.gradle.api.internal.artifacts.repositories.resolver.IvyResolver;
import org.gradle.api.internal.artifacts.repositories.transport.RepositoryTransport;
import org.gradle.api.internal.artifacts.repositories.transport.RepositoryTransportFactory;
import org.gradle.api.internal.file.FileCollectionFactory;
import org.gradle.api.model.ObjectFactory;
import org.gradle.internal.component.external.model.ModuleComponentArtifactIdentifier;
import org.gradle.internal.component.external.model.ModuleComponentArtifactMetadata;
import org.gradle.internal.component.external.model.MutableModuleComponentResolveMetadata;
import org.gradle.internal.hash.ChecksumService;
import org.gradle.internal.instantiation.InstantiatorFactory;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.resolve.caching.ImplicitInputRecorder;
import org.gradle.internal.resolve.caching.ImplicitInputsProvidingService;
import org.gradle.internal.resource.local.FileStore;
import org.gradle.internal.resource.local.LocallyAvailableResourceFinder;
import org.gradle.util.internal.CollectionUtils;

import javax.annotation.Nullable;
import java.io.File;
import java.io.InputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class DefaultFlatDirArtifactRepository extends AbstractResolutionAwareArtifactRepository implements FlatDirectoryArtifactRepository, ResolutionAwareRepository, PublicationAwareRepository {
    private final FileCollectionFactory fileCollectionFactory;
    private final List<Object> dirs = new ArrayList<>();
    private final RepositoryTransportFactory transportFactory;
    private final LocallyAvailableResourceFinder<ModuleComponentArtifactMetadata> locallyAvailableResourceFinder;
    private final FileStore<ModuleComponentArtifactIdentifier> artifactFileStore;
    private final IvyMutableModuleMetadataFactory metadataFactory;
    private final InstantiatorFactory instantiatorFactory;
    private final ChecksumService checksumService;

    public DefaultFlatDirArtifactRepository(FileCollectionFactory fileCollectionFactory,
                                            RepositoryTransportFactory transportFactory,
                                            LocallyAvailableResourceFinder<ModuleComponentArtifactMetadata> locallyAvailableResourceFinder,
                                            FileStore<ModuleComponentArtifactIdentifier> artifactFileStore,
                                            IvyMutableModuleMetadataFactory metadataFactory,
                                            InstantiatorFactory instantiatorFactory,
                                            ObjectFactory objectFactory,
                                            ChecksumService checksumService,
                                            VersionParser versionParser
    ) {
        super(objectFactory, versionParser);
        this.fileCollectionFactory = fileCollectionFactory;
        this.transportFactory = transportFactory;
        this.locallyAvailableResourceFinder = locallyAvailableResourceFinder;
        this.artifactFileStore = artifactFileStore;
        this.metadataFactory = metadataFactory;
        this.instantiatorFactory = instantiatorFactory;
        this.checksumService = checksumService;
    }

    @Override
    public String getDisplayName() {
        Set<File> dirs = getDirs();
        if (dirs.isEmpty()) {
            return super.getDisplayName();
        }
        return super.getDisplayName() + '(' + Joiner.on(", ").join(dirs) + ')';
    }

    @Override
    public Set<File> getDirs() {
        return fileCollectionFactory.resolving(dirs).getFiles();
    }

    @Override
    public void setDirs(Set<File> dirs) {
        setDirs((Iterable<?>) dirs);
    }

    @Override
    public void setDirs(Iterable<?> dirs) {
        invalidateDescriptor();
        this.dirs.clear();
        CollectionUtils.addAll(this.dirs, dirs);
    }

    @Override
    public void dir(Object dir) {
        dirs(dir);
    }

    @Override
    public void dirs(Object... dirs) {
        invalidateDescriptor();
        this.dirs.addAll(Arrays.asList(dirs));
    }

    @Override
    public ModuleVersionPublisher createPublisher() {
        return createRealResolver();
    }

    @Override
    public ConfiguredModuleComponentRepository createResolver() {
        return createRealResolver();
    }

    @Override
    protected RepositoryDescriptor createDescriptor() {
        return new FlatDirRepositoryDescriptor(
            getName(),
            getDirs()
        );
    }


    @Override
    protected RepositoryResourceAccessor createRepositoryAccessor(RepositoryTransport transport, URI rootUri, FileStore<String> externalResourcesFileStore) {
        return new NoOpRepositoryResourceAccessor();
    }

    private IvyResolver createRealResolver() {
        Set<File> dirs = getDirs();
        if (dirs.isEmpty()) {
            throw new InvalidUserDataException("You must specify at least one directory for a flat directory repository.");
        }

        RepositoryTransport transport = transportFactory.createFileTransport(getName());
        Instantiator injector = createInjectorForMetadataSuppliers(transport, instantiatorFactory, null, null);
        IvyResolver resolver = new IvyResolver(getName(), transport, locallyAvailableResourceFinder, false, artifactFileStore, null, null, createMetadataSources(), IvyMetadataArtifactProvider.INSTANCE, injector, checksumService);
        for (File root : dirs) {
            resolver.addArtifactLocation(root.toURI(), "/[artifact]-[revision](-[classifier]).[ext]");
            resolver.addArtifactLocation(root.toURI(), "/[artifact](-[classifier]).[ext]");
        }
        return resolver;
    }

    private ImmutableMetadataSources createMetadataSources() {
        MetadataSource<MutableModuleComponentResolveMetadata> artifactMetadataSource = new DefaultArtifactMetadataSource(metadataFactory);
        return new DefaultImmutableMetadataSources(Collections.singletonList(artifactMetadataSource));
    }

    private static class NoOpRepositoryResourceAccessor implements RepositoryResourceAccessor, ImplicitInputsProvidingService<String, Long, RepositoryResourceAccessor> {
        @Override
        public void withResource(String relativePath, Action<? super InputStream> action) {
            // No-op
        }

        @Override
        public RepositoryResourceAccessor withImplicitInputRecorder(ImplicitInputRecorder registrar) {
            // Service calls have no effect, no need to register them
            return this;
        }

        @Override
        public boolean isUpToDate(String s, @Nullable Long oldValue) {
            // Nothing accessible, always up to date
            return true;
        }
    }
}

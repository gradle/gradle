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
import com.google.common.collect.Lists;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.artifacts.repositories.FlatDirectoryArtifactRepository;
import org.gradle.api.internal.InstantiatorFactory;
import org.gradle.api.internal.artifacts.ImmutableModuleIdentifierFactory;
import org.gradle.api.internal.artifacts.ModuleVersionPublisher;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ConfiguredModuleComponentRepository;
import org.gradle.api.internal.artifacts.repositories.metadata.DefaultArtifactMetadataSource;
import org.gradle.api.internal.artifacts.repositories.metadata.DefaultImmutableMetadataSources;
import org.gradle.api.internal.artifacts.repositories.metadata.ImmutableMetadataSources;
import org.gradle.api.internal.artifacts.repositories.metadata.IvyMetadataArtifactProvider;
import org.gradle.api.internal.artifacts.repositories.metadata.IvyMutableModuleMetadataFactory;
import org.gradle.api.internal.artifacts.repositories.metadata.MetadataSource;
import org.gradle.api.internal.artifacts.repositories.resolver.IvyResolver;
import org.gradle.api.internal.artifacts.repositories.transport.RepositoryTransport;
import org.gradle.api.internal.artifacts.repositories.transport.RepositoryTransportFactory;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.authentication.Authentication;
import org.gradle.internal.component.external.model.ModuleComponentArtifactIdentifier;
import org.gradle.internal.component.external.model.ModuleComponentArtifactMetadata;
import org.gradle.internal.resource.local.FileStore;
import org.gradle.internal.resource.local.LocallyAvailableResourceFinder;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class DefaultFlatDirArtifactRepository extends AbstractArtifactRepository implements FlatDirectoryArtifactRepository, ResolutionAwareRepository, PublicationAwareRepository {
    private final FileResolver fileResolver;
    private List<Object> dirs = new ArrayList<Object>();
    private final RepositoryTransportFactory transportFactory;
    private final LocallyAvailableResourceFinder<ModuleComponentArtifactMetadata> locallyAvailableResourceFinder;
    private final FileStore<ModuleComponentArtifactIdentifier> artifactFileStore;
    private final ImmutableModuleIdentifierFactory moduleIdentifierFactory;
    private final IvyMutableModuleMetadataFactory metadataFactory;
    private final InstantiatorFactory instantiatorFactory;

    public DefaultFlatDirArtifactRepository(FileResolver fileResolver,
                                            RepositoryTransportFactory transportFactory,
                                            LocallyAvailableResourceFinder<ModuleComponentArtifactMetadata> locallyAvailableResourceFinder,
                                            FileStore<ModuleComponentArtifactIdentifier> artifactFileStore,
                                            ImmutableModuleIdentifierFactory moduleIdentifierFactory,
                                            IvyMutableModuleMetadataFactory metadataFactory,
                                            InstantiatorFactory instantiatorFactory) {
        this.fileResolver = fileResolver;
        this.transportFactory = transportFactory;
        this.locallyAvailableResourceFinder = locallyAvailableResourceFinder;
        this.artifactFileStore = artifactFileStore;
        this.moduleIdentifierFactory = moduleIdentifierFactory;
        this.metadataFactory = metadataFactory;
        this.instantiatorFactory = instantiatorFactory;
    }

    @Override
    public String getDisplayName() {
        Set<File> dirs = getDirs();
        if (dirs.isEmpty()) {
            return super.getDisplayName();
        }
        return super.getDisplayName() + '(' + Joiner.on(", ").join(dirs) + ')';
    }

    public Set<File> getDirs() {
        return fileResolver.resolveFiles(dirs).getFiles();
    }

    public void setDirs(Set<File> dirs) {
        setDirs((Iterable<?>) dirs);
    }

    public void setDirs(Iterable<?> dirs) {
        this.dirs = Lists.newArrayList(dirs);
    }

    public void dir(Object dir) {
        dirs(dir);
    }

    public void dirs(Object... dirs) {
        this.dirs.addAll(Arrays.asList(dirs));
    }

    public ModuleVersionPublisher createPublisher() {
        return createRealResolver();
    }

    public ConfiguredModuleComponentRepository createResolver() {
        return createRealResolver();
    }

    private IvyResolver createRealResolver() {
        Set<File> dirs = getDirs();
        if (dirs.isEmpty()) {
            throw new InvalidUserDataException("You must specify at least one directory for a flat directory repository.");
        }

        RepositoryTransport transport = transportFactory.createTransport("file", getName(), Collections.<Authentication>emptyList());
        IvyResolver resolver = new IvyResolver(getName(), transport, locallyAvailableResourceFinder, false, artifactFileStore, moduleIdentifierFactory, null, null, createMetadataSources(), IvyMetadataArtifactProvider.INSTANCE, instantiatorFactory.inject());
        for (File root : dirs) {
            resolver.addArtifactLocation(root.toURI(), "/[artifact]-[revision](-[classifier]).[ext]");
            resolver.addArtifactLocation(root.toURI(), "/[artifact](-[classifier]).[ext]");
        }
        return resolver;
    }

    private ImmutableMetadataSources createMetadataSources() {
        MetadataSource artifactMetadataSource = new DefaultArtifactMetadataSource(metadataFactory);
        return new DefaultImmutableMetadataSources(Collections.<MetadataSource<?>>singletonList(artifactMetadataSource));
    }
}

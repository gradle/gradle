/*
 * Copyright 2013 the original author or authors.
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

import org.gradle.api.InvalidUserDataException;
import org.gradle.api.artifacts.repositories.AuthenticationContainer;
import org.gradle.api.artifacts.repositories.MavenArtifactRepository;
import org.gradle.api.internal.FeaturePreviews;
import org.gradle.internal.instantiation.InstantiatorFactory;
import org.gradle.api.internal.artifacts.ImmutableModuleIdentifierFactory;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser.MetaDataParser;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser.GradleModuleMetadataParser;
import org.gradle.api.internal.artifacts.repositories.maven.MavenMetadataLoader;
import org.gradle.api.internal.artifacts.repositories.metadata.DefaultMavenPomMetadataSource;
import org.gradle.api.internal.artifacts.repositories.metadata.MavenMetadataArtifactProvider;
import org.gradle.api.internal.artifacts.repositories.metadata.MavenMutableModuleMetadataFactory;
import org.gradle.api.internal.artifacts.repositories.resolver.ExternalResourceArtifactResolver;
import org.gradle.api.internal.artifacts.repositories.resolver.MavenResolver;
import org.gradle.api.internal.artifacts.repositories.transport.RepositoryTransport;
import org.gradle.api.internal.artifacts.repositories.transport.RepositoryTransportFactory;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.model.ObjectFactory;
import org.gradle.internal.component.external.model.ModuleComponentArtifactIdentifier;
import org.gradle.internal.component.external.model.ModuleComponentArtifactMetadata;
import org.gradle.internal.component.external.model.maven.MutableMavenModuleResolveMetadata;
import org.gradle.internal.isolation.IsolatableFactory;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.resolve.result.DefaultResourceAwareResolveResult;
import org.gradle.internal.resource.local.FileResourceRepository;
import org.gradle.internal.resource.local.FileStore;
import org.gradle.internal.resource.local.LocallyAvailableResourceFinder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;

public class DefaultMavenLocalArtifactRepository extends DefaultMavenArtifactRepository implements MavenArtifactRepository {
    private static final Logger LOGGER = LoggerFactory.getLogger(MavenResolver.class);

    private final ImmutableModuleIdentifierFactory moduleIdentifierFactory;

    public DefaultMavenLocalArtifactRepository(FileResolver fileResolver, RepositoryTransportFactory transportFactory,
                                               LocallyAvailableResourceFinder<ModuleComponentArtifactMetadata> locallyAvailableResourceFinder, InstantiatorFactory instantiatorFactory,
                                               FileStore<ModuleComponentArtifactIdentifier> artifactFileStore,
                                               MetaDataParser<MutableMavenModuleResolveMetadata> pomParser,
                                               GradleModuleMetadataParser metadataParser,
                                               AuthenticationContainer authenticationContainer,
                                               ImmutableModuleIdentifierFactory moduleIdentifierFactory,
                                               FileResourceRepository fileResourceRepository,
                                               FeaturePreviews featurePreviews,
                                               MavenMutableModuleMetadataFactory metadataFactory,
                                               IsolatableFactory isolatableFactory,
                                               ObjectFactory objectFactory) {
        super(fileResolver, transportFactory, locallyAvailableResourceFinder, instantiatorFactory, artifactFileStore, pomParser, metadataParser, authenticationContainer, moduleIdentifierFactory, null, fileResourceRepository, featurePreviews, metadataFactory, isolatableFactory, objectFactory);
        this.moduleIdentifierFactory = moduleIdentifierFactory;
    }

    @Override
    protected MavenResolver createRealResolver() {
        URI rootUri = getUrl();
        if (rootUri == null) {
            throw new InvalidUserDataException("You must specify a URL for a Maven repository.");
        }

        RepositoryTransport transport = getTransport(rootUri.getScheme());
        MavenMetadataLoader mavenMetadataLoader = new MavenMetadataLoader(transport.getResourceAccessor(), getResourcesFileStore());
        Instantiator injector = createInjectorForMetadataSuppliers(transport, getInstantiatorFactory(), getUrl(), getResourcesFileStore());
        MavenResolver resolver = new MavenResolver(
            getName(),
            rootUri,
            transport,
            getLocallyAvailableResourceFinder(),
            getArtifactFileStore(),
            moduleIdentifierFactory,
            createMetadataSources(mavenMetadataLoader),
            MavenMetadataArtifactProvider.INSTANCE,
            mavenMetadataLoader,
            null,
            null, injector);
        for (URI repoUrl : getArtifactUrls()) {
            resolver.addArtifactLocation(repoUrl);
        }
        return resolver;
    }

    @Override
    protected DefaultMavenPomMetadataSource.MavenMetadataValidator getMetadataValidationServices() {
        return new MavenLocalMetadataValidator();
    }

    /**
     * It is common for a local m2 repo to have POM files with no respective artifacts. Ignore these POM files.
     */
    private static class MavenLocalMetadataValidator implements DefaultMavenPomMetadataSource.MavenMetadataValidator {
        @Override
        public boolean isUsableModule(String repoName, MutableMavenModuleResolveMetadata metaData, ExternalResourceArtifactResolver artifactResolver) {

            if (metaData.isPomPackaging()) {
                return true;
            }

            // check custom packaging
            ModuleComponentArtifactMetadata artifact;
            if (metaData.isKnownJarPackaging()) {
                artifact = metaData.artifact("jar", "jar", null);
            } else {
                artifact = metaData.artifact(metaData.getPackaging(), metaData.getPackaging(), null);
            }

            if (artifactResolver.artifactExists(artifact, new DefaultResourceAwareResolveResult())) {
                return true;
            }

            LOGGER.debug("POM file found for module '{}' in repository '{}' but no artifact found. Ignoring.", metaData.getModuleVersionId(), repoName);
            return false;

        }
    }
}

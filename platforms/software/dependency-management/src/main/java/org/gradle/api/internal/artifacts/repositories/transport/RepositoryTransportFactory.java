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
package org.gradle.api.internal.artifacts.repositories.transport;

import org.gradle.api.InvalidUserDataException;
import org.gradle.api.credentials.Credentials;
import org.gradle.api.internal.artifacts.ivyservice.ArtifactCacheLockingAccessCoordinator;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.StartParameterResolutionOverride;
import org.gradle.api.internal.artifacts.ivyservice.resolutionstrategy.DefaultExternalResourceCachePolicy;
import org.gradle.api.internal.artifacts.ivyservice.resolutionstrategy.ExternalResourceCachePolicy;
import org.gradle.api.internal.file.temp.TemporaryFileProvider;
import org.gradle.authentication.Authentication;
import org.gradle.cache.internal.ProducerGuard;
import org.gradle.internal.authentication.AuthenticationInternal;
import org.gradle.internal.hash.ChecksumService;
import org.gradle.internal.operations.BuildOperationRunner;
import org.gradle.internal.resource.ExternalResourceName;
import org.gradle.internal.resource.cached.CachedExternalResourceIndex;
import org.gradle.internal.resource.connector.ResourceConnectorFactory;
import org.gradle.internal.resource.connector.ResourceConnectorSpecification;
import org.gradle.internal.resource.local.FileResourceRepository;
import org.gradle.internal.resource.transfer.ExternalResourceConnector;
import org.gradle.internal.resource.transport.ResourceConnectorRepositoryTransport;
import org.gradle.internal.resource.transport.file.FileTransport;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;
import org.gradle.internal.verifier.HttpRedirectVerifier;
import org.gradle.util.internal.BuildCommencedTimeProvider;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@ServiceScope(Scope.Build.class)
public class RepositoryTransportFactory {
    private final List<ResourceConnectorFactory> registeredProtocols = new ArrayList<>();

    private final TemporaryFileProvider temporaryFileProvider;
    private final CachedExternalResourceIndex<String> cachedExternalResourceIndex;
    private final BuildCommencedTimeProvider timeProvider;
    private final ArtifactCacheLockingAccessCoordinator artifactCacheLockingManager;
    private final BuildOperationRunner buildOperationRunner;
    private final StartParameterResolutionOverride startParameterResolutionOverride;
    private final ProducerGuard<ExternalResourceName> producerGuard;
    private final FileResourceRepository fileRepository;
    private final ChecksumService checksumService;

    public RepositoryTransportFactory(Collection<ResourceConnectorFactory> resourceConnectorFactory,
                                      TemporaryFileProvider temporaryFileProvider,
                                      CachedExternalResourceIndex<String> cachedExternalResourceIndex,
                                      BuildCommencedTimeProvider timeProvider,
                                      ArtifactCacheLockingAccessCoordinator cacheAccessCoordinator,
                                      BuildOperationRunner buildOperationRunner,
                                      StartParameterResolutionOverride startParameterResolutionOverride,
                                      ProducerGuard<ExternalResourceName> producerGuard,
                                      FileResourceRepository fileRepository,
                                      ChecksumService checksumService) {
        this.temporaryFileProvider = temporaryFileProvider;
        this.cachedExternalResourceIndex = cachedExternalResourceIndex;
        this.timeProvider = timeProvider;
        this.artifactCacheLockingManager = cacheAccessCoordinator;
        this.buildOperationRunner = buildOperationRunner;
        this.startParameterResolutionOverride = startParameterResolutionOverride;
        this.producerGuard = producerGuard;
        this.fileRepository = fileRepository;
        this.checksumService = checksumService;

        registeredProtocols.addAll(resourceConnectorFactory);
    }

    public Set<String> getRegisteredProtocols() {
        Set<String> validSchemes = new LinkedHashSet<>();
        for (ResourceConnectorFactory registeredProtocol : registeredProtocols) {
            validSchemes.addAll(registeredProtocol.getSupportedProtocols());
        }
        return validSchemes;
    }

    public RepositoryTransport createFileTransport(String name) {
        return new FileTransport(name, fileRepository, cachedExternalResourceIndex, temporaryFileProvider, timeProvider, artifactCacheLockingManager, producerGuard, checksumService);
    }

    public RepositoryTransport createTransport(String scheme, String name, Collection<Authentication> authentications, HttpRedirectVerifier redirectVerifier) {
        return createTransport(Collections.singleton(scheme), name, authentications, redirectVerifier);
    }

    public RepositoryTransport createTransport(Set<String> schemes, String name, Collection<Authentication> authentications, HttpRedirectVerifier redirectVerifier) {
        validateSchemes(schemes);

        ResourceConnectorFactory connectorFactory = findConnectorFactory(schemes);

        // Ensure resource transport protocol, authentication types and credentials are all compatible
        validateConnectorFactoryCredentials(schemes, connectorFactory, authentications);

        // File resources are handled slightly differently at present.
        // file:// repos are treated differently
        // 1) we don't cache their files
        // 2) we don't do progress logging for "downloading"
        if (schemes.equals(Collections.singleton("file"))) {
            return createFileTransport(name);
        }
        ResourceConnectorSpecification connectionDetails = new DefaultResourceConnectorSpecification(authentications, redirectVerifier);

        ExternalResourceConnector resourceConnector = connectorFactory.createResourceConnector(connectionDetails);
        resourceConnector = startParameterResolutionOverride.overrideExternalResourceConnector(resourceConnector);

        ExternalResourceCachePolicy cachePolicy = new DefaultExternalResourceCachePolicy();
        cachePolicy = startParameterResolutionOverride.overrideExternalResourceCachePolicy(cachePolicy);

        return new ResourceConnectorRepositoryTransport(name, temporaryFileProvider, cachedExternalResourceIndex, timeProvider, artifactCacheLockingManager, resourceConnector, buildOperationRunner, cachePolicy, producerGuard, fileRepository, checksumService);
    }

    private void validateSchemes(Set<String> schemes) {
        Set<String> validSchemes = getRegisteredProtocols();
        for (String scheme : schemes) {
            if (!validSchemes.contains(scheme)) {
                throw new InvalidUserDataException(String.format("Not a supported repository protocol '%s': valid protocols are %s", scheme, validSchemes));
            }
        }
    }

    private void validateConnectorFactoryCredentials(Set<String> schemes, ResourceConnectorFactory factory, Collection<Authentication> authentications) {
        Set<Class<? extends Authentication>> configuredAuthenticationTypes = new HashSet<>();

        for (Authentication authentication : authentications) {
            AuthenticationInternal authenticationInternal = (AuthenticationInternal) authentication;
            boolean isAuthenticationSupported = false;
            Credentials credentials = authenticationInternal.getCredentials();
            boolean needCredentials = authenticationInternal.requiresCredentials();

            for (Class<?> authenticationType : factory.getSupportedAuthentication()) {
                if (authenticationType.isAssignableFrom(authentication.getClass())) {
                    isAuthenticationSupported = true;
                    break;
                }
            }

            if (!isAuthenticationSupported) {
                throw new InvalidUserDataException(String.format("Authentication scheme %s is not supported by protocol '%s'",
                    authentication, schemes.iterator().next()));
            }

            if (credentials != null) {
                if (!((AuthenticationInternal) authentication).supports(credentials)) {
                    throw new InvalidUserDataException(String.format("Credentials type of '%s' is not supported by authentication scheme %s",
                        credentials.getClass().getSimpleName(), authentication));
                }
            } else {
                if (needCredentials) {
                    throw new InvalidUserDataException("You cannot configure authentication schemes for this repository type if no credentials are provided.");
                }
            }

            if (!configuredAuthenticationTypes.add(authenticationInternal.getType())) {
                throw new InvalidUserDataException(String.format("You cannot configure multiple authentication schemes of the same type.  The duplicate one is %s.", authentication));
            }
        }
    }

    private ResourceConnectorFactory findConnectorFactory(Set<String> schemes) {
        for (ResourceConnectorFactory protocolRegistration : registeredProtocols) {
            if (protocolRegistration.getSupportedProtocols().containsAll(schemes)) {
                return protocolRegistration;
            }
        }
        throw new InvalidUserDataException("You cannot mix different URL schemes for a single repository. Please declare separate repositories.");
    }

    private static class DefaultResourceConnectorSpecification implements ResourceConnectorSpecification {
        private final Collection<Authentication> authentications;
        private final HttpRedirectVerifier redirectVerifier;

        private DefaultResourceConnectorSpecification(Collection<Authentication> authentications, HttpRedirectVerifier redirectVerifier) {
            this.authentications = authentications;
            this.redirectVerifier = redirectVerifier;
        }

        @Override
        public <T> T getCredentials(Class<T> type) {
            if (authentications == null || authentications.size() < 1) {
                return null;
            }

            Credentials credentials = ((AuthenticationInternal) authentications.iterator().next()).getCredentials();

            if (credentials == null) {
                return null;
            }
            if (type.isAssignableFrom(credentials.getClass())) {
                return type.cast(credentials);
            } else {
                throw new IllegalArgumentException(String.format("Credentials must be an instance of '%s'.", type.getCanonicalName()));
            }
        }

        @Override
        public Collection<Authentication> getAuthentications() {
            return authentications;
        }

        @Override
        public HttpRedirectVerifier getRedirectVerifier() {
            return redirectVerifier;
        }
    }
}

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

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.credentials.Credentials;
import org.gradle.api.internal.artifacts.ivyservice.CacheLockingManager;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.StartParameterResolutionOverride;
import org.gradle.api.internal.artifacts.ivyservice.resolutionstrategy.DefaultExternalResourceCachePolicy;
import org.gradle.api.internal.artifacts.ivyservice.resolutionstrategy.ExternalResourceCachePolicy;
import org.gradle.api.internal.file.TemporaryFileProvider;
import org.gradle.authentication.Authentication;
import org.gradle.cache.internal.ProducerGuard;
import org.gradle.internal.authentication.AuthenticationInternal;
import org.gradle.internal.logging.progress.ProgressLoggerFactory;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.resource.ExternalResourceName;
import org.gradle.internal.resource.cached.CachedExternalResourceIndex;
import org.gradle.internal.resource.connector.ResourceConnectorFactory;
import org.gradle.internal.resource.connector.ResourceConnectorSpecification;
import org.gradle.internal.resource.local.FileResourceRepository;
import org.gradle.internal.resource.transfer.ExternalResourceConnector;
import org.gradle.internal.resource.transport.ResourceConnectorRepositoryTransport;
import org.gradle.internal.resource.transport.file.FileTransport;
import org.gradle.util.BuildCommencedTimeProvider;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class RepositoryTransportFactory {
    private final List<ResourceConnectorFactory> registeredProtocols = Lists.newArrayList();

    private final TemporaryFileProvider temporaryFileProvider;
    private final CachedExternalResourceIndex<String> cachedExternalResourceIndex;
    private final ProgressLoggerFactory progressLoggerFactory;
    private final BuildCommencedTimeProvider timeProvider;
    private final CacheLockingManager cacheLockingManager;
    private final BuildOperationExecutor buildOperationExecutor;
    private final StartParameterResolutionOverride startParameterResolutionOverride;
    private final ProducerGuard<ExternalResourceName> producerGuard;
    private final FileResourceRepository fileRepository;

    public RepositoryTransportFactory(Collection<ResourceConnectorFactory> resourceConnectorFactory,
                                      ProgressLoggerFactory progressLoggerFactory,
                                      TemporaryFileProvider temporaryFileProvider,
                                      CachedExternalResourceIndex<String> cachedExternalResourceIndex,
                                      BuildCommencedTimeProvider timeProvider,
                                      CacheLockingManager cacheLockingManager,
                                      BuildOperationExecutor buildOperationExecutor,
                                      StartParameterResolutionOverride startParameterResolutionOverride,
                                      ProducerGuard<ExternalResourceName> producerGuard,
                                      FileResourceRepository fileRepository) {
        this.progressLoggerFactory = progressLoggerFactory;
        this.temporaryFileProvider = temporaryFileProvider;
        this.cachedExternalResourceIndex = cachedExternalResourceIndex;
        this.timeProvider = timeProvider;
        this.cacheLockingManager = cacheLockingManager;
        this.buildOperationExecutor = buildOperationExecutor;
        this.startParameterResolutionOverride = startParameterResolutionOverride;
        this.producerGuard = producerGuard;
        this.fileRepository = fileRepository;

        for (ResourceConnectorFactory connectorFactory : resourceConnectorFactory) {
            register(connectorFactory);
        }
    }

    public void register(ResourceConnectorFactory resourceConnectorFactory) {
        registeredProtocols.add(resourceConnectorFactory);
    }

    public Set<String> getRegisteredProtocols() {
        Set<String> validSchemes = Sets.newLinkedHashSet();
        for (ResourceConnectorFactory registeredProtocol : registeredProtocols) {
            validSchemes.addAll(registeredProtocol.getSupportedProtocols());
        }
        return validSchemes;
    }

    public RepositoryTransport createTransport(String scheme, String name, Collection<Authentication> authentications) {
        return createTransport(Collections.singleton(scheme), name, authentications);
    }

    public RepositoryTransport createTransport(Set<String> schemes, String name, Collection<Authentication> authentications) {
        validateSchemes(schemes);

        ResourceConnectorFactory connectorFactory = findConnectorFactory(schemes);

        // Ensure resource transport protocol, authentication types and credentials are all compatible
        validateConnectorFactoryCredentials(schemes, connectorFactory, authentications);

        // File resources are handled slightly differently at present.
        // file:// repos are treated differently
        // 1) we don't cache their files
        // 2) we don't do progress logging for "downloading"
        if (schemes.equals(Collections.singleton("file"))) {
            return new FileTransport(name, fileRepository, cachedExternalResourceIndex, temporaryFileProvider, timeProvider, cacheLockingManager, producerGuard);
        }
        ResourceConnectorSpecification connectionDetails = new DefaultResourceConnectorSpecification(authentications);

        ExternalResourceConnector resourceConnector = connectorFactory.createResourceConnector(connectionDetails);
        resourceConnector = startParameterResolutionOverride.overrideExternalResourceConnnector(resourceConnector);

        ExternalResourceCachePolicy cachePolicy = new DefaultExternalResourceCachePolicy();
        cachePolicy = startParameterResolutionOverride.overrideExternalResourceCachePolicy(cachePolicy);

        return new ResourceConnectorRepositoryTransport(name, progressLoggerFactory, temporaryFileProvider, cachedExternalResourceIndex, timeProvider, cacheLockingManager, resourceConnector, buildOperationExecutor, cachePolicy, producerGuard, fileRepository);
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
        Set<Class<? extends Authentication>> configuredAuthenticationTypes = Sets.newHashSet();

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

    private class DefaultResourceConnectorSpecification implements ResourceConnectorSpecification {
        private final Collection<Authentication> authentications;

        private DefaultResourceConnectorSpecification(Collection<Authentication> authentications) {
            this.authentications = authentications;
        }

        @Override
        public <T> T getCredentials(Class<T> type) {
            if (authentications == null || authentications.size() < 1) {
                return null;
            }

            Credentials credentials = ((AuthenticationInternal)authentications.iterator().next()).getCredentials();

            if(credentials == null) {
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
    }
}

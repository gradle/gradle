/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.jvm.toolchain.internal;

import org.gradle.api.Action;
import org.gradle.api.GradleException;
import org.gradle.api.invocation.Gradle;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.services.BuildServiceParameters;
import org.gradle.api.services.BuildServiceRegistry;
import org.gradle.api.services.BuildServiceSpec;
import org.gradle.internal.authentication.AuthenticationSchemeRegistry;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.jvm.toolchain.JavaToolchainRepository;
import org.gradle.jvm.toolchain.JavaToolchainResolver;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public abstract class DefaultJavaToolchainResolverRegistry implements JavaToolchainResolverRegistryInternal {

    private static final Action<BuildServiceSpec<BuildServiceParameters.None>> EMPTY_CONFIGURE_ACTION = buildServiceSpec -> {
    };

    private final BuildServiceRegistry sharedServices;

    private final DefaultJavaToolchainRepositoryHandler repositoryHandler;

    private final List<RealizedJavaToolchainRepository> realizedRepositories = new ArrayList<>();

    private final Map<Class<? extends JavaToolchainResolver>, Provider<? extends JavaToolchainResolver>> registrations = new HashMap<>();

    @Inject
    public DefaultJavaToolchainResolverRegistry(
            Gradle gradle,
            Instantiator instantiator,
            ObjectFactory objectFactory,
            ProviderFactory providerFactory,
            AuthenticationSchemeRegistry authenticationSchemeRegistry
    ) {
        this.sharedServices = gradle.getSharedServices();
        this.repositoryHandler = objectFactory.newInstance(DefaultJavaToolchainRepositoryHandler.class, instantiator, objectFactory, providerFactory, authenticationSchemeRegistry);
    }

    @Override
    public JavaToolchainRepositoryHandlerInternal getRepositories() {
        return repositoryHandler;
    }

    @Override
    public <T extends JavaToolchainResolver> void register(Class<T> implementationType) {
        if (registrations.containsKey(implementationType)) {
            throw new GradleException("Duplicate registration for '" + implementationType.getName() + "'.");
        }

        Provider<T> provider = sharedServices.registerIfAbsent(implementationType.getName(), implementationType, EMPTY_CONFIGURE_ACTION);
        registrations.put(implementationType, provider);
    }

    @Override
    public List<RealizedJavaToolchainRepository> requestedRepositories() {
        if (realizedRepositories.size() != repositoryHandler.size()) {
            realizeRepositories();
        }
        return realizedRepositories;
    }

    @Override
    public void preventFromFurtherMutation() {
        repositoryHandler.preventFromFurtherMutation();
        // This makes sure all configured elements have been transformed in their internal representation for later use
        realizeRepositories();
    }

    private void realizeRepositories() {
        realizedRepositories.clear();

        Set<Class<?>> resolvers = new HashSet<>();
        for (JavaToolchainRepository repository : repositoryHandler.getAsList()) {
            if (!resolvers.add(repository.getResolverClass().get())) {
                throw new GradleException("Duplicate configuration for repository implementation '" + repository.getResolverClass().get().getName() + "'.");
            }
            realizedRepositories.add(realize(repository));
        }
    }

    private RealizedJavaToolchainRepository realize(JavaToolchainRepository repository) {
        Class<? extends JavaToolchainResolver> repositoryClass = getResolverClass(repository);
        Provider<? extends JavaToolchainResolver> provider = findProvider(repositoryClass);
        return new RealizedJavaToolchainRepository(provider, (JavaToolchainRepositoryInternal) repository);
    }

    private Provider<? extends JavaToolchainResolver> findProvider(Class<? extends JavaToolchainResolver> repositoryClass) {
        Provider<? extends JavaToolchainResolver> provider = registrations.get(repositoryClass);
        if (provider == null) {
            throw new GradleException("Class " + repositoryClass.getName() + " hasn't been registered as a Java toolchain repository");
        }
        return provider;
    }

    private static Class<? extends JavaToolchainResolver> getResolverClass(JavaToolchainRepository repository) {
        Property<Class<? extends JavaToolchainResolver>> resolverClassProperty = repository.getResolverClass();
        resolverClassProperty.finalizeValueOnRead();
        if (!resolverClassProperty.isPresent()) {
            throw new GradleException("Java toolchain repository `" + repository.getName() + "` must have the `resolverClass` property set");
        }
        return resolverClassProperty.get();
    }

}

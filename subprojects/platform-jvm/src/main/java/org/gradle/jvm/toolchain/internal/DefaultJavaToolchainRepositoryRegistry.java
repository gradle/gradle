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
import org.gradle.jvm.toolchain.JavaToolchainRepositoryResolver;
import org.gradle.jvm.toolchain.JavaToolchainRepositoryResolverHandler;

import javax.inject.Inject;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public abstract class DefaultJavaToolchainRepositoryRegistry implements JavaToolchainRepositoryRegistryInternal {

    private static final Action<BuildServiceSpec<BuildServiceParameters.None>> EMPTY_CONFIGURE_ACTION = buildServiceSpec -> {
    };

    private final BuildServiceRegistry sharedServices;

    private final DefaultJavaToolchainRepositoryResolverHandler resolvers;

    private final Map<Class<? extends JavaToolchainRepository>, Provider<? extends JavaToolchainRepository>> registrations = new HashMap<>();

    @Inject
    public DefaultJavaToolchainRepositoryRegistry(
            Gradle gradle,
            Instantiator instantiator,
            ObjectFactory objectFactory,
            ProviderFactory providerFactory,
            AuthenticationSchemeRegistry authenticationSchemeRegistry
    ) {
        this.sharedServices = gradle.getSharedServices();
        this.resolvers = objectFactory.newInstance(DefaultJavaToolchainRepositoryResolverHandler.class, this, instantiator, objectFactory, providerFactory, authenticationSchemeRegistry);
    }

    @Override
    public JavaToolchainRepositoryResolverHandler getResolvers() {
        return resolvers;
    }

    @Override
    public <T extends JavaToolchainRepository> void register(Class<T> implementationType) {
        if (registrations.containsKey(implementationType)) {
            throw new GradleException("Duplicate registration for '" + implementationType.getName() + "'.");
        }

        Provider<T> provider = sharedServices.registerIfAbsent(implementationType.getName(), implementationType, EMPTY_CONFIGURE_ACTION);
        registrations.put(implementationType, provider);
    }

    @Override
    public List<ResolvedJavaToolchainRepository> requestedRepositories() {
        return resolvers.stream()
                .map(this::resolve)
                .collect(Collectors.toList());
    }

    private ResolvedJavaToolchainRepository resolve(JavaToolchainRepositoryResolver resolver) {
        Class<? extends JavaToolchainRepository> repositoryClass = getRepositoryClass(resolver);
        Provider<? extends JavaToolchainRepository> provider = findProvider(repositoryClass);
        return new ResolvedJavaToolchainRepository(provider, (JavaToolchainRepositoryResolverInternal) resolver);
    }

    private Provider<? extends JavaToolchainRepository> findProvider(Class<? extends JavaToolchainRepository> repositoryClass) {
        Provider<? extends JavaToolchainRepository> provider = registrations.get(repositoryClass);
        if (provider == null) {
            throw new GradleException("Class " + repositoryClass.getName() + " hasn't been registered as a Java toolchain repository");
        }
        return provider;
    }

    private static Class<? extends JavaToolchainRepository> getRepositoryClass(JavaToolchainRepositoryResolver resolver) {
        Property<Class<? extends JavaToolchainRepository>> implementationClassProperty = resolver.getImplementationClass();
        implementationClassProperty.finalizeValueOnRead();
        if (!implementationClassProperty.isPresent()) {
            throw new GradleException("Java toolchain repository `" + resolver.getName() + "` must have the `implementationClass` property set");
        }
        return implementationClassProperty.get();
    }

}

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
import org.gradle.api.Namer;
import org.gradle.api.internal.CollectionCallbackActionDecorator;
import org.gradle.api.internal.DefaultNamedDomainObjectList;
import org.gradle.api.internal.artifacts.repositories.AuthenticationSupporter;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.authentication.Authentication;
import org.gradle.internal.authentication.AuthenticationSchemeRegistry;
import org.gradle.internal.authentication.DefaultAuthenticationContainer;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.jvm.toolchain.JavaToolchainRepository;
import org.gradle.jvm.toolchain.JavaToolchainRepositoryHandler;

import javax.inject.Inject;
import java.util.Map;

public class DefaultJavaToolchainRepositoryHandler extends DefaultNamedDomainObjectList<JavaToolchainRepository>
        implements JavaToolchainRepositoryHandler {

    private final JavaToolchainResolverRegistryInternal registry;

    private final Instantiator instantiator;

    private final ObjectFactory objectFactory;

    private final ProviderFactory providerFactory;

    private final AuthenticationSchemeRegistry authenticationSchemeRegistry;

    @Inject
    public DefaultJavaToolchainRepositoryHandler(
            JavaToolchainResolverRegistryInternal registry,
            Instantiator instantiator,
            ObjectFactory objectFactory,
            ProviderFactory providerFactory,
            AuthenticationSchemeRegistry authenticationSchemeRegistry
    ) {
        super(JavaToolchainRepository.class, instantiator, new RepositoryNamer(), CollectionCallbackActionDecorator.NOOP);
        this.registry = registry;
        this.instantiator = instantiator;
        this.objectFactory = objectFactory;
        this.providerFactory = providerFactory;
        this.authenticationSchemeRegistry = authenticationSchemeRegistry;
    }

    private static class RepositoryNamer implements Namer<JavaToolchainRepository> {
        @Override
        public String determineName(JavaToolchainRepository repository) {
            return repository.getName();
        }
    }

    @Override
    public void repository(String name, Action<? super JavaToolchainRepository> configureAction) {
        DefaultAuthenticationContainer authenticationContainer = new DefaultAuthenticationContainer(instantiator, CollectionCallbackActionDecorator.NOOP);
        for (Map.Entry<Class<Authentication>, Class<? extends Authentication>> e : authenticationSchemeRegistry.getRegisteredSchemes().entrySet()) {
            authenticationContainer.registerBinding(e.getKey(), e.getValue());
        }
        AuthenticationSupporter authenticationSupporter = new AuthenticationSupporter(instantiator, objectFactory, authenticationContainer, providerFactory);

        DefaultJavaToolchainRepository repository = objectFactory.newInstance(DefaultJavaToolchainRepository.class, name, authenticationContainer, authenticationSupporter, providerFactory);
        configureAction.execute(repository);

        boolean isNew = registry.getRepositories().add(repository);
        if (!isNew) {
            throw new GradleException("Duplicate configuration for repository '" + name + "'.");
        }
    }

    @Override
    public String getTypeDisplayName() {
        return "repository";
    }
}

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
import org.gradle.api.InvalidUserCodeException;
import org.gradle.api.Namer;
import org.gradle.api.artifacts.repositories.AuthenticationContainer;
import org.gradle.api.artifacts.repositories.PasswordCredentials;
import org.gradle.api.credentials.Credentials;
import org.gradle.api.internal.CollectionCallbackActionDecorator;
import org.gradle.api.internal.DefaultNamedDomainObjectList;
import org.gradle.api.internal.artifacts.repositories.AuthenticationSupporter;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.authentication.Authentication;
import org.gradle.internal.authentication.AuthenticationSchemeRegistry;
import org.gradle.internal.authentication.DefaultAuthenticationContainer;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.jvm.toolchain.JavaToolchainRepository;
import org.gradle.jvm.toolchain.JavaToolchainResolver;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class DefaultJavaToolchainRepositoryHandler implements JavaToolchainRepositoryHandlerInternal {

    private final DefaultNamedDomainObjectList<JavaToolchainRepository> repositories;

    private final Instantiator instantiator;

    private final ObjectFactory objectFactory;

    private final ProviderFactory providerFactory;

    private final AuthenticationSchemeRegistry authenticationSchemeRegistry;

    private boolean mutable = true;

    @Inject
    public DefaultJavaToolchainRepositoryHandler(
            Instantiator instantiator,
            ObjectFactory objectFactory,
            ProviderFactory providerFactory,
            AuthenticationSchemeRegistry authenticationSchemeRegistry
    ) {
        this.repositories = new DefaultNamedDomainObjectList<JavaToolchainRepository>(JavaToolchainRepository.class, instantiator, new RepositoryNamer(), CollectionCallbackActionDecorator.NOOP) {
            @Override
            public String getTypeDisplayName() {
                return "repository";
            }
        };
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
        assertMutable();

        DefaultAuthenticationContainer authenticationContainer = new DefaultAuthenticationContainer(instantiator, CollectionCallbackActionDecorator.NOOP);
        for (Map.Entry<Class<Authentication>, Class<? extends Authentication>> e : authenticationSchemeRegistry.getRegisteredSchemes().entrySet()) {
            authenticationContainer.registerBinding(e.getKey(), e.getValue());
        }
        AuthenticationSupporter authenticationSupporter = new AuthenticationSupporter(instantiator, objectFactory, authenticationContainer, providerFactory);

        DefaultJavaToolchainRepository repository = objectFactory.newInstance(DefaultJavaToolchainRepository.class, name, authenticationContainer, authenticationSupporter, providerFactory);
        configureAction.execute(repository);

        boolean isNew = repositories.add(repository);
        if (!isNew) {
            throw new GradleException("Duplicate configuration for repository '" + name + "'.");
        }
    }

    @Override
    public List<JavaToolchainRepository> getAsList() {
        ArrayList<JavaToolchainRepository> copy = repositories.stream()
                .map(it -> (JavaToolchainRepositoryInternal) it)
                .map(ImmutableJavaToolchainRepository::new)
                .collect(Collectors.toCollection(ArrayList::new));
        return Collections.unmodifiableList(copy);
    }

    @Override
    public int size() {
        return repositories.size();
    }

    @Override
    public boolean remove(String name) {
        assertMutable();

        JavaToolchainRepository repository = repositories.findByName(name);
        if (repository == null) {
            return false;
        }

        return repositories.remove(repository);
    }

    @Override
    public void preventFromFurtherMutation() {
        this.mutable = false;
    }

    private void assertMutable() {
        if (!mutable) {
            throw new InvalidUserCodeException("Mutation of toolchain repositories declared in settings is only allowed during settings evaluation");
        }
    }

    private static class ImmutableJavaToolchainRepository implements JavaToolchainRepositoryInternal {

        private final JavaToolchainRepositoryInternal delegate;

        public ImmutableJavaToolchainRepository(JavaToolchainRepositoryInternal delegate) {
            this.delegate = delegate;
        }

        @Override
        public Collection<Authentication> getConfiguredAuthentication() {
            return delegate.getConfiguredAuthentication();
        }

        @Override
        public PasswordCredentials getCredentials() {
            return delegate.getCredentials();
        }

        @Override
        public <T extends Credentials> T getCredentials(Class<T> credentialsType) {
            return delegate.getCredentials(credentialsType);
        }

        @Override
        public void credentials(Action<? super PasswordCredentials> action) {
            throw new UnsupportedOperationException("Can't modify repositories through a read-only view");
        }

        @Override
        public <T extends Credentials> void credentials(Class<T> credentialsType, Action<? super T> action) {
            throw new UnsupportedOperationException("Can't modify repositories through a read-only view");
        }

        @Override
        public void credentials(Class<? extends Credentials> credentialsType) {
            throw new UnsupportedOperationException("Can't modify repositories through a read-only view");
        }

        @Override
        public void authentication(Action<? super AuthenticationContainer> action) {
            throw new UnsupportedOperationException("Can't modify repositories through a read-only view");
        }

        @Override
        public AuthenticationContainer getAuthentication() {
            return delegate.getAuthentication();
        }

        @Override
        public String getName() {
            return delegate.getName();
        }

        @Override
        public Property<Class<? extends JavaToolchainResolver>> getResolverClass() {
            return delegate.getResolverClass();
        }
    }

}

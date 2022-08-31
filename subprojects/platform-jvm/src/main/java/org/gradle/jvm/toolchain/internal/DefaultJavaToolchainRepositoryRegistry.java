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

import com.google.common.collect.ImmutableList;
import org.gradle.api.Action;
import org.gradle.api.GradleException;
import org.gradle.api.artifacts.repositories.AuthenticationSupported;
import org.gradle.api.internal.CollectionCallbackActionDecorator;
import org.gradle.api.internal.artifacts.repositories.AuthenticationSupporter;
import org.gradle.api.invocation.Gradle;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.services.BuildServiceParameters;
import org.gradle.api.services.BuildServiceRegistry;
import org.gradle.api.services.BuildServiceSpec;
import org.gradle.api.toolchain.management.JavaToolchainRepositoryRegistration;
import org.gradle.authentication.Authentication;
import org.gradle.internal.authentication.AuthenticationSchemeRegistry;
import org.gradle.internal.authentication.DefaultAuthenticationContainer;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.jvm.toolchain.JavaToolchainRepository;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class DefaultJavaToolchainRepositoryRegistry implements JavaToolchainRepositoryRegistryInternal {

    private static final Action<BuildServiceSpec<BuildServiceParameters.None>> EMPTY_CONFIGURE_ACTION = buildServiceSpec -> {
    };

    private final BuildServiceRegistry sharedServices;

    private final JavaToolchainRepositoryRegistrationListener registrationBroadcaster;

    private final Instantiator instantiator;

    private final ObjectFactory objectFactory;

    private final ProviderFactory providerFactory;

    private final AuthenticationSchemeRegistry authenticationSchemeRegistry;

    private final Map<String, JavaToolchainRepositoryRegistrationInternal> registrations = new HashMap<>();

    private final List<JavaToolchainRepositoryRequest> requests = new ArrayList<>();

    @Inject
    public DefaultJavaToolchainRepositoryRegistry(
            Gradle gradle,
            JavaToolchainRepositoryRegistrationListener registrationBroadcaster,
            Instantiator instantiator,
            ObjectFactory objectFactory,
            ProviderFactory providerFactory,
            AuthenticationSchemeRegistry authenticationSchemeRegistry
    ) {
        this.sharedServices = gradle.getSharedServices();
        this.registrationBroadcaster = registrationBroadcaster;
        this.instantiator = instantiator;
        this.objectFactory = objectFactory;
        this.providerFactory = providerFactory;
        this.authenticationSchemeRegistry = authenticationSchemeRegistry;
    }

    @Override
    public <T extends JavaToolchainRepository> void register(String name, Class<T> implementationType) {
        if (registrations.containsKey(name)) {
            throw new GradleException("Duplicate " + JavaToolchainRepository.class.getSimpleName() + " registration under the name '" + name + "'");
        }

        Provider<T> provider = sharedServices.registerIfAbsent(name, implementationType, EMPTY_CONFIGURE_ACTION);
        JavaToolchainRepositoryRegistrationInternal registration = new DefaultJavaToolchainRepositoryRegistration(name, provider);
        registrations.put(name, registration);

        registrationBroadcaster.onRegister(registration);
    }

    @Override
    public void request(String registrationName) {
        request(findRegistrationByName(registrationName));
    }

    @Override
    public void request(String registrationName, Action<? super AuthenticationSupported> authentication) {
        request(findRegistrationByName(registrationName), authentication);
    }

    @Override
    public void request(JavaToolchainRepositoryRegistration registration) {
        requests.add(createRequest((JavaToolchainRepositoryRegistrationInternal) registration));
    }

    @Override
    public void request(JavaToolchainRepositoryRegistration registration, Action<? super AuthenticationSupported> authentication) {
        JavaToolchainRepositoryRequest request = createRequest((JavaToolchainRepositoryRegistrationInternal) registration);
        authentication.execute(request);
        requests.add(request);
    }

    private JavaToolchainRepositoryRequest createRequest(JavaToolchainRepositoryRegistrationInternal registration) {
        DefaultAuthenticationContainer authenticationContainer = new DefaultAuthenticationContainer(instantiator, CollectionCallbackActionDecorator.NOOP);
        for (Map.Entry<Class<Authentication>, Class<? extends Authentication>> e : authenticationSchemeRegistry.getRegisteredSchemes().entrySet()) {
            authenticationContainer.registerBinding(e.getKey(), e.getValue());
        }

        AuthenticationSupporter authenticationSupporter = new AuthenticationSupporter(instantiator, objectFactory, authenticationContainer, providerFactory);
        return objectFactory.newInstance(JavaToolchainRepositoryRequest.class, registration, authenticationSupporter, providerFactory);
    }

    @Override
    public List<? extends JavaToolchainRepositoryRegistration> allRequestedRegistrations() {
        return requests.stream()
                .map(JavaToolchainRepositoryRequest::getRegistration)
                .map(r -> new ViewOnlyJavaToolchainRepositoryRegistration(r.getName(), r.getType()))
                .collect(ImmutableList.toImmutableList());
    }

    @Override
    public List<JavaToolchainRepositoryRequest> requestedRepositories() {
        return requests; //TODO (#21082): do we need to create a defensive copy here?
    }

    private JavaToolchainRepositoryRegistrationInternal findRegistrationByName(String registrationName) {
        JavaToolchainRepositoryRegistrationInternal registration = registrations.get(registrationName);
        if (registration == null) {
            throw new GradleException("Unknown Java Toolchain registry: " + registrationName);
        }
        return registration;
    }

    private static final class ViewOnlyJavaToolchainRepositoryRegistration implements JavaToolchainRepositoryRegistration {

        private final String name;

        private final String type;

        public ViewOnlyJavaToolchainRepositoryRegistration(String name, String type) {
            this.name = name;
            this.type = type;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public String getType() {
            return type;
        }
    }

}

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
import org.gradle.api.plugins.ExtensionContainer;
import org.gradle.api.provider.Provider;
import org.gradle.api.services.BuildServiceParameters;
import org.gradle.api.services.BuildServiceRegistry;
import org.gradle.api.services.BuildServiceSpec;
import org.gradle.api.toolchain.management.JavaToolchainRepositoryRegistration;
import org.gradle.api.toolchain.management.ToolchainManagementSpec;
import org.gradle.internal.extensibility.ExtensibleDynamicObject;
import org.gradle.internal.instantiation.InstanceGenerator;
import org.gradle.jvm.toolchain.JavaToolchainRepository;
import org.gradle.jvm.toolchain.internal.install.AdoptOpenJdkRemoteBinary;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class DefaultToolchainManagementSpec implements ToolchainManagementSpecInternal, JavaToolchainRepositoryRegistryInternal {

    public static final String DEFAULT_REGISTRY_NAME = "adoptOpenJdk";

    private static final Action<BuildServiceSpec<BuildServiceParameters.None>> EMPTY_CONFIGURE_ACTION = buildServiceSpec -> {
    };

    private final BuildServiceRegistry sharedServices;

    private final Map<String, JavaToolchainRepositoryRegistrationInternal> registrations = new HashMap<>();

    private final List<JavaToolchainRepositoryRegistrationInternal> requests = new ArrayList<>();

    private final ExtensibleDynamicObject extensibleDynamicObject;

    @Inject
    public DefaultToolchainManagementSpec(Gradle gradle, InstanceGenerator instanceGenerator) {
        this.extensibleDynamicObject = new ExtensibleDynamicObject(this, ToolchainManagementSpec.class, instanceGenerator);

        this.sharedServices = gradle.getSharedServices();
        register(DEFAULT_REGISTRY_NAME, AdoptOpenJdkRemoteBinary.class); //our default implementation, should go away in 8.0
    }

    @Override
    public void jdks(String... registrationNames) {
        validateNames(registrationNames);
        requests.addAll(Arrays.stream(registrationNames)
                .map(registrations::get)
                .collect(Collectors.toList()));
    }

    @Override
    public void jdks(JavaToolchainRepositoryRegistration... registrations) {
        requests.addAll(Arrays.stream(registrations)
                .map(registration -> (JavaToolchainRepositoryRegistrationInternal) registration) //TODO (#21082): I don't like needing this cast... (bad design?)
                .collect(Collectors.toList()));
    }

    @Override
    public <T extends JavaToolchainRepository> void register(String name, Class<T> implementationType) {
        //TODO (#21082): make sure there is no name duplication in registrations (and write test for it)

        Provider<T> provider = sharedServices.registerIfAbsent(name, implementationType, EMPTY_CONFIGURE_ACTION);
        JavaToolchainRepositoryRegistrationInternal registration = new DefaultJavaToolchainRepositoryRegistration(name, provider);
        registrations.put(name, registration);

        getExtensions().add(JavaToolchainRepositoryRegistration.class, name, registration);
    }

    @Override
    public boolean hasExplicitRequests() {
        return !requests.isEmpty();
    }

    @Override
    public List<JavaToolchainRepository> requestedRepositories() {
        if (requests.isEmpty()) {
            return Collections.singletonList(registrations.get(DEFAULT_REGISTRY_NAME).getProvider().get());
        }

        return requests.stream()
                .map(JavaToolchainRepositoryRegistrationInternal::getProvider)
                .map(Provider::get)
                .collect(Collectors.toList());
    }

    private void validateNames(String... registryNames) {
        for (String name : registryNames) {
            if (!registrations.containsKey(name)) {
                throw new GradleException("Unknown Java Toolchain registry: " + name);
            }
        }
    }

    @Override
    public ExtensionContainer getExtensions() {
        return extensibleDynamicObject.getConvention(); //TODO (#21082): deprecated? what else should I use?
    }
}

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
import org.gradle.api.provider.Provider;
import org.gradle.api.services.BuildServiceParameters;
import org.gradle.api.services.BuildServiceRegistry;
import org.gradle.api.services.BuildServiceSpec;
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

public class DefaultJavaToolchainRepositoryRegistry implements JavaToolchainRepositoryRegistryInternal {

    public static final String DEFAULT_REGISTRY_NAME = "adoptOpenJdk";

    private static final Action<BuildServiceSpec<BuildServiceParameters.None>> EMPTY_CONFIGURE_ACTION = buildServiceSpec -> {
    };

    private final BuildServiceRegistry sharedServices;

    private final Map<String, Provider<? extends JavaToolchainRepository>> registrations = new HashMap<>();

    private final List<String> requests = new ArrayList<>();

    @Inject
    public DefaultJavaToolchainRepositoryRegistry(Gradle gradle) {
        this.sharedServices = gradle.getSharedServices();
        register(DEFAULT_REGISTRY_NAME, AdoptOpenJdkRemoteBinary.class); //our default implementation, should go away in 8.0
    }

    @Override
    public <T extends JavaToolchainRepository> void register(String name, Class<T> implementationType) {
        Provider<T> provider = sharedServices.registerIfAbsent(name, implementationType, EMPTY_CONFIGURE_ACTION);
        registrations.put(name, provider);
    }

    @Override
    public void request(String... registryNames) {
        validateNames(registryNames);
        requests.addAll(Arrays.asList(registryNames));
    }

    @Override
    public boolean hasExplicitRequests() {
        return !requests.isEmpty();
    }

    @Override
    public List<JavaToolchainRepository> requestedRepositories() {
        if (requests.isEmpty()) {
            return Collections.singletonList(registrations.get(DEFAULT_REGISTRY_NAME).get());
        }

        return requests.stream().map(name -> registrations.get(name).get()).collect(Collectors.toList());
    }

    private void validateNames(String... registryNames) {
        for (String name : registryNames) {
            if (!registrations.containsKey(name)) {
                throw new GradleException("Unknown Java Toolchain registry: " + name);
            }
        }
    }

}

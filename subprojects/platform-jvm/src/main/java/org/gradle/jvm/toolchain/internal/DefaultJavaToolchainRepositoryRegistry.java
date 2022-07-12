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
import org.gradle.api.invocation.Gradle;
import org.gradle.api.provider.Provider;
import org.gradle.api.services.BuildServiceParameters;
import org.gradle.api.services.BuildServiceRegistry;
import org.gradle.api.services.BuildServiceSpec;
import org.gradle.jvm.toolchain.JavaToolchainRepository;

import javax.inject.Inject;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class DefaultJavaToolchainRepositoryRegistry implements JavaToolchainRepositoryRegistryInternal {

    public static final Action<BuildServiceSpec<BuildServiceParameters.None>> EMPTY_CONFIGURE_ACTION = buildServiceSpec -> {
    };
    private final BuildServiceRegistry sharedServices;

    private final Map<String, Provider<? extends JavaToolchainRepository>> registrations = new HashMap<>();

    @Inject
    public DefaultJavaToolchainRepositoryRegistry(Gradle gradle) {
        this.sharedServices = gradle.getSharedServices();
    }

    @Override
    public <T extends JavaToolchainRepository> void register(String name, Class<T> implementationType) {
        Provider<T> provider = sharedServices.registerIfAbsent(name, implementationType, EMPTY_CONFIGURE_ACTION);
        registrations.put(name, provider);
    }

    @Override
    public List<JavaToolchainRepository> requestedRepositories() {
        //TODO: this is a hack, for now we consider that all repositories registered are also requested
        return registrations.values().stream().map(Provider::get).collect(Collectors.toList());
    }
}

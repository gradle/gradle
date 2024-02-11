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

import org.gradle.api.provider.Provider;
import org.gradle.authentication.Authentication;
import org.gradle.internal.authentication.AuthenticationInternal;
import org.gradle.jvm.toolchain.JavaToolchainResolver;

import javax.inject.Inject;
import java.net.URI;
import java.util.Collection;

public class RealizedJavaToolchainRepository {

    private final Provider<? extends JavaToolchainResolver> resolverProvider;

    private final JavaToolchainRepositoryInternal repository;

    @Inject
    public RealizedJavaToolchainRepository(Provider<? extends JavaToolchainResolver> resolverProvider, JavaToolchainRepositoryInternal repository) {
        this.resolverProvider = resolverProvider;
        this.repository = repository;
    }

    public JavaToolchainResolver getResolver() {
        return resolverProvider.get();
    }

    public Collection<Authentication> getAuthentications(URI uri) {
        Collection<Authentication> configuredAuthentication = repository.getConfiguredAuthentication();

        for (Authentication authentication : configuredAuthentication) {
            AuthenticationInternal authenticationInternal = (AuthenticationInternal) authentication;
            if (uri.getScheme().startsWith("http")) {
                authenticationInternal.addHost(uri.getHost(), uri.getPort());
            }
        }
        return configuredAuthentication;
    }
}

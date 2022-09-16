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
import org.gradle.jvm.toolchain.JavaToolchainRepository;

import javax.inject.Inject;
import java.net.URI;
import java.util.Collection;

public class ResolvedJavaToolchainRepository {

    private final Provider<? extends JavaToolchainRepository> repositoryProvider;

    private final JavaToolchainRepositoryResolverInternal resolver;

    @Inject
    public ResolvedJavaToolchainRepository(Provider<? extends JavaToolchainRepository> repositoryProvider, JavaToolchainRepositoryResolverInternal resolver) {
        this.repositoryProvider = repositoryProvider;
        this.resolver = resolver;
    }

    public JavaToolchainRepository getRepository() {
        return repositoryProvider.get();
    }

    public Collection<Authentication> getAuthentications(URI uri) {
        Collection<Authentication> configuredAuthentication = resolver.getConfiguredAuthentication();

        for (Authentication authentication : configuredAuthentication) {
            AuthenticationInternal authenticationInternal = (AuthenticationInternal) authentication;
            if (uri.getScheme().startsWith("http")) {
                authenticationInternal.addHost(uri.getHost(), uri.getPort());
            }
        }
        return configuredAuthentication;
    }
}

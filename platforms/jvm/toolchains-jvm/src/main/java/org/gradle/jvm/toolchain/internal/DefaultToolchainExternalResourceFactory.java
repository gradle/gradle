/*
 * Copyright 2024 the original author or authors.
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

import org.gradle.api.internal.artifacts.repositories.transport.RepositoryTransportFactory;
import org.gradle.authentication.Authentication;
import org.gradle.internal.resource.ExternalResourceFactory;
import org.gradle.internal.resource.ExternalResourceRepository;
import org.gradle.internal.verifier.HttpRedirectVerifier;
import org.gradle.jvm.toolchain.internal.install.JavaToolchainHttpRedirectVerifierFactory;

import java.net.URI;
import java.util.Collection;

public class DefaultToolchainExternalResourceFactory implements ExternalResourceFactory {

    private final RepositoryTransportFactory repositoryTransportFactory;
    private final JavaToolchainHttpRedirectVerifierFactory httpRedirectVerifierFactory;

    public DefaultToolchainExternalResourceFactory(RepositoryTransportFactory repositoryTransportFactory, JavaToolchainHttpRedirectVerifierFactory httpRedirectVerifierFactory) {
        this.repositoryTransportFactory = repositoryTransportFactory;
        this.httpRedirectVerifierFactory = httpRedirectVerifierFactory;
    }

    @Override
    public ExternalResourceRepository createExternalResource(URI source, Collection<Authentication> authentications) {
        final HttpRedirectVerifier redirectVerifier = httpRedirectVerifierFactory.createVerifier(source);
        return repositoryTransportFactory.createTransport("https", "jdk toolchains", authentications, redirectVerifier).getRepository();
    }
}

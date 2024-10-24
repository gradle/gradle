/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.api.internal.artifacts.repositories;

import org.gradle.api.InvalidUserCodeException;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.artifacts.repositories.UrlArtifactRepository;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.internal.deprecation.Documentation;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;
import org.gradle.internal.verifier.HttpRedirectVerifier;
import org.gradle.internal.verifier.HttpRedirectVerifierFactory;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import javax.inject.Inject;
import java.net.URI;
import java.util.function.Supplier;

public class DefaultUrlArtifactRepository implements UrlArtifactRepository {

    private final Property<URI> url;
    private final Property<Boolean> allowInsecureProtocol;
    private final String repositoryType;
    private final FileResolver fileResolver;
    private final Supplier<String> displayNameSupplier;
    private final ProviderFactory providers;

    DefaultUrlArtifactRepository(
        final FileResolver fileResolver,
        final ObjectFactory objectFactory,
        final ProviderFactory providers,
        final String repositoryType,
        final Supplier<String> displayNameSupplier
    ) {
        this.fileResolver = fileResolver;
        this.providers = providers;
        this.repositoryType = repositoryType;
        this.displayNameSupplier = displayNameSupplier;
        this.url = objectFactory.property(URI.class);
        this.allowInsecureProtocol = objectFactory.property(Boolean.class).convention(false);
    }

    @Override
    public Property<URI> getUrl() {
        return url;
    }

    @SuppressWarnings("unchecked")
    @Override
    public void setUrl(Object url) {
        // TODO: Handle deprecation in 9.0
        // ProviderApiDeprecationLogger.logDeprecation(UrlArtifactRepository.class, "setUrl(Object)", "getUrl");
        if (url instanceof URI) {
            this.getUrl().set((URI) url);
        } else if (url instanceof Provider) {
            this.getUrl().set(((Provider<Object>) url).map(uri -> uri instanceof URI
                ? (URI) uri
                : fileResolver.resolveUri(uri)
            ));
        } else {
            this.getUrl().set(providers.provider(() -> fileResolver.resolveUri(url)));
        }
    }

    @Override
    public Property<Boolean> getAllowInsecureProtocol() {
        return allowInsecureProtocol;
    }

    @NonNull
    public URI validateUrl() {
        URI rootUri = getUrl().getOrNull();
        if (rootUri == null) {
            throw new InvalidUserDataException(String.format(
                "You must specify a URL for a %s repository.",
                repositoryType
            ));
        }
        return rootUri;
    }

    private void throwExceptionDueToInsecureProtocol() throws InvalidUserCodeException {
        throw new InsecureProtocolException(
            "Using insecure protocols with repositories, without explicit opt-in, is unsupported.",
            String.format("Switch %s repository '%s' to redirect to a secure protocol (like HTTPS) or allow insecure protocols.", repositoryType, displayNameSupplier.get()),
            Documentation.dslReference(UrlArtifactRepository.class, "allowInsecureProtocol").getConsultDocumentationMessage()
        );
    }

    private void throwExceptionDueToInsecureRedirect(@Nullable URI redirectFrom, URI redirectLocation) throws InvalidUserCodeException {
        final String contextualAdvice;
        if (redirectFrom != null) {
            contextualAdvice = String.format(
                " '%s' is redirecting to '%s'. ",
                redirectFrom,
                redirectLocation
            );
        } else {
            contextualAdvice = "";
        }
        throw new InsecureProtocolException(
            "Redirecting from secure protocol to insecure protocol, without explicit opt-in, is unsupported." + contextualAdvice,
            String.format("Switch %s repository '%s' to redirect to a secure protocol (like HTTPS) or allow insecure protocols. ", repositoryType, displayNameSupplier.get()),
            Documentation.dslReference(UrlArtifactRepository.class, "allowInsecureProtocol").getConsultDocumentationMessage()
        );
    }

    HttpRedirectVerifier createRedirectVerifier() {
        URI uri = getUrl().getOrNull();
        return HttpRedirectVerifierFactory
            .create(
                uri,
                getAllowInsecureProtocol().get(),
                this::throwExceptionDueToInsecureProtocol,
                redirection -> throwExceptionDueToInsecureRedirect(uri, redirection)
            );
    }

    @ServiceScope(Scope.Project.class)
    public static class Factory {
        private final FileResolver fileResolver;
        private final ObjectFactory objectFactory;
        private final ProviderFactory providers;

        @Inject
        public Factory(FileResolver fileResolver, ObjectFactory objectFactory, ProviderFactory providers) {
            this.fileResolver = fileResolver;
            this.objectFactory = objectFactory;
            this.providers = providers;
        }

        DefaultUrlArtifactRepository create(String repositoryType, Supplier<String> displayNameSupplier) {
            return new DefaultUrlArtifactRepository(fileResolver, objectFactory, providers, repositoryType, displayNameSupplier);
        }
    }
}

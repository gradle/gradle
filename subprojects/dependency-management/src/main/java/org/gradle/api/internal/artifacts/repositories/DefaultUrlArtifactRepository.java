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

import org.gradle.api.InvalidUserDataException;
import org.gradle.api.artifacts.repositories.UrlArtifactRepository;
import org.gradle.api.internal.DocumentationRegistry;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.internal.verifier.HttpRedirectVerifier;
import org.gradle.internal.verifier.HttpRedirectVerifierFactory;
import org.gradle.util.DeprecationLogger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.inject.Inject;
import java.net.URI;
import java.util.function.Supplier;

public class DefaultUrlArtifactRepository implements UrlArtifactRepository {

    private Object url;
    private boolean allowInsecureProtocol;
    private final String repositoryType;
    private final FileResolver fileResolver;
    private final DocumentationRegistry documentationRegistry;
    private final Supplier<String> displayNameSupplier;

    DefaultUrlArtifactRepository(
        final FileResolver fileResolver,
        final DocumentationRegistry documentationRegistry,
        final String repositoryType,
        final Supplier<String> displayNameSupplier
    ) {
        this.fileResolver = fileResolver;
        this.documentationRegistry = documentationRegistry;
        this.repositoryType = repositoryType;
        this.displayNameSupplier = displayNameSupplier;
    }

    @Override
    public URI getUrl() {
        return url == null ? null : fileResolver.resolveUri(url);
    }

    @Override
    public void setUrl(URI url) {
        this.url = url;
    }

    @Override
    public void setUrl(Object url) {
        this.url = url;
    }

    @Override
    public void setAllowInsecureProtocol(boolean allowInsecureProtocol) {
        this.allowInsecureProtocol = allowInsecureProtocol;
    }

    @Override
    public boolean isAllowInsecureProtocol() {
        return allowInsecureProtocol;
    }

    @Nonnull
    public URI validateUrl() {
        URI rootUri = getUrl();
        if (rootUri == null) {
            throw new InvalidUserDataException(String.format(
                "You must specify a URL for a %s repository.",
                repositoryType
            ));
        }
        return rootUri;
    }

    private String allowInsecureProtocolHelpLink() {
        return documentationRegistry.getDslRefForProperty(UrlArtifactRepository.class, "allowInsecureProtocol");
    }

    private void nagUserOfInsecureProtocol() {
        DeprecationLogger
            .nagUserOfDeprecated(
                "Using insecure protocols with repositories",
                String.format(
                    "Switch %s repository '%s' to a secure protocol (like HTTPS) or allow insecure protocols, see %s.",
                    repositoryType,
                    displayNameSupplier.get(),
                    allowInsecureProtocolHelpLink()
                )
            );
    }

    private void nagUserOfInsecureRedirect(@Nullable URI redirectFrom, URI redirectLocation) {
        String contextualAdvice = null;
        if(redirectFrom != null) {
            contextualAdvice = String.format(
                "'%s' is redirecting to '%s'.",
                redirectFrom,
                redirectLocation
            );
        }
        DeprecationLogger
            .nagUserOfDeprecated(
                "Following insecure redirects",
                String.format(
                    "Switch %s repository '%s' to redirect to a secure protocol (like HTTPS) or allow insecure protocols, see %s.",
                    repositoryType,
                    displayNameSupplier.get(),
                    allowInsecureProtocolHelpLink()
                ),
                contextualAdvice
            );
    }

    HttpRedirectVerifier createRedirectVerifier() {
        @Nullable
        URI uri = getUrl();
        return HttpRedirectVerifierFactory
            .create(
                uri,
                allowInsecureProtocol,
                this::nagUserOfInsecureProtocol,
                redirection -> nagUserOfInsecureRedirect(uri, redirection)
            );
    }

    public static class Factory {
        private final FileResolver fileResolver;
        private final DocumentationRegistry documentationRegistry;

        @Inject
        public Factory(FileResolver fileResolver, DocumentationRegistry documentationRegistry) {
            this.fileResolver = fileResolver;
            this.documentationRegistry = documentationRegistry;
        }

        DefaultUrlArtifactRepository create(String repositoryType, Supplier<String> displayNameSupplier) {
            return new DefaultUrlArtifactRepository(fileResolver, documentationRegistry, repositoryType, displayNameSupplier);
        }
    }
}

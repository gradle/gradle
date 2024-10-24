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
import org.gradle.api.file.FileSystemLocation;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.provider.ProviderApiDeprecationLogger;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;
import org.gradle.internal.deprecation.Documentation;
import org.gradle.internal.instrumentation.api.annotations.ReplacedAccessor;
import org.gradle.internal.instrumentation.api.annotations.ReplacesEagerProperty;
import org.gradle.internal.verifier.HttpRedirectVerifier;
import org.gradle.internal.verifier.HttpRedirectVerifierFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.inject.Inject;
import java.io.File;
import java.net.URI;
import java.net.URL;
import java.nio.file.Path;
import java.util.function.Supplier;

public class DefaultUrlArtifactRepository implements UrlArtifactRepository {

    private final Property<URI> url;
    private final Property<Boolean> allowInsecureProtocol;
    private final String repositoryType;
    private final FileResolver fileResolver;
    private final Supplier<String> displayNameSupplier;

    DefaultUrlArtifactRepository(
        final FileResolver fileResolver,
        final ObjectFactory objectFactory,
        final String repositoryType,
        final Supplier<String> displayNameSupplier
    ) {
        this.fileResolver = fileResolver;
        this.repositoryType = repositoryType;
        this.displayNameSupplier = displayNameSupplier;
        this.url = objectFactory.property(URI.class);
        this.allowInsecureProtocol = objectFactory.property(Boolean.class).convention(false);
    }

    @Override
    @ReplacesEagerProperty(replacedAccessors = {
        @ReplacedAccessor(originalType = URI.class, value = ReplacedAccessor.AccessorType.GETTER, name = "getUrl"),
    })
    public Property<URI> getUrl() {
        return url;
    }

    @Override
    @Deprecated
    public void setUrl(CharSequence url) {
        ProviderApiDeprecationLogger.logDeprecation(DefaultUrlArtifactRepository.class, "setUrl(Object)", "getUrl");
        getUrl().set(fileResolver.resolveUri(url));
    }

    @Override
    @Deprecated
    public void setUrl(File url) {
        ProviderApiDeprecationLogger.logDeprecation(DefaultUrlArtifactRepository.class, "setUrl(Object)", "getUrl");
        getUrl().set(fileResolver.resolveUri(url));
    }
    @Override
    @Deprecated
    public void setUrl(FileSystemLocation url) {
        ProviderApiDeprecationLogger.logDeprecation(DefaultUrlArtifactRepository.class, "setUrl(Object)", "getUrl");
        getUrl().set(fileResolver.resolveUri(url));
    }
    @Override
    @Deprecated
    public void setUrl(Path url) {
        ProviderApiDeprecationLogger.logDeprecation(DefaultUrlArtifactRepository.class, "setUrl(Object)", "getUrl");
        getUrl().set(fileResolver.resolveUri(url));
    }
    @Override
    @Deprecated
    public void setUrl(URL url) {
        ProviderApiDeprecationLogger.logDeprecation(DefaultUrlArtifactRepository.class, "setUrl(Object)", "getUrl");
        getUrl().set(fileResolver.resolveUri(url));
    }

    @Override
    @Deprecated
    public void setUrl(URI url) {
        ProviderApiDeprecationLogger.logDeprecation(DefaultUrlArtifactRepository.class, "setUrl(Object)", "getUrl");
        getUrl().set(fileResolver.resolveUri(url));
    }

    @Override
    @ReplacesEagerProperty(originalType = boolean.class)
    public Property<Boolean> getAllowInsecureProtocol() {
        return allowInsecureProtocol;
    }

    @Nonnull
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
                allowInsecureProtocol.get(),
                this::throwExceptionDueToInsecureProtocol,
                redirection -> throwExceptionDueToInsecureRedirect(uri, redirection)
            );
    }

    public static class Factory {
        private final FileResolver fileResolver;
        private final ObjectFactory objectFactory;

        @Inject
        public Factory(FileResolver fileResolver, ObjectFactory objectFactory) {
            this.fileResolver = fileResolver;
            this.objectFactory = objectFactory;
        }

        DefaultUrlArtifactRepository create(String repositoryType, Supplier<String> displayNameSupplier) {
            return new DefaultUrlArtifactRepository(fileResolver, objectFactory, repositoryType, displayNameSupplier);
        }
    }
}

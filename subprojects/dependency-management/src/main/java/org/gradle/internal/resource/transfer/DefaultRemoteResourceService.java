/*
 * Copyright 2021 the original author or authors.
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
package org.gradle.internal.resource.transfer;

import org.apache.commons.io.IOUtils;
import org.gradle.StartParameter;
import org.gradle.api.Action;
import org.gradle.api.GradleException;
import org.gradle.api.InvalidUserCodeException;
import org.gradle.api.internal.artifacts.repositories.transport.RepositoryTransport;
import org.gradle.api.internal.artifacts.repositories.transport.RepositoryTransportFactory;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.resources.MissingResourceException;
import org.gradle.api.resources.RemoteResourceService;
import org.gradle.internal.resource.ExternalResource;
import org.gradle.internal.resource.ExternalResourceName;
import org.gradle.internal.resource.ResourceExceptions;
import org.gradle.internal.resource.cached.ExternalResourceFileStore;
import org.gradle.internal.resource.local.LocallyAvailableExternalResource;
import org.gradle.internal.verifier.HttpRedirectVerifier;
import org.gradle.internal.verifier.HttpRedirectVerifierFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Collections;

public class DefaultRemoteResourceService implements RemoteResourceService {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultRemoteResourceService.class);

    private final RepositoryTransportFactory repositoryTransportFactory;
    private final ExternalResourceFileStore fileStore;
    private final StartParameter startParameter;
    private final ObjectFactory objectFactory;

    public DefaultRemoteResourceService(
        RepositoryTransportFactory repositoryTransportFactory,
        ExternalResourceFileStore fileStore,
        StartParameter startParameter,
        ObjectFactory objectFactory
    ) {
        this.repositoryTransportFactory = repositoryTransportFactory;
        this.fileStore = fileStore;
        this.startParameter = startParameter;
        this.objectFactory = objectFactory;
    }

    @Override
    public void withResource(URI uri, String displayName, Action<? super RemoteResource> consumer) {
        LocallyAvailableExternalResource resource;
        RepositoryTransport transport = createTransport(uri);
        CacheAwareExternalResourceAccessor resourceAccessor = transport.getResourceAccessor();
        try {
            resource = resourceAccessor.getResource(createResourceName(uri, displayName), null, downloadedResource -> fileStore.move(uri.toASCIIString(), downloadedResource), null);
        } catch (IOException e) {
            throw new MissingResourceException("Unable to download external resource " + uri, e);
        }
        if (resource == null) {
            throw new MissingResourceException("Unable to download external resource " + uri);
        }
        consumer.execute(objectFactory.newInstance(DefaultRemoteResource.class, startParameter.isOffline(), uri, resource));
    }

    private ExternalResourceName createResourceName(URI source, String name) {
        return new ExternalResourceName(source) {
            @Override
            public String getShortDisplayName() {
                return name;
            }
        };
    }

    private RepositoryTransport createTransport(URI source) {
        final HttpRedirectVerifier redirectVerifier;
        try {
            redirectVerifier = HttpRedirectVerifierFactory.create(new URI(source.getScheme(), source.getAuthority(), null, null, null), false, () -> {
                throw new InvalidUserCodeException("Attempting to download a resource from an insecure URI " + source + ". This is not supported, use a secure URI instead.");
            }, uri -> {
                throw new InvalidUserCodeException("Attempting to download a resource from an insecure URI " + uri + ". This URI was reached as a redirect from " + source + ". This is not supported, make sure no insecure URIs appear in the redirect");
            });
        } catch (URISyntaxException e) {
            throw new InvalidUserCodeException("Cannot extract host information from specified URI " + source);
        }
        return repositoryTransportFactory.createTransport("https", "jdk toolchains", Collections.emptyList(), redirectVerifier);
    }

    public static class DefaultRemoteResource implements RemoteResource {
        private final boolean offline;
        private final URI uri;
        private final LocallyAvailableExternalResource externalResource;

        @Inject
        public DefaultRemoteResource(boolean offline, URI uri, LocallyAvailableExternalResource externalResource) {
            this.offline = offline;
            this.uri = uri;
            this.externalResource = externalResource;
        }

        @Override
        public boolean isOffline() {
            return offline;
        }

        @Override
        public void persistInto(File destination) {
            if (!isOffline()) {
                downloadResource(uri, destination, externalResource);
            }
        }

        @Override
        public void withContent(Action<? super InputStream> action) {
            if (!isOffline()) {
                externalResource.withContent(action);
            }
        }

        private void downloadResource(URI source, File targetFile, ExternalResource resource) {
            final File downloadFile = new File(targetFile.getAbsoluteFile() + ".part");
            try {
                resource.withContent(inputStream -> {
                    LOGGER.info("Downloading {} to {}", resource.getDisplayName(), targetFile);
                    copyIntoFile(source, inputStream, downloadFile);
                });
                moveFile(targetFile, downloadFile);
            } catch (IOException e) {
                throw new GradleException("Unable to move downloaded resource to target destination", e);
            } finally {
                downloadFile.delete();
            }
        }

        private static void moveFile(File targetFile, File downloadFile) throws IOException {
            try {
                Files.move(downloadFile.toPath(), targetFile.toPath(), StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(downloadFile.toPath(), targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        }

        private static void copyIntoFile(URI source, InputStream inputStream, File destination) {
            try (FileOutputStream outputStream = new FileOutputStream(destination)) {
                IOUtils.copyLarge(inputStream, outputStream);
            } catch (IOException e) {
                throw ResourceExceptions.getFailed(source, e);
            }
        }
    }

}

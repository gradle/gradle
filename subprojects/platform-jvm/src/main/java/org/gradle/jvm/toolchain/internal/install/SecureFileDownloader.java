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

package org.gradle.jvm.toolchain.internal.install;

import org.apache.commons.io.IOUtils;
import org.gradle.api.GradleException;
import org.gradle.api.InvalidUserCodeException;
import org.gradle.api.internal.artifacts.repositories.transport.RepositoryTransport;
import org.gradle.api.internal.artifacts.repositories.transport.RepositoryTransportFactory;
import org.gradle.api.resources.MissingResourceException;
import org.gradle.authentication.Authentication;
import org.gradle.internal.resource.ExternalResource;
import org.gradle.internal.resource.ExternalResourceName;
import org.gradle.internal.resource.ResourceExceptions;
import org.gradle.internal.verifier.HttpRedirectVerifier;
import org.gradle.internal.verifier.HttpRedirectVerifierFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Collection;

public class SecureFileDownloader {

    private static final Logger LOGGER = LoggerFactory.getLogger(SecureFileDownloader.class);

    private final RepositoryTransportFactory repositoryTransportFactory;

    public SecureFileDownloader(RepositoryTransportFactory repositoryTransportFactory) {
        this.repositoryTransportFactory = repositoryTransportFactory;
    }

    public ExternalResource getResourceFor(URI source, Collection<Authentication> authentications) {
        return createExternalResource(source, authentications);
    }

    public void download(URI source, File destination, ExternalResource resource) {
        try {
            downloadResource(source, destination, resource);
        } catch (MissingResourceException e) {
            throw new MissingResourceException(source, String.format("Unable to download '%s' into file '%s'", source, destination), e);
        }
    }

    private ExternalResource createExternalResource(URI source, Collection<Authentication> authentications) {
        final ExternalResourceName resourceName = new ExternalResourceName(source) {
            @Override
            public String getShortDisplayName() {
                return source.toString();
            }
        };
        return getTransport(source, authentications).getRepository().withProgressLogging().resource(resourceName);
    }

    private void downloadResource(URI source, File targetFile, ExternalResource resource) {
        final File downloadFile = new File(targetFile.getAbsoluteFile() + ".part");
        try {
            resource.withContent(inputStream -> {
                LOGGER.info("Downloading {} to {}", resource.getDisplayName(), targetFile);
                copyIntoFile(source, inputStream, downloadFile);
            });
            try {
                moveFile(targetFile, downloadFile);
            } catch (IOException e) {
                throw new GradleException("Unable to move downloaded file to target destination", e);
            }
        } finally {
            downloadFile.delete();
        }
    }

    private void moveFile(File targetFile, File downloadFile) throws IOException {
        try {
            Files.move(downloadFile.toPath(), targetFile.toPath(), StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(downloadFile.toPath(), targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void copyIntoFile(URI source, InputStream inputStream, File destination) {
        try (FileOutputStream outputStream = new FileOutputStream(destination)) {
            IOUtils.copyLarge(inputStream, outputStream);
        } catch (IOException e) {
            throw ResourceExceptions.getFailed(source, e);
        }
    }

    private RepositoryTransport getTransport(URI source, Collection<Authentication> authentications) {
        final HttpRedirectVerifier redirectVerifier;
        try {
            redirectVerifier = HttpRedirectVerifierFactory.create(new URI(source.getScheme(), source.getAuthority(), null, null, null), false,
                    () -> {
                        throw new InvalidUserCodeException("Attempting to download a file from an insecure URI " + source +
                                ". This is not supported, use a secure URI instead.");
                    },
                    uri -> {
                        throw new InvalidUserCodeException("Attempting to download a file from an insecure URI " + uri +
                                ". This URI was reached as a redirect from " + source + ". This is not supported, make sure no insecure URIs appear in the redirect");
                    });
        } catch (URISyntaxException e) {
            throw new InvalidUserCodeException("Cannot extract host information from specified URI " + source);
        }
        return repositoryTransportFactory.createTransport("https", "jdk toolchains", authentications, redirectVerifier);
    }


}

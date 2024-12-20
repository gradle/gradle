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

package org.gradle.launcher.daemon.toolchain;

import org.gradle.authentication.Authentication;
import org.gradle.internal.event.ListenerManager;
import org.gradle.internal.logging.progress.ProgressLoggerFactory;
import org.gradle.internal.nativeintegration.filesystem.FileSystem;
import org.gradle.internal.resource.ExternalResourceFactory;
import org.gradle.internal.resource.ExternalResourceRepository;
import org.gradle.internal.resource.local.FileResourceConnector;
import org.gradle.internal.resource.transport.DefaultExternalResourceRepository;
import org.gradle.internal.resource.transport.http.DefaultHttpSettings;
import org.gradle.internal.resource.transport.http.DefaultSslContextFactory;
import org.gradle.internal.resource.transport.http.HttpClientHelper;
import org.gradle.internal.time.Clock;
import org.gradle.jvm.toolchain.internal.install.JavaToolchainHttpRedirectVerifierFactory;
import org.gradle.tooling.internal.protocol.InternalBuildProgressListener;

import java.net.URI;
import java.util.Collection;
import java.util.Optional;

public class DaemonToolchainExternalResourceFactory implements ExternalResourceFactory {

    private final FileSystem fileSystem;
    private final ListenerManager listenerManager;
    private final JavaToolchainHttpRedirectVerifierFactory httpRedirectVerifierFactory;
    private final ProgressLoggerFactory progressLoggerFactory;
    private final HttpClientHelper.Factory httpClientHelperFactory;
    private final Optional<InternalBuildProgressListener> buildProgressListener;
    private final Clock clock;

    public DaemonToolchainExternalResourceFactory(FileSystem fileSystem, ListenerManager listenerManager, JavaToolchainHttpRedirectVerifierFactory httpRedirectVerifierFactory, HttpClientHelper.Factory httpClientHelperFactory, ProgressLoggerFactory progressLoggerFactory, Clock clock, Optional<InternalBuildProgressListener> buildProgressListener) {
        this.fileSystem = fileSystem;
        this.listenerManager = listenerManager;
        this.httpRedirectVerifierFactory = httpRedirectVerifierFactory;
        this.progressLoggerFactory = progressLoggerFactory;
        this.httpClientHelperFactory = httpClientHelperFactory;
        this.clock = clock;
        this.buildProgressListener = buildProgressListener;
    }

    @Override
    public ExternalResourceRepository createExternalResource(URI source, Collection<Authentication> authentications) {
        if ("file".equals(source.getScheme())) {
            return new FileResourceConnector(fileSystem, listenerManager);
        } else {
            HttpClientHelper httpClientHelper = httpClientHelperFactory.create(DefaultHttpSettings.builder()
                .withAuthenticationSettings(authentications)
                .withSslContextFactory(new DefaultSslContextFactory())
                .withRedirectVerifier(httpRedirectVerifierFactory.createVerifier(source))
                .build()
            );
            ToolchainDownloadProgressListener toolchainDownloadProgressListener = new ToolchainDownloadProgressListener(progressLoggerFactory, buildProgressListener);
            ToolchainExternalResourceAccessor toolchainExternalResourceAccessor = new ToolchainExternalResourceAccessor(httpClientHelper, clock, toolchainDownloadProgressListener);
            return new DefaultExternalResourceRepository("jdk toolchains", toolchainExternalResourceAccessor, null, null);
        }
    }
}

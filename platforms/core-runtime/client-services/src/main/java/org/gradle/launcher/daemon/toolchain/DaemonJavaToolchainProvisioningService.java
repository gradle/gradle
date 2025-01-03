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

import org.gradle.cache.FileLock;
import org.gradle.internal.deprecation.Documentation;
import org.gradle.internal.resource.ExternalResource;
import org.gradle.jvm.toolchain.JavaToolchainSpec;
import org.gradle.jvm.toolchain.internal.JdkCacheDirectory;
import org.gradle.jvm.toolchain.internal.ToolchainDownloadFailedException;
import org.gradle.jvm.toolchain.internal.install.DefaultJdkCacheDirectory;
import org.gradle.jvm.toolchain.internal.install.JavaToolchainProvisioningService;
import org.gradle.jvm.toolchain.internal.install.SecureFileDownloader;
import org.gradle.jvm.toolchain.internal.install.exceptions.MissingToolchainException;
import org.gradle.platform.BuildPlatform;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;

public class DaemonJavaToolchainProvisioningService implements JavaToolchainProvisioningService {

    private static final Object PROVISIONING_PROCESS_LOCK = new Object();

    private final SecureFileDownloader downloader;
    private final DefaultJdkCacheDirectory cacheDirProvider;
    private final BuildPlatform buildPlatform;
    private final ToolchainDownloadUrlProvider toolchainDownloadUrlProvider;
    private final boolean isAutoDownloadEnabled;

    public DaemonJavaToolchainProvisioningService(SecureFileDownloader downloader, JdkCacheDirectory cacheDirProvider, BuildPlatform buildPlatform, ToolchainDownloadUrlProvider toolchainDownloadUrlProvider, Boolean isAutoDownloadEnabled) {
        this.downloader = downloader;
        this.cacheDirProvider = (DefaultJdkCacheDirectory) cacheDirProvider;
        this.buildPlatform = buildPlatform;
        this.toolchainDownloadUrlProvider = toolchainDownloadUrlProvider;
        this.isAutoDownloadEnabled = isAutoDownloadEnabled;
    }

    @Override
    public boolean isAutoDownloadEnabled() {
        return isAutoDownloadEnabled;
    }

    @Override
    public File tryInstall(JavaToolchainSpec spec) throws ToolchainDownloadFailedException {
        if (!isAutoDownloadEnabled()) {
            throw new ToolchainDownloadFailedException("No locally installed toolchains match and toolchain auto-provisioning is not enabled.",
                "Learn more about toolchain auto-detection at " + Documentation.userManual("toolchains", "sec:auto_detection").getUrl() + ".");
        }

        synchronized (PROVISIONING_PROCESS_LOCK) {
            URI uri = getBuildPlatformToolchainUrl();
            try {
                File downloadFolder = cacheDirProvider.getDownloadLocation();
                ExternalResource resource = downloader.getResourceFor(uri);
                File archiveFile = new File(downloadFolder, getFileName(uri, resource));
                final FileLock fileLock = cacheDirProvider.acquireWriteLock(archiveFile, "Downloading toolchain");
                try {
                    if (!archiveFile.exists()) {
                        downloader.download(uri, archiveFile, resource);
                    }
                    return cacheDirProvider.provisionFromArchive(spec, archiveFile, uri);
                } finally {
                    fileLock.close();
                }
            } catch (Exception e) {
                throw new MissingToolchainException(spec, uri, new Throwable(e));
            }
        }
    }

    private URI getBuildPlatformToolchainUrl() {
        String stringUri = toolchainDownloadUrlProvider.getToolchainDownloadUrlByPlatform().get(buildPlatform);
        try {
            return new URI(stringUri);
        } catch (NullPointerException e) {
            throw new ToolchainDownloadFailedException(String.format("No defined toolchain download url for %s %s", buildPlatform.getOperatingSystem(), buildPlatform.getArchitecture()),
                "Learn more about toolchain auto-detection at " + Documentation.userManual("toolchains", "sec:auto_detection").getUrl() + ".",
                "Learn more about toolchain repositories at " + Documentation.userManual("toolchains", "sub:download_repositories").getUrl() + ".");
        } catch (URISyntaxException e) {
            throw new ToolchainDownloadFailedException(String.format("Invalid toolchain download url %s for %s %s", stringUri, buildPlatform.getOperatingSystem(), buildPlatform.getArchitecture()),
                "Learn more about toolchain auto-detection at " + Documentation.userManual("toolchains", "sec:auto_detection").getUrl() + ".",
                "Learn more about toolchain repositories at " + Documentation.userManual("toolchains", "sub:download_repositories").getUrl() + ".");
        }
    }
}

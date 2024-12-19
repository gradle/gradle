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

package org.gradle.launcher.daemon.toolchain

import org.gradle.cache.FileLock
import org.gradle.internal.resource.ExternalResource
import org.gradle.internal.resource.metadata.ExternalResourceMetaData
import org.gradle.jvm.toolchain.JavaToolchainSpec
import org.gradle.jvm.toolchain.internal.ToolchainDownloadFailedException
import org.gradle.jvm.toolchain.internal.install.DefaultJdkCacheDirectory
import org.gradle.jvm.toolchain.internal.install.SecureFileDownloader
import org.gradle.platform.BuildPlatform
import spock.lang.Specification
import spock.lang.TempDir

class DaemonJavaToolchainProvisioningServiceTest extends Specification {

    private static final String ARCHIVE_NAME = 'ibm-11-x64-hotspot-linux.zip'
    private static final URI DOWNLOAD_URL = URI.create('https://server.com')

    @TempDir
    public File temporaryFolder

    def downloader = Mock(SecureFileDownloader)
    def cache = Mock(DefaultJdkCacheDirectory)
    def archiveFileLock = Mock(FileLock)
    def buildPlatform = Mock(BuildPlatform)
    def spec = Mock(JavaToolchainSpec)

    def setup() {
        ExternalResourceMetaData downloadResourceMetadata = Mock(ExternalResourceMetaData)
        downloadResourceMetadata.getFilename() >> ARCHIVE_NAME

        ExternalResource downloadResource = Mock(ExternalResource)
        downloadResource.getMetaData() >> downloadResourceMetadata

        downloader.getResourceFor(_ as URI) >> downloadResource

        cache.acquireWriteLock(_ as File, _ as String) >> archiveFileLock
        cache.getDownloadLocation() >> temporaryFolder
        cache.provisionFromArchive(_ as JavaToolchainSpec, _ as File, _ as URI) >> new File(temporaryFolder, "install_dir")
    }

    def "cache is properly locked around provisioning a jdk"() {
        given:
        def toolchainDownloadUrlProvider = new ToolchainDownloadUrlProvider([(buildPlatform) : DOWNLOAD_URL.toString()])
        def provisioningService = new DaemonJavaToolchainProvisioningService(downloader, cache, buildPlatform, toolchainDownloadUrlProvider, true)

        when:
        provisioningService.tryInstall(spec)

        then:
        1 * cache.acquireWriteLock(_, _) >> archiveFileLock
        1 * downloader.download(_, _, _)
        1 * archiveFileLock.close()
    }

    def "skips downloading if already downloaded"() {
        given:
        def toolchainDownloadUrlProvider = new ToolchainDownloadUrlProvider([(buildPlatform) : DOWNLOAD_URL.toString()])
        def provisioningService = new DaemonJavaToolchainProvisioningService(downloader, cache, buildPlatform, toolchainDownloadUrlProvider, true)
        new File(temporaryFolder, ARCHIVE_NAME).createNewFile()

        when:
        provisioningService.tryInstall(spec)

        then:
        0 * downloader.download(_, _, _)
    }

    def "auto download can be disabled"() {
        given:
        def toolchainDownloadUrlProvider = new ToolchainDownloadUrlProvider([(buildPlatform) : DOWNLOAD_URL.toString()])
        def provisioningService = new DaemonJavaToolchainProvisioningService(downloader, cache, buildPlatform, toolchainDownloadUrlProvider, false)

        when:
        provisioningService.tryInstall(spec)

        then:
        thrown(ToolchainDownloadFailedException.class)
    }

    def "fails downloading from not provided platform toolchain url"() {
        given:
        def toolchainDownloadUrlProvider = new ToolchainDownloadUrlProvider([:])
        def provisioningService = new DaemonJavaToolchainProvisioningService(downloader, cache, buildPlatform, toolchainDownloadUrlProvider, true)

        when:
        provisioningService.tryInstall(spec)

        then:
        thrown(ToolchainDownloadFailedException.class)
    }

    def "fails downloading from invalid provided platform toolchain url"() {
        given:
        def toolchainDownloadUrlProvider = new ToolchainDownloadUrlProvider([(buildPlatform) : "invalid url"])
        def provisioningService = new DaemonJavaToolchainProvisioningService(downloader, cache, buildPlatform, toolchainDownloadUrlProvider, true)

        when:
        provisioningService.tryInstall(spec)

        then:
        thrown(ToolchainDownloadFailedException.class)
    }

    def "downloads from url"() {
        given:
        def toolchainDownloadUrlProvider = new ToolchainDownloadUrlProvider([(buildPlatform) : DOWNLOAD_URL.toString()])
        def provisioningService = new DaemonJavaToolchainProvisioningService(downloader, cache, buildPlatform, toolchainDownloadUrlProvider, true)

        when:
        provisioningService.tryInstall(spec)

        then:
        1 * downloader.download(DOWNLOAD_URL, new File(temporaryFolder, ARCHIVE_NAME), _)
    }
}

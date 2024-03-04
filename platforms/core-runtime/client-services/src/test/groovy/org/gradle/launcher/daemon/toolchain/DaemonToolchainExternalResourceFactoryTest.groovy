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

import org.gradle.authentication.Authentication
import org.gradle.internal.event.ListenerManager
import org.gradle.internal.logging.progress.ProgressLoggerFactory
import org.gradle.internal.nativeintegration.filesystem.FileSystem
import org.gradle.internal.resource.ExternalResourceFactory
import org.gradle.internal.resource.ExternalResourceRepository
import org.gradle.internal.resource.local.FileResourceConnector
import org.gradle.internal.resource.local.FileResourceListener
import org.gradle.internal.resource.transport.DefaultExternalResourceRepository
import org.gradle.internal.resource.transport.http.HttpClientHelper
import org.gradle.internal.time.Clock
import org.gradle.internal.verifier.HttpRedirectVerifier
import org.gradle.jvm.toolchain.internal.install.JavaToolchainHttpRedirectVerifierFactory
import spock.lang.Specification

class DaemonToolchainExternalResourceFactoryTest extends Specification {

    ExternalResourceFactory repositoryTransportFactory
    def fileSystem = Mock(FileSystem)
    def fileListener = Mock(FileResourceListener)
    def listenerManager = Mock(ListenerManager)
    def verifierFactory = Mock(JavaToolchainHttpRedirectVerifierFactory)
    def httpClientFactory = Mock(HttpClientHelper.Factory)
    def progressLoggerFactory = Mock(ProgressLoggerFactory)
    def clock = Mock(Clock)

    def setup() {
        verifierFactory.createVerifier(_ as URI) >> Mock(HttpRedirectVerifier)
        listenerManager.getBroadcaster(_ as Class<FileResourceListener>) >> fileListener
        repositoryTransportFactory = new DaemonToolchainExternalResourceFactory(fileSystem, listenerManager, verifierFactory, httpClientFactory, progressLoggerFactory, clock, Optional.empty())
    }

    def "create external resource from https uri"() {
        when:
        def externalResource = createExternalResource(URI.create("https://server.com"), [])

        then:
        externalResource.class == DefaultExternalResourceRepository
        externalResource.toString() == "jdk toolchains"
    }

    def "create external resource from local file uri"() {
        when:
        def externalResource = createExternalResource(URI.create("file:/local"), [])

        then:
        externalResource.class == FileResourceConnector
        externalResource.@fileSystem == fileSystem
        externalResource.@listener == fileListener
    }

    private ExternalResourceRepository createExternalResource(URI source, Collection<Authentication> authentications) {
        return repositoryTransportFactory.createExternalResource(source, authentications)
    }
}

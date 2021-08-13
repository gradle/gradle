/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.jvm.toolchain.install.internal

import org.gradle.StartParameter
import org.gradle.api.Action
import org.gradle.api.BuildCancelledException
import org.gradle.api.internal.artifacts.repositories.transport.RepositoryTransport
import org.gradle.api.internal.artifacts.repositories.transport.RepositoryTransportFactory
import org.gradle.api.resources.RemoteResourceService
import org.gradle.authentication.Authentication
import org.gradle.internal.resource.cached.ExternalResourceFileStore
import org.gradle.internal.resource.local.LocallyAvailableExternalResource
import org.gradle.internal.resource.local.LocallyAvailableResource
import org.gradle.internal.resource.transfer.CacheAwareExternalResourceAccessor
import org.gradle.internal.resource.transfer.DefaultRemoteResourceService
import org.gradle.internal.verifier.HttpRedirectVerifier
import org.gradle.util.TestUtil
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Files

class AdoptOpenJdkDownloaderTest extends Specification {

    @TempDir
    public File temporaryFolder

    def "cancelled download does not leave destination file behind"() {
        RepositoryTransportFactory transportFactory = newFailingTransportFactory()
        RemoteResourceService remoteResourceService = remoteResourceServiceFor(transportFactory)

        given:
        def downloader = new JdkDownloader(remoteResourceService)
        def destinationFile = new File(Files.createTempDirectory(temporaryFolder.toPath(), null).toFile(), "target")

        when:
        downloader.download(URI.create("https://foo"), destinationFile)

        then:
        thrown(BuildCancelledException)
        !destinationFile.exists()
    }

    private RepositoryTransportFactory newFailingTransportFactory() {
        Mock(RepositoryTransportFactory) {
            createTransport(_ as String, _ as String, _ as Collection<Authentication>, _ as HttpRedirectVerifier) >> Mock(RepositoryTransport) {
                getResourceAccessor() >> Stub(CacheAwareExternalResourceAccessor) {
                    getResource(_, _, _, _) >> { args ->
                        Stub(LocallyAvailableExternalResource) {
                            withContent(_ as Action<? super InputStream>) >> { Action<? super InputStream> readAction ->
                                readAction.execute(new ByteArrayInputStream("foo".bytes))
                                throw new BuildCancelledException()
                            }
                        }
                    }
                }
            }
        }
    }

    private RemoteResourceService remoteResourceServiceFor(RepositoryTransportFactory repositoryTransportFactory) {
        new DefaultRemoteResourceService(
            repositoryTransportFactory,
            Stub(ExternalResourceFileStore) {
                move(_, _) >> { args ->
                    def (key, file) = args
                    Stub(LocallyAvailableResource) {
                        getFile() >> file
                    }
                }
            },
            Stub(StartParameter) {
                isOffline() >> false
            },
            TestUtil.objectFactory()
        )
    }
}

/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.jvm.toolchain.install

import org.gradle.api.Action
import org.gradle.api.BuildCancelledException
import org.gradle.api.internal.artifacts.repositories.transport.RepositoryTransport
import org.gradle.api.internal.artifacts.repositories.transport.RepositoryTransportFactory
import org.gradle.authentication.Authentication
import org.gradle.internal.resource.ExternalResource
import org.gradle.internal.resource.ExternalResourceName
import org.gradle.internal.resource.ExternalResourceRepository
import org.gradle.internal.verifier.HttpRedirectVerifier
import org.gradle.jvm.toolchain.internal.install.SecureFileDownloader
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Files

class SecureFileDownloaderTest extends Specification {

    @TempDir
    public File temporaryFolder

    def "successful download creates destination file with the right content"() {
        RepositoryTransportFactory transportFactory = newTransportFactory()

        given:
        def downloader = new SecureFileDownloader(transportFactory)
        def destinationFile = new File(Files.createTempDirectory(temporaryFolder.toPath(), null).toFile(), "target")

        when:
        URI uri = URI.create("https://foo")
        downloader.download(uri, destinationFile, downloader.getResourceFor(uri, Collections.emptyList()))

        then:
        noExceptionThrown()
        destinationFile.exists()
        destinationFile.text == "foo"
    }

    def "cancelled download does not leave destination file behind"() {
        RepositoryTransportFactory transportFactory = newTransportFactory({ throw new BuildCancelledException() })

        given:
        def downloader = new SecureFileDownloader(transportFactory)
        def destinationFile = new File(Files.createTempDirectory(temporaryFolder.toPath(), null).toFile(), "target")

        when:
        URI uri = URI.create("https://foo")
        downloader.download(uri, destinationFile, downloader.getResourceFor(uri, Collections.emptyList()))

        then:
        thrown(BuildCancelledException)
        !destinationFile.exists()
    }

    private RepositoryTransportFactory newTransportFactory(Closure doAfterRead = {}) {
        Mock(RepositoryTransportFactory) {
            createTransport(_ as String, _ as String, _ as Collection<Authentication>, _ as HttpRedirectVerifier) >> Mock(RepositoryTransport) {
                getRepository() >> Mock(ExternalResourceRepository) {
                    withProgressLogging() >> Mock(ExternalResourceRepository) {
                        resource(_ as ExternalResourceName) >> Mock(ExternalResource) {
                            withContent(_ as Action<? super InputStream>) >> { Action<? super InputStream> readAction ->
                                readAction.execute(new ByteArrayInputStream("foo".bytes))
                                doAfterRead()
                            }
                        }
                    }
                }
            }
        }
    }
}

/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.integtests.resolve.resource.sftp

import org.gradle.api.artifacts.repositories.UrlArtifactRepository
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.FileSystemTestPreconditions

import static org.hamcrest.CoreMatchers.equalTo

class SftpHostKeyVerificationIntegrationTest extends AbstractSftpDependencyResolutionTest {

    def "fails to resolve from a server whose host is not listed in known hosts"() {
        given:
        def module = mavenSftpRepo.module('org.group.name', 'projectA', '1.2').publish()
        mavenRepository('')
        server.unlistHostKey()

        when:
        fails 'retrieve'

        then:
        failure.assertHasCause('Could not resolve org.group.name:projectA:1.2')
        failure.assertThatCause(equalTo(untrustedHostKeyMessage("the host is not listed in the SSH known hosts files")))
    }

    @Requires(FileSystemTestPreconditions.FilePermissions)
    def "skips a known hosts file it cannot read"() {
        given:
        mavenSftpRepo.module('org.group.name', 'projectA', '1.2').publish()
        mavenRepository('')
        server.knownHostsFile.makeUnreadable()

        when:
        fails 'retrieve'

        then:
        failure.assertThatCause(equalTo(untrustedHostKeyMessage("the host is not listed in the SSH known hosts files")))
    }

    def "fails to resolve from a server whose host key does not match the one in known hosts"() {
        given:
        def module = mavenSftpRepo.module('org.group.name', 'projectA', '1.2').publish()
        mavenRepository('')
        server.listDifferentHostKey()

        when:
        fails 'retrieve'

        then:
        failure.assertHasCause('Could not resolve org.group.name:projectA:1.2')
        failure.assertThatCause(equalTo(untrustedHostKeyMessage("its key does not match the one listed for this host in the SSH known hosts files. The server's key may have changed, or another server may be impersonating it")))
    }

    def "resolves from a server addressed by an IPv6 literal whose host key is listed in known hosts"() {
        given:
        server.useIpv6Loopback()
        def module = mavenSftpRepo.module('org.group.name', 'projectA', '1.2').publish()
        mavenRepository('')

        when:
        module.pom.expectMetadataRetrieve()
        module.pom.expectFileDownload()
        module.artifact.expectMetadataRetrieve()
        module.artifact.expectFileDownload()

        then:
        succeeds 'retrieve'
        file('libs').assertHasDescendants 'projectA-1.2.jar'
    }

    def "resolves from a Maven repository whose host is not listed in known hosts when insecure protocols are allowed"() {
        given:
        def module = mavenSftpRepo.module('org.group.name', 'projectA', '1.2').publish()
        mavenRepository('allowInsecureProtocol = true')
        server.unlistHostKey()

        when:
        module.pom.expectMetadataRetrieve()
        module.pom.expectFileDownload()
        module.artifact.expectMetadataRetrieve()
        module.artifact.expectFileDownload()

        then:
        succeeds 'retrieve'
        file('libs').assertHasDescendants 'projectA-1.2.jar'
    }

    def "resolves from an Ivy repository whose host is not listed in known hosts when insecure protocols are allowed"() {
        given:
        def module = ivySftpRepo.module('org.group.name', 'projectA', '1.2').publish()
        buildFile << """
            repositories {
                ivy {
                    url = "${ivySftpRepo.uri}"
                    allowInsecureProtocol = true
                    credentials {
                        username = 'sftp'
                        password = 'sftp'
                    }
                }
            }
            ${retrieveTask()}
        """
        server.unlistHostKey()

        when:
        module.ivy.expectDownload()
        module.jar.expectDownload()

        then:
        succeeds 'retrieve'
        file('libs').assertHasDescendants 'projectA-1.2.jar'
    }

    private String untrustedHostKeyMessage(String reason) {
        "Could not verify the identity of SFTP server at ${server.uri}: ${reason}. " +
            "The server presented the ssh-rsa key with fingerprint ${server.hostKeyFingerprint}. " +
            "Check this fingerprint with the server's administrator, then add the key to ${server.knownHostsFile}, for example with 'ssh-keyscan -p ${server.port} ${server.hostAddress} >> ~/.ssh/known_hosts'. " +
            "Alternatively, allow insecure protocols for the repository. " +
            "For more information, please refer to ${documentationRegistry.getDslRefForProperty(UrlArtifactRepository, 'allowInsecureProtocol')} in the Gradle documentation."
    }

    private void mavenRepository(String extraConfiguration) {
        buildFile << """
            repositories {
                maven {
                    url = "${mavenSftpRepo.uri}"
                    ${extraConfiguration}
                    credentials {
                        username = 'sftp'
                        password = 'sftp'
                    }
                }
            }
            ${retrieveTask()}
        """
    }

    private static String retrieveTask() {
        """
            configurations { compile }
            dependencies { compile 'org.group.name:projectA:1.2' }
            task retrieve(type: Sync) {
                from configurations.compile
                into 'libs'
            }
        """
    }
}

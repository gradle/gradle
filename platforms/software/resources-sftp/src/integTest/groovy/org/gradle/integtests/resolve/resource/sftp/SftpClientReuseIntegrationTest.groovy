/*
 * Copyright 2017 the original author or authors.
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

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.test.fixtures.server.http.BlockingHttpServer
import org.gradle.test.fixtures.server.sftp.SFTPServer
import org.junit.Rule

class SftpClientReuseIntegrationTest extends AbstractIntegrationSpec {
    @Rule final SFTPServer sftpServer = new SFTPServer(temporaryFolder)
    @Rule final BlockingHttpServer coordinator = new BlockingHttpServer()

    def "does not attempt to reuse a client that has been disconnected"() {
        coordinator.start()

        buildFile << """
            ${sftpTask}

            task firstUse(type: SftpTask) {
                credentials = creds
            }

            task block {
                doLast {
                    ${coordinator.callFromBuild('sync')}
                }
                dependsOn firstUse
            }

            task reuseClient(type: SftpTask) {
                credentials = creds
                dependsOn block
            }
        """
        sftpServer.expectLstat("/")
        def sync = coordinator.expectAndBlock('sync')

        when:
        def gradle = executer.withTasks('reuseClient').withArgument("--info").start()
        sync.waitForAllPendingCalls()

        then:
        sftpServer.clearSessions()
        sftpServer.expectLstat("/")
        sleep(1000)

        when:
        sync.releaseAll()

        then:
        gradle.waitForFinish()
    }

    String getSftpTask() {
        return """
            import org.gradle.internal.resource.transport.sftp.SftpClientFactory
            import org.gradle.api.artifacts.repositories.PasswordCredentials
            import org.gradle.internal.credentials.DefaultPasswordCredentials

            PasswordCredentials creds = new DefaultPasswordCredentials('sftp', 'sftp')

            class SftpTask extends DefaultTask {
                @Internal
                PasswordCredentials credentials

                @Inject
                SftpClientFactory getSftpClientFactory() {
                    throw new UnsupportedOperationException()
                }

                @TaskAction
                void sftpTest() {
                    def client = sftpClientFactory.createSftpClient(new URI("${sftpServer.uri}"), credentials)
                    client.sftpClient.lstat("/")
                    sftpClientFactory.releaseSftpClient(client)
                }
            }
        """
    }
}

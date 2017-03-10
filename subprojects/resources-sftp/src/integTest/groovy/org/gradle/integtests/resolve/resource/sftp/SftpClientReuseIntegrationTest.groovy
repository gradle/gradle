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
import org.gradle.test.fixtures.server.http.CyclicBarrierHttpServer
import org.gradle.test.fixtures.server.sftp.SFTPServer
import org.junit.Rule

class SftpClientReuseIntegrationTest extends AbstractIntegrationSpec {
    @Rule final SFTPServer sftpServer = new SFTPServer(temporaryFolder)
    @Rule final CyclicBarrierHttpServer coordinator = new CyclicBarrierHttpServer()

    def "does not attempt to reuse a client that has been disconnected"() {
        buildFile << """
            ${sftpTask}
            
            task firstUse(type: SftpTask) {
                credentials = creds
            }
            
            task block {
                doLast {
                    new URL("${coordinator.uri}").text
                }
                dependsOn firstUse
            }
            
            task reuseClient(type: SftpTask) {
                credentials = creds
                dependsOn block
            }
        """
        sftpServer.expectLstat("/")

        when:
        def gradle = executer.withTasks('reuseClient').withArgument("--info").start()
        def coordinatorWaitForResult = coordinator.waitFor(false)

        then:
        sftpServer.clearSessions()
        sftpServer.expectLstat("/")
        sleep(1000)

        when:
        coordinator.release()

        then:
        gradle.waitForFinish()
        coordinatorWaitForResult
    }

    String getSftpTask() {
        return """
            import javax.inject.Inject
            import org.gradle.internal.resource.transport.sftp.SftpClientFactory
            import org.gradle.api.artifacts.repositories.PasswordCredentials
            import org.gradle.api.internal.artifacts.repositories.DefaultPasswordCredentials
            
            PasswordCredentials creds = new DefaultPasswordCredentials('sftp', 'sftp')
            
            class SftpTask extends DefaultTask {
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

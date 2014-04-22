/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.integtests.resolve.ivy

import org.apache.sshd.client.SftpClient
import org.gradle.api.artifacts.repositories.PasswordCredentials
import org.gradle.api.internal.artifacts.repositories.DefaultPasswordCredentials
import org.gradle.api.internal.externalresource.transport.sftp.SftpClientFactory
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.test.fixtures.ivy.IvySftpRepository
import org.gradle.test.fixtures.server.sftp.SFTPServer
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition
import org.junit.Rule

@Requires(TestPrecondition.JDK6_OR_LATER)
class IvySftpClientFactoryIntegrationTest extends AbstractIntegrationSpec {
    @Rule
    final SFTPServer server = new SFTPServer(this)

    SftpClientFactory sftpClientFactory = new SftpClientFactory()

    IvySftpRepository getIvySftpRepo(String contextPath) {
        new IvySftpRepository(server, contextPath, false, null)
    }

    def "Can acquire and release single client"() {
        given:
        def ivySftpRepo = getIvySftpRepo('/repo')
        URI uri = ivySftpRepo.uri
        PasswordCredentials credentials = new DefaultPasswordCredentials('sftp', 'sftp')

        when:
        server.expectInit()
        SftpClient client = sftpClientFactory.createSftpClient(uri, credentials)

        then:
        sftpClientFactory.clients.size() == 1
        List<SftpClientFactory.SftpHost> clientsByHost = getClientsForSftpHost(uri, credentials)
        clientsByHost.size() == 1
        clientsByHost.get(0) == client
        client.locked

        when:
        sftpClientFactory.releaseSftpClient(client)

        then:
        sftpClientFactory.clients.size() == 1
        clientsByHost.size() == 1
        clientsByHost.get(0) == client
        !client.locked

        cleanup:
        sftpClientFactory.stop()
    }

    def "Creates new client if existing client is locked"() {
        given:
        def ivySftpRepo = getIvySftpRepo('/repo')
        URI uri = ivySftpRepo.uri
        PasswordCredentials credentials = new DefaultPasswordCredentials('sftp', 'sftp')

        when:
        server.expectInit()
        SftpClient initialClient = sftpClientFactory.createSftpClient(uri, credentials)

        then:
        sftpClientFactory.clients.size() == 1
        List<SftpClientFactory.SftpHost> clientsByHost = getClientsForSftpHost(uri, credentials)
        clientsByHost.size() == 1
        initialClient.locked

        when:
        server.expectInit()
        SftpClient newClient = sftpClientFactory.createSftpClient(uri, credentials)

        then:
        sftpClientFactory.clients.keySet().size() == 1
        clientsByHost.size() == 2
        newClient.locked
        initialClient != newClient

        cleanup:
        sftpClientFactory.stop()
    }

    def "Can acquire, release and reuse single client"() {
        given:
        def ivySftpRepo = getIvySftpRepo('/repo')
        URI uri = ivySftpRepo.uri
        PasswordCredentials credentials = new DefaultPasswordCredentials('sftp', 'sftp')

        when:
        server.expectInit()
        SftpClient initialClient = sftpClientFactory.createSftpClient(uri, credentials)
        sftpClientFactory.releaseSftpClient(initialClient)

        then:
        sftpClientFactory.clients.size() == 1
        List<SftpClientFactory.SftpHost> clientsByHost = getClientsForSftpHost(uri, credentials)
        clientsByHost.size() == 1
        clientsByHost.get(0) == initialClient
        !initialClient.locked

        when:
        SftpClient reusedClient = sftpClientFactory.createSftpClient(uri, credentials)
        sftpClientFactory.releaseSftpClient(reusedClient)

        then:
        sftpClientFactory.clients.size() == 1
        clientsByHost.size() == 1
        clientsByHost.get(0) == reusedClient
        initialClient == reusedClient
        !reusedClient.locked

        cleanup:
        sftpClientFactory.stop()
    }

    def "Can acquire and release multiple clients"() {
        given:
        def ivySftpRepo1 = getIvySftpRepo('/repo1')
        def ivySftpRepo2 = getIvySftpRepo('/repo2')
        URI uri1 = ivySftpRepo1.uri
        URI uri2 = ivySftpRepo2.uri
        PasswordCredentials credentials1 = new DefaultPasswordCredentials('sftp1', 'sftp1')
        PasswordCredentials credentials2 = new DefaultPasswordCredentials('sftp2', 'sftp2')

        when:
        server.expectInit()
        SftpClient client1 = sftpClientFactory.createSftpClient(uri1, credentials1)
        server.expectInit()
        SftpClient client2 = sftpClientFactory.createSftpClient(uri2, credentials2)

        then:
        sftpClientFactory.clients.size() == 2
        List<SftpClientFactory.SftpHost> clientsByHost1 = getClientsForSftpHost(uri1, credentials1)
        clientsByHost1.size() == 1
        clientsByHost1.get(0) == client1
        List<SftpClientFactory.SftpHost> clientsByHost2 = getClientsForSftpHost(uri2, credentials2)
        clientsByHost2.size() == 1
        clientsByHost2.get(0) == client2
        client1.locked
        client2.locked

        when:
        sftpClientFactory.releaseSftpClient(client1)
        sftpClientFactory.releaseSftpClient(client2)

        then:
        sftpClientFactory.clients.size() == 2
        clientsByHost1.size() == 1
        clientsByHost1.get(0) == client1
        clientsByHost2.size() == 1
        clientsByHost2.get(0) == client2
        !client1.locked
        !client2.locked

        cleanup:
        sftpClientFactory.stop()
    }

    private List<SftpClientFactory.SftpHost> getClientsForSftpHost(URI uri, PasswordCredentials credentials) {
        sftpClientFactory.clients.get(new SftpClientFactory.SftpHost(uri, credentials))
    }
}

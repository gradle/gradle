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

package org.gradle.internal.resource.transport.sftp

import org.gradle.api.credentials.PasswordCredentials
import org.gradle.test.fixtures.concurrent.ConcurrentSpec
import org.gradle.util.TestCredentialUtil

import static org.gradle.internal.resource.transport.sftp.SftpClientFactory.SftpClientCreator

class SftpClientFactoryTest extends ConcurrentSpec {

    SftpClientFactory sftpClientFactory = new SftpClientFactory()
    SftpClientCreator sftpClientCreator = Mock(SftpClientCreator)

    def setup() {
        sftpClientFactory.sftpClientCreator = sftpClientCreator
    }

    def "Can acquire and release single client"() {
        def mockSftpClient = Mock(LockableSftpClient)

        given:
        URI uri = new URI('http://localhost:22/repo')
        PasswordCredentials credentials = TestCredentialUtil.defaultPasswordCredentials('sftp', 'sftp')

        when:
        LockableSftpClient actualClient = sftpClientFactory.createSftpClient(uri, credentials)

        then:
        sftpClientCreator.createNewClient(new SftpHost(uri, credentials)) >> mockSftpClient
        sftpClientFactory.idleClients.size() == 0

        when:
        sftpClientFactory.releaseSftpClient(actualClient)

        then:
        1 * mockSftpClient.host >> new SftpHost(uri, credentials)
        sftpClientFactory.idleClients.size() == 1
        sftpClientFactory.allClients.size() == 1
        List<SftpHost> clientsByHost = getClientsForSftpHost(uri, credentials)
        clientsByHost.size() == 1
        clientsByHost.get(0) == actualClient
    }

    def "Creates new client if existing client is in use"() {
        def mockInitialSftpClient = Mock(LockableSftpClient)
        def mockNewSftpClient = Mock(LockableSftpClient)

        given:
        URI uri = new URI('http://localhost:22/repo')
        PasswordCredentials credentials = org.gradle.util.TestCredentialUtil.defaultPasswordCredentials('sftp', 'sftp')

        when:
        LockableSftpClient initialClient = sftpClientFactory.createSftpClient(uri, credentials)

        then:
        sftpClientCreator.createNewClient(new SftpHost(uri, credentials)) >> mockInitialSftpClient
        sftpClientFactory.idleClients.size() == 0

        when:
        LockableSftpClient newClient = sftpClientFactory.createSftpClient(uri, credentials)

        then:
        sftpClientCreator.createNewClient(new SftpHost(uri, credentials)) >> mockNewSftpClient
        sftpClientFactory.idleClients.size() == 0
        sftpClientFactory.allClients.size() == 2
        initialClient != newClient
    }

    def "Can acquire, release and reuse single client"() {
        def mockSftpClient = Mock(LockableSftpClient)

        given:
        URI uri = new URI('http://localhost:22/repo')
        PasswordCredentials credentials = org.gradle.util.TestCredentialUtil.defaultPasswordCredentials('sftp', 'sftp')

        when:
        LockableSftpClient initialClient = sftpClientFactory.createSftpClient(uri, credentials)
        sftpClientFactory.releaseSftpClient(initialClient)

        then:
        1 * mockSftpClient.host >> new SftpHost(uri, credentials)
        sftpClientCreator.createNewClient(new SftpHost(uri, credentials)) >> mockSftpClient
        sftpClientFactory.idleClients.size() == 1
        List<SftpHost> clientsByHost = getClientsForSftpHost(uri, credentials)
        clientsByHost.size() == 1
        clientsByHost.get(0) == initialClient

        when:
        LockableSftpClient reusedClient = sftpClientFactory.createSftpClient(uri, credentials)
        sftpClientFactory.releaseSftpClient(reusedClient)

        then:
        1 * mockSftpClient.host >> new SftpHost(uri, credentials)
        1 * mockSftpClient.connected >> true
        sftpClientFactory.idleClients.size() == 1
        sftpClientFactory.allClients.size() == 1
        clientsByHost.size() == 1
        clientsByHost.get(0) == reusedClient
        initialClient == reusedClient
    }

    def "Can acquire and release multiple clients"() {
        def mockSftpClient1 = Mock(LockableSftpClient)
        def mockSftpClient2 = Mock(LockableSftpClient)

        given:
        URI uri1 = new URI('http://localhost:22/repo1')
        URI uri2 = new URI('http://localhost:22/repo2')
        PasswordCredentials credentials1 = org.gradle.util.TestCredentialUtil.defaultPasswordCredentials('sftp1', 'sftp1')
        PasswordCredentials credentials2 = org.gradle.util.TestCredentialUtil.defaultPasswordCredentials('sftp2', 'sftp2')

        when:
        LockableSftpClient client1 = sftpClientFactory.createSftpClient(uri1, credentials1)
        LockableSftpClient client2 = sftpClientFactory.createSftpClient(uri2, credentials2)

        then:
        sftpClientCreator.createNewClient(new SftpHost(uri1, credentials1)) >> mockSftpClient1
        sftpClientCreator.createNewClient(new SftpHost(uri2, credentials2)) >> mockSftpClient2
        sftpClientFactory.idleClients.size() == 0

        when:
        sftpClientFactory.releaseSftpClient(client1)
        sftpClientFactory.releaseSftpClient(client2)

        then:
        1 * mockSftpClient1.host >> new SftpHost(uri1, credentials1)
        1 * mockSftpClient2.host >> new SftpHost(uri2, credentials2)
        sftpClientFactory.idleClients.size() == 2
        sftpClientFactory.allClients.size() == 2
        List<SftpHost> clientsByHost1 = getClientsForSftpHost(uri1, credentials1)
        clientsByHost1.size() == 1
        clientsByHost1.get(0) == client1
        List<SftpHost> clientsByHost2 = getClientsForSftpHost(uri2, credentials2)
        clientsByHost2.size() == 1
        clientsByHost2.get(0) == client2
    }

    def "Can stop a single, released client"() {
        def mockSftpClient = Mock(LockableSftpClient)

        given:
        URI uri = new URI('http://localhost:22/repo')
        PasswordCredentials credentials = org.gradle.util.TestCredentialUtil.defaultPasswordCredentials('sftp', 'sftp')

        when:
        LockableSftpClient actualClient = sftpClientFactory.createSftpClient(uri, credentials)

        and:
        sftpClientFactory.releaseSftpClient(actualClient)
        sftpClientFactory.stop()

        then:
        sftpClientCreator.createNewClient(new SftpHost(uri, credentials)) >> mockSftpClient
        1 * mockSftpClient.host >> new SftpHost(uri, credentials)
        sftpClientFactory.idleClients.size() == 0
        sftpClientFactory.allClients.size() == 0
        1 * mockSftpClient.stop()
    }

    def "Can stop multiple, released clients"() {
        def mockSftpClient1 = Mock(LockableSftpClient)
        def mockSftpClient2 = Mock(LockableSftpClient)

        given:
        URI uri1 = new URI('http://localhost:22/repo1')
        URI uri2 = new URI('http://localhost:22/repo2')
        PasswordCredentials credentials1 = org.gradle.util.TestCredentialUtil.defaultPasswordCredentials('sftp1', 'sftp1')
        PasswordCredentials credentials2 = org.gradle.util.TestCredentialUtil.defaultPasswordCredentials('sftp2', 'sftp2')

        when:
        LockableSftpClient client1 = sftpClientFactory.createSftpClient(uri1, credentials1)
        LockableSftpClient client2 = sftpClientFactory.createSftpClient(uri2, credentials2)

        and:
        sftpClientFactory.releaseSftpClient(client1)
        sftpClientFactory.releaseSftpClient(client2)
        sftpClientFactory.stop()

        then:
        sftpClientCreator.createNewClient(new SftpHost(uri1, credentials1)) >> mockSftpClient1
        sftpClientCreator.createNewClient(new SftpHost(uri2, credentials2)) >> mockSftpClient2
        1 * mockSftpClient1.host >> new SftpHost(uri1, credentials1)
        1 * mockSftpClient2.host >> new SftpHost(uri2, credentials2)
        sftpClientFactory.idleClients.size() == 0
        sftpClientFactory.allClients.size() == 0
        1 * mockSftpClient1.stop()
        1 * mockSftpClient2.stop()
    }

    def "Multiple threads can create and release a client concurrently"() {
        def mockSftpClient = Mock(LockableSftpClient)

        given:
        URI uri = new URI('http://localhost:22/repo')
        PasswordCredentials credentials = TestCredentialUtil.defaultPasswordCredentials('sftp', 'sftp')

        when:
        async {
            10.times {
                start {
                    LockableSftpClient actualClient = sftpClientFactory.createSftpClient(uri, credentials)
                    assert actualClient != null
                    sftpClientFactory.releaseSftpClient(actualClient)
                }
            }
        }

        then:
        sftpClientCreator.createNewClient(new SftpHost(uri, credentials)) >> mockSftpClient
        mockSftpClient.host >> new SftpHost(uri, credentials)
        sftpClientFactory.idleClients.size() > 0
        sftpClientFactory.allClients.size() == sftpClientFactory.idleClients.size()
    }

    def "Creates new client if currently in use by different thread"() {
        def mockSftpClient1 = Mock(LockableSftpClient)
        def mockSftpClient2 = Mock(LockableSftpClient)

        given:
        URI uri = new URI('http://localhost:22/repo')
        PasswordCredentials credentials = org.gradle.util.TestCredentialUtil.defaultPasswordCredentials('sftp', 'sftp')
        LockableSftpClient actualClient1
        LockableSftpClient actualClient2

        when:
        async {
            start {
                actualClient1 = sftpClientFactory.createSftpClient(uri, credentials)
                instant.action1
                thread.blockUntil.action2
                sftpClientFactory.releaseSftpClient(actualClient1)
            }

            start {
                actualClient2 = sftpClientFactory.createSftpClient(uri, credentials)
                instant.action2
                thread.blockUntil.action1
                sftpClientFactory.releaseSftpClient(actualClient2)
            }
        }

        then:
        1 * sftpClientCreator.createNewClient(new SftpHost(uri, credentials)) >> mockSftpClient1
        1 * sftpClientCreator.createNewClient(new SftpHost(uri, credentials)) >> mockSftpClient2
        1 * mockSftpClient1.host >> new SftpHost(uri, credentials)
        1 * mockSftpClient2.host >> new SftpHost(uri, credentials)
        sftpClientFactory.idleClients.size() == 2
        sftpClientFactory.allClients.size() == 2
        actualClient1 != actualClient2
    }

    def "creates a new client when no existing clients are connected"() {
        def mockSftpClient1 = Mock(LockableSftpClient)
        def mockSftpClient2 = Mock(LockableSftpClient)
        def mockSftpClient3 = Mock(LockableSftpClient)

        given:
        URI uri = new URI('http://localhost:22/repo')
        PasswordCredentials credentials = TestCredentialUtil.defaultPasswordCredentials('sftp', 'sftp')

        when:
        LockableSftpClient client1 = sftpClientFactory.createSftpClient(uri, credentials)
        LockableSftpClient client2 = sftpClientFactory.createSftpClient(uri, credentials)
        sftpClientFactory.releaseSftpClient(client1)
        sftpClientFactory.releaseSftpClient(client2)
        LockableSftpClient client3 = sftpClientFactory.createSftpClient(uri, credentials)

        then:
        3 * sftpClientCreator.createNewClient(new SftpHost(uri, credentials)) >>> [mockSftpClient1,mockSftpClient2,mockSftpClient3]
        1 * mockSftpClient1.host >> new SftpHost(uri, credentials)
        1 * mockSftpClient2.host >> new SftpHost(uri, credentials)
        1 * mockSftpClient1.connected >> false
        1 * mockSftpClient2.connected >> false
        sftpClientFactory.idleClients.size() == 0
        sftpClientFactory.allClients.size() == 1
        client3 == mockSftpClient3
    }

    def "reuses a different client when an existing client is no longer connected"() {
        def mockSftpClient1 = Mock(LockableSftpClient)
        def mockSftpClient2 = Mock(LockableSftpClient)

        given:
        URI uri = new URI('http://localhost:22/repo')
        PasswordCredentials credentials = org.gradle.util.TestCredentialUtil.defaultPasswordCredentials('sftp', 'sftp')

        when:
        LockableSftpClient client1 = sftpClientFactory.createSftpClient(uri, credentials)
        LockableSftpClient client2 = sftpClientFactory.createSftpClient(uri, credentials)
        sftpClientFactory.releaseSftpClient(client1)
        sftpClientFactory.releaseSftpClient(client2)
        LockableSftpClient client3 = sftpClientFactory.createSftpClient(uri, credentials)

        then:
        2 * sftpClientCreator.createNewClient(new SftpHost(uri, credentials)) >>> [mockSftpClient1,mockSftpClient2]
        1 * mockSftpClient1.host >> new SftpHost(uri, credentials)
        1 * mockSftpClient2.host >> new SftpHost(uri, credentials)
        1 * mockSftpClient1.connected >> false
        1 * mockSftpClient2.connected >> true
        sftpClientFactory.idleClients.size() == 0
        sftpClientFactory.allClients.size() == 1
        client3 == mockSftpClient2
    }

    def "all clients are stopped even if they are not released"() {
        def mockSftpClient1 = Mock(LockableSftpClient)
        def mockSftpClient2 = Mock(LockableSftpClient)

        given:
        URI uri1 = new URI('http://localhost:22/repo1')
        URI uri2 = new URI('http://localhost:22/repo2')
        PasswordCredentials credentials1 = org.gradle.util.TestCredentialUtil.defaultPasswordCredentials('sftp1', 'sftp1')
        PasswordCredentials credentials2 = org.gradle.util.TestCredentialUtil.defaultPasswordCredentials('sftp2', 'sftp2')

        when:
        LockableSftpClient client1 = sftpClientFactory.createSftpClient(uri1, credentials1)
        LockableSftpClient client2 = sftpClientFactory.createSftpClient(uri2, credentials2)

        and:
        sftpClientFactory.releaseSftpClient(client1)
        // client2 has not been released
        sftpClientFactory.stop()

        then:
        sftpClientCreator.createNewClient(new SftpHost(uri1, credentials1)) >> mockSftpClient1
        sftpClientCreator.createNewClient(new SftpHost(uri2, credentials2)) >> mockSftpClient2
        1 * mockSftpClient1.host >> new SftpHost(uri1, credentials1)
        sftpClientFactory.idleClients.size() == 0
        sftpClientFactory.allClients.size() == 0
        1 * mockSftpClient1.stop()
        1 * mockSftpClient2.stop()
    }

    private List<SftpHost> getClientsForSftpHost(URI uri, PasswordCredentials credentials) {
        sftpClientFactory.idleClients.get(new SftpHost(uri, credentials))
    }
}

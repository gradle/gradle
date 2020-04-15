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

package org.gradle.api.publication.maven.internal.wagon
import org.apache.maven.wagon.ResourceDoesNotExistException
import org.apache.maven.wagon.TransferFailedException
import org.apache.maven.wagon.authentication.AuthenticationInfo
import org.apache.maven.wagon.events.SessionListener
import org.apache.maven.wagon.events.TransferListener
import org.apache.maven.wagon.proxy.ProxyInfo
import org.apache.maven.wagon.proxy.ProxyInfoProvider
import org.apache.maven.wagon.repository.Repository
import org.gradle.api.GradleException
import org.gradle.internal.resource.ReadableContent
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

class RepositoryTransportDeployWagonTest extends Specification {

    @Rule
    final TestNameTestDirectoryProvider testDirectory = new TestNameTestDirectoryProvider(getClass())

    def "wagon connections attempts should set a repository and signal session opening events"() {
        setup:
        SessionListener sessionListener = Mock()
        RepositoryTransportDeployWagon wagon = new RepositoryTransportDeployWagon()
        wagon.addSessionListener(sessionListener)

        when:
        wagon."$method"(*args)

        then:
        wagon.getRepository()
        1 * sessionListener.sessionLoggedIn(_)
        1 * sessionListener.sessionOpened(_)

        where:
        method    | args
        'connect' | [Mock(Repository)]
        'connect' | [Mock(Repository), Mock(ProxyInfo)]
        'connect' | [Mock(Repository), Mock(ProxyInfoProvider)]
        'connect' | [Mock(Repository), Mock(AuthenticationInfo)]
        'connect' | [Mock(Repository), Mock(AuthenticationInfo), Mock(ProxyInfo)]
        'connect' | [Mock(Repository), Mock(AuthenticationInfo), Mock(ProxyInfoProvider)]
    }

    def "wagon disconnect should signal disconnection events"() {
        setup:
        SessionListener sessionListener = Mock()
        RepositoryTransportDeployWagon wagon = new RepositoryTransportDeployWagon()
        wagon.addSessionListener(sessionListener)

        when:
        wagon.disconnect()

        then:
        1 * sessionListener.sessionDisconnecting(_)
        1 * sessionListener.sessionLoggedOff(_)
        1 * sessionListener.sessionDisconnected(_);
    }

    def "should throw GradleException for a bunch of unused wagon methods"() {
        setup:
        RepositoryTransportDeployWagon wagon = new RepositoryTransportDeployWagon()

        when:
        wagon."$method"(*args)

        then:
        thrown(GradleException)

        where:
        method           | args
        'getFileList'    | ['s']
        'getIfNewer'     | ['a', Mock(File), 0]
        'putDirectory'   | [Mock(File), 'a']
        'resourceExists' | ['a']
    }

    def "should provide defaults which ignore maven centric stuff"() {
        RepositoryTransportDeployWagon wagon = new RepositoryTransportDeployWagon()

        expect:
        !wagon.supportsDirectoryCopy()
        wagon.getTimeout() == 0
        !wagon.isInteractive()
    }

    def "should signal progress events when input stream is read"() {
        setup:
        def transferListener = Mock(TransferListener)
        RepositoryTransportWagonAdapter delegate = Mock()
        delegate.putRemoteFile(*_) >> { ReadableContent resource, String resourceName ->
            def is = resource.open()
            // 3 reads >> 3 events
            is.read()
            is.read(new byte[3])
            is.read(new byte[3], 1, 2)
            is.close()
        }

        def file = testDirectory.createFile('target.jar')
        file << "here is some file content"

        def resourceName = '/some/resource.jar'

        RepositoryTransportDeployWagon wagon = new RepositoryTransportDeployWagon()
        wagon.addTransferListener(transferListener)
        wagon.contextualize(delegate)

        when:
        wagon.put(file, resourceName)

        //Order matters
        then:
        1 * transferListener.transferInitiated(_)
        then:
        1 * transferListener.transferStarted(_)
        then:
        3 * transferListener.transferProgress(*_)
        then:
        1 * transferListener.transferCompleted(_)
        then:
        0 * transferListener._
    }

    def "should signal correct events on a failed upload"() {
        setup:
        SessionListener sessionListener = Mock()
        TransferListener transferListener = Mock()
        def failure = new IOException("failed")
        RepositoryTransportWagonAdapter delegate = Mock()
        delegate.putRemoteFile(*_) >> { ReadableContent resource, String resourceName ->
            resource.open().close()
            throw failure
        }

        RepositoryTransportDeployWagon wagon = new RepositoryTransportDeployWagon()
        def file = testDirectory.createFile('target.jar')
        def resourceName = '/some/resource.jar'

        wagon.addSessionListener(sessionListener)
        wagon.addTransferListener(transferListener)
        wagon.contextualize(delegate)

        when:
        wagon.put(file, resourceName)

        then:
        1 * transferListener.transferInitiated(_)
        1 * transferListener.transferStarted(_)
        1 * transferListener.transferError(_)

        then:
        0 * transferListener._

        then:
        def ex = thrown(TransferFailedException)
        ex.cause == failure
    }

    def "should signal the correct events on a successful retrieval"() {
        setup:
        TransferListener transferListener = Mock()
        RepositoryTransportWagonAdapter delegate = Mock()

        RepositoryTransportDeployWagon wagon = new RepositoryTransportDeployWagon()
        def file = testDirectory.createFile('target.jar')
        file << "someText"
        def resourceName = '/some/resource.jar'

        wagon.addTransferListener(transferListener)
        wagon.contextualize(delegate)

        when:
        wagon.get(resourceName, file)

        then:
        1 * transferListener.transferInitiated(_)
        then:
        1 * transferListener.transferStarted(_)
        then:
        1 * delegate.getRemoteFile(file, resourceName) >> true
        then:
        1 * transferListener.transferCompleted(*_)
        then:
        0 * transferListener._
    }

    def "should create the destination file if the deployer supplies a file which does not exist"() {
        setup:
        TransferListener transferListener = Mock()
        RepositoryTransportWagonAdapter delegate = Mock()

        RepositoryTransportDeployWagon wagon = new RepositoryTransportDeployWagon()
        def resourceName = '/some/resource.jar'

        TestFile file = testDirectory.createFile('target.jar')
        file.delete()

        wagon.addTransferListener(transferListener)
        wagon.contextualize(delegate)

        when:
        assert !file.exists()
        wagon.get(resourceName, file)

        then:
        1 * transferListener.transferInitiated(_)
        then:
        1 * transferListener.transferStarted(_)
        then:
        1 * delegate.getRemoteFile(file, resourceName) >> true
        then:
        1 * transferListener.transferCompleted(*_)
        then:
        0 * transferListener._

        and:
        file.exists()
    }

    def "should throw ResourceDoesNotExistException and signal events when the remote resource does not exist"() {
        setup:
        TransferListener transferListener = Mock()
        RepositoryTransportWagonAdapter delegate = Mock()

        RepositoryTransportDeployWagon wagon = new RepositoryTransportDeployWagon()
        def file = testDirectory.createFile('target.jar')
        def resourceName = '/some/resource.jar'

        wagon.addTransferListener(transferListener)
        wagon.contextualize(delegate)

        when:
        wagon.get(resourceName, file)

        then:
        1 * transferListener.transferInitiated(_)
        then:
        1 * transferListener.transferStarted(_)

        then:
        delegate.getRemoteFile(file, resourceName) >> false

        then: "Normally indicates to the deployer that it's a first time snapshot publish"
        thrown(ResourceDoesNotExistException)
    }

    def "should throw TransferFailedException and signal events when failed to download a remote resource"() {
        setup:
        TransferListener transferListener = Mock()
        RepositoryTransportWagonAdapter delegate = Mock()

        RepositoryTransportDeployWagon wagon = new RepositoryTransportDeployWagon()
        def file = testDirectory.createFile('target.jar')
        def resourceName = '/some/resource.jar'

        wagon.addTransferListener(transferListener)
        wagon.contextualize(delegate)
        delegate.getRemoteFile(*_) >> { throw new IOException("Explode!") }

        when:
        wagon.get(resourceName, file)

        then:
        1 * transferListener.transferInitiated(_)
        then:
        1 * transferListener.transferStarted(_)

        then:
        thrown(TransferFailedException)

        then:
        1 * transferListener.transferError(_)
    }

    def "should add and remove wagon listeners"() {
        TransferListener transferListener = Mock()
        SessionListener sessionListener = Mock()

        RepositoryTransportDeployWagon wagon = new RepositoryTransportDeployWagon()
        wagon.addTransferListener(transferListener)
        wagon.addSessionListener(sessionListener)

        when:
        wagon.removeSessionListener(sessionListener)
        wagon.removeTransferListener(transferListener)

        then:
        !wagon.hasSessionListener(sessionListener)
        !wagon.hasTransferListener(transferListener)
    }
}

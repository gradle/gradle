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

package org.gradle.cache.internal

import org.gradle.cache.FileLock
import org.gradle.cache.FileLockManager
import org.gradle.cache.FileLockReleasedSignal
import org.gradle.cache.internal.filelock.DefaultLockOptions
import org.gradle.cache.internal.locklistener.DefaultFileLockCommunicator
import org.gradle.cache.internal.locklistener.DefaultFileLockContentionHandler
import org.gradle.cache.internal.locklistener.FileLockContentionHandler
import org.gradle.cache.internal.locklistener.FileLockPacketPayload
import org.gradle.cache.internal.locklistener.FileLockPacketType
import org.gradle.cache.internal.locklistener.InetAddressProvider
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.executer.GradleHandle
import org.gradle.internal.concurrent.DefaultExecutorFactory
import org.gradle.internal.remote.internal.inet.InetAddressFactory
import org.gradle.internal.time.Time

import java.util.function.Consumer

import static org.gradle.test.fixtures.ConcurrentTestUtil.poll
import static org.gradle.util.internal.TextUtil.escapeString

class DefaultFileLockManagerContentionIntegrationTest extends AbstractIntegrationSpec {
    def addressFactory = new InetAddressFactory()

    FileLockContentionHandler receivingFileLockContentionHandler
    TestableFileLockCommunicator communicator
    FileLock receivingLock

    def setup() {
        executer.withArguments("-d")
        executer.requireOwnGradleUserHomeDir().withDaemonBaseDir(file("daemonsRequestingLock")).requireDaemon()
        buildFile """
            import org.gradle.cache.FileLockManager
            import org.gradle.cache.internal.filelock.DefaultLockOptions

            abstract class FileLocker extends DefaultTask {
                @Inject
                abstract FileLockManager getFileLockManager()

                @Inject
                abstract ProjectLayout getProjectLayout()

                @TaskAction
                void lockIt() {
                    def lock
                    try {
                        lock = fileLockManager.lock(projectLayout.projectDirectory.file("locks/testlock").asFile, DefaultLockOptions.mode(FileLockManager.LockMode.Exclusive), "task file lock")
                    } finally {
                        lock?.close()
                    }
                }
            }

            tasks.register("lock", FileLocker)
        """
    }

    def "the lock holder is not hammered with ping requests for the shared lock"() {
        given:
        setupLockOwner({
            it.trigger()
        })

        when:
        // for the lock holder, do not respond to messages for a bit
        communicator.allowCommunication = false

        // spin up a client to request the same lock
        def build = executer.withTasks("lock").start()
        def timer = Time.startTimer()

        // once we've seen a few pings, allow the lock holder to respond
        def pingCountInOutput = 0
        poll(120) {
            pingCountInOutput = (build.standardOutput =~ 'Pinged owner at port').count
            assert pingCountInOutput >= 3
        }
        // lock holder will respond and release the lock
        communicator.allowCommunication = true
        receivingLock.close()
        then:
        build.waitForFinish()
        // If the lock requestor went wild, we would see lots of ping counts or lots of total messages
        communicator.totalMessages == pingCountInOutput
        communicator.totalMessages < 50 // sanity check that we didn't send too many pings
        // Sanity check that we waited at least some time before releasing the lock
        // IOW, we don't want to see 3 pings in ~milliseconds and call this test valid
        timer.elapsedMillis > 3000 // See: DefaultFileLockContentionHandler.PING_DELAY
    }

    def "the lock holder starts the release request only once and discards additional requests in the meantime"() {
        given:
        def requestReceived = false
        def terminate = false
        setupLockOwner {
            requestReceived = true
            while(!terminate) { Thread.sleep(100) } //block the release action thread
        }

        when:
        def build = executer.withTasks("lock").start()
        poll(120) { assert requestReceived }

        // simulate additional requests
        def socket = new DatagramSocket(0, addressFactory.wildcardBindingAddress)
        (1..500).each {
            def address = addressFactory.localBindingAddress
            byte[] bytes = FileLockPacketPayload.encode(0, FileLockPacketType.UNKNOWN)
            DatagramPacket confirmPacket = new DatagramPacket(bytes, bytes.length, address, communicator.port)
            socket.send(confirmPacket)
        }

        then:
        waitCloseAndFinish(build)

        cleanup:
        terminate = true
    }

    def "if the lock holder confirmed the request, it is not pinged again"() {
        given:
        def requestReceived = false
        setupLockOwner() { requestReceived = true }

        when:
        def build = executer.withTasks("lock").start()
        poll(120) {
            assert requestReceived
        }

        then:
        waitCloseAndFinish(build)
        countPingsSent(build) == 1
        communicator.totalMessages == 1
    }

    def "the lock holder confirms that a request is in process to multiple requesters"() {
        given:
        def requestReceived = false
        setupLockOwner() { requestReceived = true }

        when:
        // Debug logging might have logged exceptions from other Gradle systems, e.g. execution engine, so we disable stacktrace checks
        def build1 = executer.withStackTraceChecksDisabled().withArguments("-d").withTasks("lock").start()
        def build2 = executer.withStackTraceChecksDisabled().withArguments("-d").withTasks("lock").start()
        def build3 = executer.withStackTraceChecksDisabled().withArguments("-d").withTasks("lock").start()
        poll(120) {
            assert requestReceived
            assertConfirmationCount(build1)
            assertConfirmationCount(build2)
            assertConfirmationCount(build3)
        }

        then:
        waitCloseAndFinish(build1)
        build2.waitForFinish()
        build3.waitForFinish()
        assertConfirmationCount(build1)
        assertConfirmationCount(build2)
        assertConfirmationCount(build3)
        communicator.totalMessages == 3
    }

    def "the lock holder confirms lock releases to multiple requesters"() {
        given:
        def signal
        setupLockOwner() { s ->
            signal = s
        }

        when:
        // Debug logging might have logged exceptions from other Gradle systems, e.g. execution engine, so we disable stacktrace checks
        def build1 = executer.withStackTraceChecksDisabled().withArguments("-d").withTasks("lock").start()
        def build2 = executer.withStackTraceChecksDisabled().withArguments("-d").withTasks("lock").start()
        def build3 = executer.withStackTraceChecksDisabled().withArguments("-d").withTasks("lock").start()

        then:
        poll(120) {
            assert signal != null
            assertConfirmationCount(build1)
            assertConfirmationCount(build2)
            assertConfirmationCount(build3)
        }

        when:
        signal.trigger()

        then:
        poll(120) {
            assertReleaseSignalTriggered(build1)
            assertReleaseSignalTriggered(build2)
            assertReleaseSignalTriggered(build3)
        }

        then:
        receivingLock.close()
        build1.waitForFinish()
        build2.waitForFinish()
        build3.waitForFinish()
    }

    // This test simulates a long running Zinc compiler setup by running code similar to ZincScalaCompilerFactory through the worker API.
    // if many workers wait for the same exclusive lock, a worker does not time out because several others get the lock before
    def "worker not timeout"() {
        given:
        def gradleUserHome = file("home").absoluteFile
        buildFile << """
            task doWorkInWorker(type: WorkerTask) {
                gradleUserHome = file("${escapeString(gradleUserHome)}")
            }
        """
        buildFile """
            import org.gradle.cache.scopes.ScopedCacheBuilderFactory
            import org.gradle.cache.PersistentCache
            import org.gradle.cache.FileLockManager
            import org.gradle.internal.logging.events.OutputEventListener
            import org.gradle.internal.nativeintegration.services.NativeServices
            import org.gradle.internal.nativeintegration.services.NativeServices.NativeServicesMode
            import org.gradle.internal.service.DefaultServiceRegistry
            import org.gradle.internal.service.scopes.GlobalScopeServices
            import org.gradle.internal.service.ServiceRegistryBuilder
            import org.gradle.internal.service.scopes.GradleUserHomeScopeServices
            import org.gradle.workers.WorkParameters
            import org.gradle.workers.WorkAction
            import org.gradle.internal.instrumentation.agent.AgentStatus
            import org.gradle.cache.scopes.GlobalScopedCacheBuilderFactory

            abstract class WorkerTask extends DefaultTask {
                @Inject
                abstract WorkerExecutor getWorkerExecutor()

                @Internal
                abstract DirectoryProperty getGradleUserHome()

                @TaskAction
                void doWork() {
                    (1..8).each {
                        workerExecutor.processIsolation().submit(ToolSetupWorkAction) {
                            it.gradleUserHome = this.gradleUserHome
                        }
                    }
                }
            }

            interface ToolSetupWorkParameters extends WorkParameters {
                DirectoryProperty getGradleUserHome()
            }

            abstract class ToolSetupWorkAction implements WorkAction<ToolSetupWorkParameters> {
                void execute() {
                    ScopedCacheBuilderFactory cacheBuilderFactory = ZincCompilerServices.getInstance(parameters.gradleUserHome.get().asFile).get(GlobalScopedCacheBuilderFactory.class)
                    println "Waiting for lock..."
                    final PersistentCache zincCache = cacheBuilderFactory.createCacheBuilder("zinc-0.3.15")
                            .withDisplayName("Zinc 0.3.15 compiler cache")
                            .withInitialLockMode(FileLockManager.LockMode.Exclusive)
                            .open()
                    println "Starting work..."
                    try {
                        Thread.sleep(10000) //setup an external tool which can take some time
                    } finally {
                        zincCache.close()
                    }
                }
            }

            class ZincCompilerServices extends DefaultServiceRegistry {
                private static def instance

                private ZincCompilerServices(File gradleUserHome) {
                    super(NativeServices.getInstance())

                    add(OutputEventListener.class, OutputEventListener.NO_OP)
                    addProvider(new GlobalScopeServices(true, AgentStatus.disabled()))
                }

                 static def getInstance(File gradleUserHome) {
                    if (instance == null) {
                        NativeServices.initializeOnWorker(gradleUserHome, NativeServicesMode.ENABLED)
                        def global = new ZincCompilerServices(gradleUserHome)
                        ServiceRegistryBuilder builder = ServiceRegistryBuilder.builder()
                        builder.parent(global)
                        builder.provider(new GradleUserHomeScopeServices(global))
                        instance = builder.build()
                    }
                    return instance
                }
            }
        """

        when:
        executer.withArguments()

        then:
        succeeds "doWorkInWorker"
    }

    void assertConfirmationCount(GradleHandle build, FileLock lock = receivingLock) {
        assert (build.standardOutput =~ "Gradle process at port ${communicator.port} confirmed unlock request for lock with id ${lock.lockId}.").count == 1
    }

    void assertReleaseSignalTriggered(GradleHandle build, FileLock lock = receivingLock) {
        assert (build.standardOutput =~ "Triggering lock release signal for lock with id ${lock.lockId}.").count > 0
    }

    def countPingsSent(GradleHandle build) {
        (build.standardOutput =~ "Pinged owner at port ${communicator.port}").count
    }

    def waitCloseAndFinish(GradleHandle build) {
        Thread.sleep(10 * 1000) //wait 10 seconds, in which the build should only wait for the lock to become available
        receivingLock.close()
        build.waitForFinish()
    }

    def setupLockOwner(Consumer<FileLockReleasedSignal> whenContended = null) {
        def inetAddressProvider = new InetAddressProvider() {
            @Override
            InetAddress getWildcardBindingAddress() {
                return addressFactory.wildcardBindingAddress
            }

            @Override
            InetAddress getCommunicationAddress() {
                return addressFactory.localBindingAddress
            }
        }

        communicator = new TestableFileLockCommunicator(new DefaultFileLockCommunicator(inetAddressProvider))

        receivingFileLockContentionHandler = new DefaultFileLockContentionHandler(communicator, inetAddressProvider, new DefaultExecutorFactory())
        def fileLockManager = new DefaultFileLockManager(new ProcessMetaDataProvider() {
            String getProcessIdentifier() { return "pid" }
            String getProcessDisplayName() { return "process" }
        }, receivingFileLockContentionHandler)

        receivingLock = fileLockManager.lock(file("locks/testlock"), DefaultLockOptions.mode(FileLockManager.LockMode.Exclusive), "testlock", "test holding lock", whenContended)
    }
}

/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.cache

import org.gradle.cache.internal.locklistener.FileLockPacketPayload
import org.gradle.cache.internal.locklistener.FileLockPacketType
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.executer.GradleDistribution
import org.gradle.integtests.fixtures.timeout.IntegrationTestTimeout
import org.gradle.internal.remote.internal.inet.InetAddressFactory
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.server.http.BlockingHttpServer

@IntegrationTestTimeout(120)
class LockCommunicationSoakTest extends AbstractIntegrationSpec {
    private final BlockingHttpServer blockingServer = new BlockingHttpServer()
    private final TestFile oldBuild = file("old-build").createDir()
    private final GradleDistribution oldDistribution = buildContext.distribution("8.12")
    private final oldExecuter = oldDistribution.executer(temporaryFolder, buildContext).usingProjectDirectory(oldBuild)

    def setup() {
        blockingServer.start()
        // This is used by Gradle 8.12, so it needs to be written with the APIs available with that
        // version.
        oldBuild.file("settings.gradle").touch()
        oldBuild.file("build.gradle") << """
            import org.gradle.cache.scopes.GlobalScopedCacheBuilderFactory
            import org.gradle.cache.internal.locklistener.FileLockContentionHandler

            def fileLockContentionHandler = gradle.services.get(FileLockContentionHandler)
            try {
                fileLockContentionHandler.stop(12345L)
                fileLockContentionHandler.start(12345L, {})
            } catch (Exception e) {
                // ignore
            }
            file("port").text = fileLockContentionHandler.reservePort().toString()

            abstract class LockTask extends DefaultTask {
                @Inject
                protected abstract GlobalScopedCacheBuilderFactory getCacheBuilderFactory()

                @TaskAction
                void run() {
                    def cache = cacheBuilderFactory.createCrossVersionCacheBuilder("soak-test")
                        .withDisplayName("Soak test")
                        .withInitialLockMode(org.gradle.cache.FileLockManager.LockMode.OnDemand)
                        .open()

                    ${blockingServer.callFromTaskAction("old-before")}
                    println("about to use the cache")
                    cache.useCache {
                        println("Using cache " + cache.baseDir)
                        ${blockingServer.callFromTaskAction("old-acquire")}
                    } as Runnable

                    ${blockingServer.callFromTaskAction("old-close")}
                    cache.close()
                    println("cache closed")
                }
            }

            tasks.register("lock", LockTask)
        """

        // This uses the latest Gradle version, so it can use the latest APIs.
        buildFile """
            import org.gradle.cache.scopes.GlobalScopedCacheBuilderFactory
            import org.gradle.cache.internal.locklistener.FileLockContentionHandler

            def fileLockContentionHandler = gradle.services.get(FileLockContentionHandler)
            try {
                fileLockContentionHandler.stop(12345L)
                fileLockContentionHandler.start(12345L, {})
            } catch (Exception e) {
                // ignore
            }
            file("port").text = fileLockContentionHandler.reservePort().toString()


            abstract class LockTask extends DefaultTask {
                @Inject
                protected abstract GlobalScopedCacheBuilderFactory getCacheBuilderFactory()

                @TaskAction
                void run() {
                    def cache = cacheBuilderFactory.createCrossVersionCacheBuilder("soak-test")
                        .withDisplayName("Soak test")
                        .withInitialLockMode(org.gradle.cache.FileLockManager.LockMode.OnDemand)
                        .open()

                    ${blockingServer.callFromTaskAction("new-before")}
                    println("about to use the cache")
                    cache.useCache {
                        println("Using cache " + cache.baseDir)
                        ${blockingServer.callFromTaskAction("new-acquire")}
                    } as Runnable

                    ${blockingServer.callFromTaskAction("new-close")}
                    cache.close()
                    println("cache closed")
                }
            }

            tasks.register("lock", LockTask)
        """
    }
    def cleanup() {
        blockingServer.stop()
    }

    def "lock contention - happy path new to old"() {
        def aboutToLock = blockingServer.expectAndBlock("new-before")
        def releaseLock = blockingServer.expectAndBlock("new-acquire")

        def aboutToRequest = blockingServer.expectAndBlock("old-before")
        def acquireLock = blockingServer.expectConcurrentAndBlock("old-acquire", "new-close")

        def complete = blockingServer.expectAndBlock("old-close")

        expect:
        def newHandle = executer.withTasks('lock').start()

        // wait for new Gradle to start the task
        aboutToLock.waitForAllPendingCalls()
        aboutToLock.releaseAll()

        // wait for new Gradle to acquire the lock
        releaseLock.waitForAllPendingCalls()

        // Start the old Gradle to request the same lock
        def oldHandle = oldExecuter.withTasks('lock').start()
        // wait for old Gradle to start the task
        aboutToRequest.waitForAllPendingCalls()

        // new Gradle should be holding the lock, so release old Gradle
        aboutToRequest.releaseAll()

        // old Gradle is now waiting to acquire the lock
        // new Gradle can now release the lock
        releaseLock.releaseAll()
        // old Gradle should acquire the lock
        acquireLock.waitForAllPendingCalls()
        acquireLock.release("old-acquire")

        // Both Gradle instances should now complete
        complete.waitForAllPendingCalls()
        complete.releaseAll()
        acquireLock.releaseAll()

        newHandle.waitForFinish()
        oldHandle.waitForFinish()
    }

    def "lock contention - happy path old to new"() {
        def aboutToLock = blockingServer.expectAndBlock("old-before")
        def releaseLock = blockingServer.expectAndBlock("old-acquire")

        def aboutToRequest = blockingServer.expectAndBlock("new-before")
        def acquireLock = blockingServer.expectConcurrentAndBlock("new-acquire", "old-close")

        def complete = blockingServer.expectAndBlock("new-close")

        expect:
        def oldHandle = oldExecuter.withTasks('lock').start()

        // wait for old Gradle to start the task
        aboutToLock.waitForAllPendingCalls()
        aboutToLock.releaseAll()

        // wait for old Gradle to acquire the lock
        releaseLock.waitForAllPendingCalls()

        // Start the new Gradle to request the same lock
        def newHandle = executer.withTasks('lock').start()
        // wait for new Gradle to start the task
        aboutToRequest.waitForAllPendingCalls()

        // old Gradle should be holding the lock, so release new Gradle
        aboutToRequest.releaseAll()

        // new Gradle is now waiting to acquire the lock
        // old Gradle can now release the lock
        releaseLock.releaseAll()
        // new Gradle should acquire the lock
        acquireLock.waitForAllPendingCalls()
        acquireLock.release("new-acquire")

        // Both Gradle instances should now complete
        complete.waitForAllPendingCalls()
        complete.releaseAll()
        acquireLock.releaseAll()

        oldHandle.waitForFinish()
        newHandle.waitForFinish()
    }

    def "lock contention - poison new"() {
        def aboutToLock = blockingServer.expectAndBlock("new-before")
        def releaseLock = blockingServer.expectAndBlock("new-acquire")

        def aboutToRequest = blockingServer.expectAndBlock("old-before")
        def acquireLock = blockingServer.expectConcurrentAndBlock("old-acquire", "new-close")

        def complete = blockingServer.expectAndBlock("old-close")

        expect:
        def newHandle = executer.withTasks('lock').start()

        // wait for new Gradle to start the task
        aboutToLock.waitForAllPendingCalls()
        aboutToLock.releaseAll()

        // wait for new Gradle to acquire the lock
        releaseLock.waitForAllPendingCalls()

        // Start the old Gradle to request the same lock
        def oldHandle = oldExecuter.withTasks('lock').start()
        // wait for old Gradle to start the task
        aboutToRequest.waitForAllPendingCalls()

        // new Gradle should be holding the lock, so release old Gradle
        // Try to kill the listener in the lock owner
        poison(file("port").text.toInteger())
        aboutToRequest.releaseAll()

        // old Gradle is now waiting to acquire the lock
        // new Gradle can now release the lock
        releaseLock.releaseAll()
        // old Gradle should acquire the lock
        acquireLock.waitForAllPendingCalls()
        acquireLock.release("old-acquire")

        // Both Gradle instances should now complete
        complete.waitForAllPendingCalls()
        complete.releaseAll()
        acquireLock.releaseAll()

        newHandle.waitForFinish()
        oldHandle.waitForFinish()
    }

    def "lock contention - poison old"() {
        def aboutToLock = blockingServer.expectAndBlock("old-before")
        def releaseLock = blockingServer.expectAndBlock("old-acquire")

        def aboutToRequest = blockingServer.expectAndBlock("new-before")

        def complete = blockingServer.expectConcurrentAndBlock("old-close")

        expect:
        def oldHandle = oldExecuter.withStackTraceChecksDisabled().withTasks('lock').start()

        // wait for old Gradle to start the task
        aboutToLock.waitForAllPendingCalls()
        aboutToLock.releaseAll()

        // wait for old Gradle to acquire the lock
        releaseLock.waitForAllPendingCalls()

        // Start the new Gradle to request the same lock
        def newHandle = executer.withTasks('lock').start()
        // wait for new Gradle to start the task
        aboutToRequest.waitForAllPendingCalls()

        // old Gradle should be holding the lock, so release new Gradle
        // Try to kill the listener in the lock owner
        poison(oldBuild.file("port").text.toInteger())
        aboutToRequest.releaseAll()

        // new Gradle is now waiting to acquire the lock
        // old Gradle can now release the lock
        releaseLock.releaseAll()

        // old Gradle will now stick around without releasing the lock
        complete.waitForAllPendingCalls()

        // new Gradle can never acquire lock because it cannot communicate to old Gradle

        def failure = newHandle.waitForFailure()
        failure.assertHasCause("Timeout waiting to lock Soak test ")

        complete.releaseAll()
        oldHandle.waitForFinish()
    }

    def "lock contention - daemon expires if too many errors are seen"() {
        def aboutToLock = blockingServer.expectAndBlock("new-before")
        def releaseLock = blockingServer.expectAndBlock("new-acquire")
        def complete = blockingServer.expectAndBlock("new-close")

        expect:
        def newHandle = executer.requireIsolatedDaemons().withStackTraceChecksDisabled().withTasks('lock').start()

        aboutToLock.waitForAllPendingCalls()
        aboutToLock.releaseAll()
        releaseLock.waitForAllPendingCalls()

        // new Gradle should be holding the lock

        100.times {
            poison(file("port").text.toInteger())
        }

        // New Gradle can now release the lock
        releaseLock.releaseAll()

        // Need to wait for the daemon expiration cycle to notice that the daemon is in a bad state
        complete.waitForAllPendingCalls()

        def failure = newHandle.waitForFailure()
        failure.assertHasDescription("Gradle build daemon has been stopped: Unable to coordinate lock ownership with other builds.")
    }

    private void poison(int ownerPort) {
        try {
            def addressFactory = new InetAddressFactory()
            def socket = new DatagramSocket(0, addressFactory.wildcardBindingAddress)
            def message = FileLockPacketPayload.encode(12345L, FileLockPacketType.UNKNOWN)
            message[0] = 0 // bad protocol version
            socket.send(new DatagramPacket(message, message.length, addressFactory.localBindingAddress, ownerPort))
            socket.close()
        } catch (Exception e) {
            // don't care
        }
    }
}

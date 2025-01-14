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

@IntegrationTestTimeout(60)
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

                    ${blockingServer.callFromTaskAction("old-first")}
                    println("about to use the cache")
                    cache.useCache {
                        println("Using cache " + cache.baseDir)
                        ${blockingServer.callFromTaskAction("old-second")}
                    } as Runnable

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

                    ${blockingServer.callFromTaskAction("new-first")}
                    println("about to use the cache")
                    cache.useCache {
                        println("Using cache " + cache.baseDir)
                        ${blockingServer.callFromTaskAction("new-second")}
                    } as Runnable

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
        def aboutToLock = blockingServer.expectAndBlock("new-first")
        def releaseLock = blockingServer.expectAndBlock("new-second")
        def aboutToRequest = blockingServer.expectAndBlock("old-first")
        def acquireLock = blockingServer.expectAndBlock("old-second")

        expect:
        def newHandle = executer.withTasks('lock').start()

        aboutToLock.waitForAllPendingCalls()
        aboutToLock.releaseAll()
        releaseLock.waitForAllPendingCalls()

        // Start the old Gradle to request the same lock
        def oldHandle = oldExecuter.withTasks('lock').start()
        aboutToRequest.waitForAllPendingCalls()

        // new Gradle should be holding the lock

        aboutToRequest.releaseAll()

        // The old Gradle is now waiting to acquire the lock
        // New Gradle can now release the lock
        releaseLock.releaseAll()

        // Old Gradle should acquire the lock
        acquireLock.waitForAllPendingCalls()
        acquireLock.releaseAll()

        newHandle.waitForFinish()
        oldHandle.waitForFinish()
    }

    def "lock contention - happy path old to new"() {
        def aboutToLock = blockingServer.expectAndBlock("old-first")
        def releaseLock = blockingServer.expectAndBlock("old-second")
        def aboutToRequest = blockingServer.expectAndBlock("new-first")
        def acquireLock = blockingServer.expectAndBlock("new-second")

        expect:
        def oldHandle = oldExecuter.withTasks('lock').start()

        aboutToLock.waitForAllPendingCalls()
        aboutToLock.releaseAll()
        releaseLock.waitForAllPendingCalls()

        // Start the new Gradle to request the same lock
        def newHandle = executer.withTasks('lock').start()
        aboutToRequest.waitForAllPendingCalls()

        // old Gradle should be holding the lock

        aboutToRequest.releaseAll()

        // The new Gradle is now waiting to acquire the lock
        // Old Gradle can now release the lock
        releaseLock.releaseAll()

        // New Gradle should acquire the lock
        acquireLock.waitForAllPendingCalls()
        acquireLock.releaseAll()

        newHandle.waitForFinish()
        oldHandle.waitForFinish()
    }

    def "lock contention - poison new"() {
        def aboutToLock = blockingServer.expectAndBlock("new-first")
        def releaseLock = blockingServer.expectAndBlock("new-second")
        def aboutToRequest = blockingServer.expectAndBlock("old-first")
        def acquireLock = blockingServer.expectAndBlock("old-second")

        expect:
        def newHandle = executer.requireIsolatedDaemons().withTasks('lock').start()

        aboutToLock.waitForAllPendingCalls()
        aboutToLock.releaseAll()
        releaseLock.waitForAllPendingCalls()

        // Start the old Gradle to request the same lock
        def oldHandle = oldExecuter.withTasks('lock').start()
        aboutToRequest.waitForAllPendingCalls()

        // new Gradle should be holding the lock
        poison(file("port").text.toInteger())

        aboutToRequest.releaseAll()

        // The old Gradle is now waiting to acquire the lock
        // New Gradle can now release the lock
        releaseLock.releaseAll()

        // Old Gradle should acquire the lock
        acquireLock.waitForAllPendingCalls()
        acquireLock.releaseAll()

        newHandle.waitForFinish()
        oldHandle.waitForFinish()
    }

    def "lock contention - poison old"() {
        def aboutToLock = blockingServer.expectAndBlock("old-first")
        def releaseLock = blockingServer.expectAndBlock("old-second")
        def aboutToRequest = blockingServer.expectAndBlock("new-first")
        def acquireLock = blockingServer.expectAndBlock("new-second")

        expect:
        def oldHandle = oldExecuter.requireIsolatedDaemons().withStackTraceChecksDisabled().withTasks('lock').start()

        aboutToLock.waitForAllPendingCalls()
        aboutToLock.releaseAll()
        releaseLock.waitForAllPendingCalls()

        // Start the new Gradle to request the same lock
        def newHandle = executer.withTasks('lock').start()
        aboutToRequest.waitForAllPendingCalls()

        // old Gradle should be holding the lock
        poison(oldBuild.file("port").text.toInteger())

        aboutToRequest.releaseAll()

        // The new Gradle is now waiting to acquire the lock
        // Old Gradle can now release the lock
        releaseLock.releaseAll()

        // New Gradle should acquire the lock
        acquireLock.waitForAllPendingCalls()
        acquireLock.releaseAll()

        newHandle.waitForFinish()
        oldHandle.waitForFinish()
    }

    def "lock contention - daemon expires if too many errors are seen"() {
        def aboutToLock = blockingServer.expectAndBlock("new-first")
        def releaseLock = blockingServer.expectAndBlock("new-second")

        expect:
        def newHandle = executer.requireIsolatedDaemons().withStackTraceChecksDisabled().withTasks('lock').start()

        aboutToLock.waitForAllPendingCalls()
        aboutToLock.releaseAll()
        releaseLock.waitForAllPendingCalls()

        // new Gradle should be holding the lock

        100.times {
            poison(file("port").text.toInteger())
        }
        // Need to wait for the daemon expiration cycle to notice that the daemon is in a bad state
        Thread.sleep(15000)

        // New Gradle can now release the lock
        releaseLock.releaseAll()

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

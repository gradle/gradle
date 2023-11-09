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
import org.gradle.cache.internal.filelock.LockOptionsBuilder
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.test.fixtures.server.http.BlockingHttpServer
import org.junit.Rule
import spock.lang.Issue
import spock.lang.Subject

@Subject(FixedSharedModeCrossProcessCacheAccess)
class FixedSharedModeCrossProcessCacheAccessIntegrationTest extends AbstractIntegrationSpec {
    @Rule BlockingHttpServer server = new BlockingHttpServer()

    @Issue("https://github.com/gradle/gradle/issues/2737")
    def "parallel initialization attempt of a shared cache does not timeout one of the processes"() {
        given:
        server.start()
        executer.requireOwnGradleUserHomeDir().withDaemonBaseDir(file("daemon")).requireDaemon()
        buildFile << """
            import org.gradle.workers.WorkParameters

            task doWork(type: WorkerTask)

            abstract class WorkerTask extends DefaultTask {
                @Inject
                abstract WorkerExecutor getWorkerExecutor()

                @TaskAction
                void doWork() {
                    workerExecutor.processIsolation().submit(TestWorkAction) { }
                    ${server.callFromBuild("waiting")}
                }
            }
            abstract class TestWorkAction implements WorkAction<WorkParameters.None> { void execute() { } }
        """

        when:
        // start a build with a IsolationMode.PROCESS worker that will initialize the worker classpath cache and wait until the cache was initialized
        def block = server.expectAndBlock("waiting")
        def build = executer.withTasks("doWork").start()
        block.waitForAllPendingCalls()

        // simulate another "build" that tries to initialize the cache as well before recognizing that it has been done already
        def cacheAccess = simulateAnotherInitializationOfAlreadyInitializedWorkerClasspathCache()
        cacheAccess.open()
        block.releaseAll()
        build.waitForFinish()

        then:
        noExceptionThrown()

        cleanup:
        cacheAccess?.close()
    }

    /**
     * This setups a {@link FixedSharedModeCrossProcessCacheAccess} that does initially not see that the cache is already initialised
     * (or in the process of being initialised). This simulates a situation, where two processes happen to find a the cache in an empty state
     * simultaneously. Then the second one, attempting to initialize the cache, will fail to obtain an exclusive lock on the cache. Instead
     * of failing, it should recheck if initialization was performed by calling {@link CacheInitializationAction#requiresInitialization(org.gradle.cache.FileLock)}
     * again. This is simulated here by an requiresInitialization() implementation that returns false after a number of calls to itself.
     */
    private simulateAnotherInitializationOfAlreadyInitializedWorkerClasspathCache() {
        def cacheDir = file("user-home/caches/${distribution.version.version}/workerMain")
        def countingInitAction = new CacheInitializationAction() {
            def count = 0

            boolean requiresInitialization(FileLock fileLock) {
                assert fileLock != null
                count++
                println "Checking initialized $count"
                return count < 4
            }

            void initialize(FileLock fileLock) {}
        }
        def lockOptions = new LockOptionsBuilder(FileLockManager.LockMode.Shared)
        def lockManager1 = DefaultFileLockManagerTestHelper.createDefaultFileLockManager(100)
        new FixedSharedModeCrossProcessCacheAccess("<cache>", cacheDir, lockOptions, lockManager1, countingInitAction, {}, {})
    }
}

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

package org.gradle.performance.regression.buildcache

import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.gradle.internal.hash.Hashing
import org.gradle.performance.fixture.CrossVersionPerformanceTestRunner
import org.gradle.performance.generator.JavaTestProject
import org.gradle.profiler.BuildContext
import org.gradle.profiler.BuildMutator
import org.gradle.profiler.mutations.ApplyAbiChangeToJavaSourceFileMutator
import org.gradle.profiler.mutations.ApplyNonAbiChangeToJavaSourceFileMutator
import org.gradle.test.fixtures.keystore.TestKeyStore

import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

class TaskOutputCachingJavaPerformanceTest extends AbstractTaskOutputCachingPerformanceTest {

    def setup() {
        runner.warmUpRuns = 11
        runner.runs = 21
        runner.minimumBaseVersion = "3.5"
        runner.targetVersions = ["6.7-20200824220048+0000"]
    }

    def "clean assemble with remote http cache"() {
        setupTestProject(runner)
        protocol = "http"
        pushToRemote = true
        runner.addBuildMutator { cleanLocalCache() }
        runner.addBuildMutator() { touchCacheArtifacts() }

        when:
        def result = runner.run()

        then:
        result.assertCurrentVersionHasNotRegressed()
    }

    def "clean assemble with remote https cache"() {
        setupTestProject(runner)
        protocol = "https"
        pushToRemote = true
        runner.addBuildMutator { cleanLocalCache() }
        runner.addBuildMutator { touchCacheArtifacts() }

        def keyStore = TestKeyStore.init(temporaryFolder.file('ssl-keystore'))
        keyStore.enableSslWithServerCert(buildCacheServer)

        runner.gradleOpts.addAll(keyStore.serverAndClientCertArgs)

        when:
        def result = runner.run()

        then:
        result.assertCurrentVersionHasNotRegressed()
    }

    def "clean assemble with empty local cache"() {
        given:
        setupTestProject(runner)
        runner.warmUpRuns = 6
        runner.runs = 8
        pushToRemote = false
        runner.addBuildMutator { cleanLocalCache() }

        when:
        def result = runner.run()

        then:
        result.assertCurrentVersionHasNotRegressed()
    }

    def "clean assemble with empty remote http cache"() {
        given:
        setupTestProject(runner)
        runner.warmUpRuns = 6
        runner.runs = 8
        pushToRemote = true
        runner.addBuildMutator { cleanLocalCache() }
        runner.addBuildMutator { cleanRemoteCache() }

        when:
        def result = runner.run()

        then:
        result.assertCurrentVersionHasNotRegressed()
    }

    def "clean assemble with local cache"() {
        given:
        setupTestProject(runner)
        runner.args += "--parallel"
        pushToRemote = false
        runner.addBuildMutator { touchCacheArtifacts() }

        when:
        def result = runner.run()

        then:
        result.assertCurrentVersionHasNotRegressed()
    }

    def "clean assemble for abi change with local cache"() {
        given:
        setupTestProject(runner)
        def testProject = JavaTestProject.projectFor(runner.testProject)
        runner.addBuildMutator { new ApplyAbiChangeToJavaSourceFileMutator(new File(it.projectDir, testProject.config.fileToChangeByScenario['assemble'])) }
        runner.args += "--parallel"
        pushToRemote = false
        runner.addBuildMutator { touchCacheArtifacts() }

        when:
        def result = runner.run()

        then:
        result.assertCurrentVersionHasNotRegressed()
    }

    def "clean assemble for non-abi change with local cache"() {
        given:
        setupTestProject(runner)
        def testProject = JavaTestProject.projectFor(runner.testProject)
        runner.addBuildMutator { new ApplyNonAbiChangeToJavaSourceFileMutator(new File(it.projectDir, testProject.config.fileToChangeByScenario['assemble'])) }
        runner.args += "--parallel"
        pushToRemote = false
        runner.addBuildMutator { touchCacheArtifacts() }

        when:
        def result = runner.run()

        then:
        result.assertCurrentVersionHasNotRegressed()
    }

    private BuildMutator touchCacheArtifacts() {
        new BuildMutator() {
            @Override
            void beforeBuild(BuildContext context) {
                touchCacheArtifactsDir(cacheDir)
                if (buildCacheServer.running) {
                    touchCacheArtifactsDir(buildCacheServer.cacheDir)
                }
            }
        }
    }

    // We change the file dates inside the archives to work around unfairness caused by
    // reusing FileCollectionSnapshots based on the file dates in versions before Gradle 4.2
    void touchCacheArtifactsDir(File dir) {
        def startTime = System.currentTimeMillis()
        int count = 0
        dir.eachFile { File cacheArchiveFile ->
            if (cacheArchiveFile.name ==~ /[a-z0-9]{${Hashing.defaultFunction().hexDigits}}/) {
                def tempFile = temporaryFolder.file("re-tar-temp")
                tempFile.withOutputStream { outputStream ->
                    def tarOutput = new TarArchiveOutputStream(new GZIPOutputStream(outputStream))
                    tarOutput.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX)
                    tarOutput.setBigNumberMode(TarArchiveOutputStream.BIGNUMBER_POSIX)
                    tarOutput.setAddPaxHeadersForNonAsciiNames(true)
                    cacheArchiveFile.withInputStream { inputStream ->
                        def tarInput = new TarArchiveInputStream(new GZIPInputStream(inputStream))
                        while (true) {
                            def tarEntry = tarInput.nextTarEntry
                            if (tarEntry == null) {
                                break
                            }

                            tarEntry.setModTime(tarEntry.modTime.time + 3743)
                            tarOutput.putArchiveEntry(tarEntry)
                            if (!tarEntry.directory) {
                                tarOutput << tarInput
                            }
                            tarOutput.closeArchiveEntry()
                        }
                    }
                    tarOutput.close()
                }
                assert cacheArchiveFile.delete()
                assert tempFile.renameTo(cacheArchiveFile)
            }
            count++
        }
        def time = System.currentTimeMillis() - startTime
        println "Changed file dates in $count cache artifacts in $dir in ${time} ms"
    }

    static def setupTestProject(CrossVersionPerformanceTestRunner runner) {
        runner.gradleOpts = runner.projectMemoryOptions
        runner.tasksToRun = ["assemble"]
        runner.cleanTasks = ["clean"]
    }
}

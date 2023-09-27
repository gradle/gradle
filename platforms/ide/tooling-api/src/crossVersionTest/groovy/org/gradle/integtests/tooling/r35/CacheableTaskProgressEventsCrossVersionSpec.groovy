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

package org.gradle.integtests.tooling.r35

import org.gradle.integtests.tooling.fixture.ProgressEvents
import org.gradle.integtests.tooling.fixture.ProgressEvents.Operation
import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.TextUtil
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.test.fixtures.file.TestFile
import org.gradle.tooling.ProjectConnection
import org.gradle.tooling.events.OperationType

class CacheableTaskProgressEventsCrossVersionSpec extends ToolingApiSpecification {
    def setup() {
        buildFile << """
            apply plugin: 'base'

            task cacheable {
                def outputFile = new File(buildDir, "output")
                inputs.file("input")
                outputs.file(outputFile)
                outputs.cacheIf { true }

                doLast {
                    outputFile.parentFile.mkdirs()
                    outputFile.text = "done"
                }
            }
        """
        def cacheDir = file("task-output-cache")
        settingsFile << """
            buildCache {
                local {
                    directory = "${TextUtil.escapeString(cacheDir.absolutePath)}"
                }
            }
        """
        file("input").text = "input file"
    }

    @ToolingApiVersion('>=3.3')
    @TargetGradleVersion('>=3.5')
    def "cacheable task generates build operations for load and store"() {
        when:
        def pushToCacheEvents = ProgressEvents.create()
        runCacheableBuild(pushToCacheEvents)
        then:
        writingOperations(pushToCacheEvents).size() == 1

        when:
        file("build").deleteDir()
        and:
        def pullFromCacheResults = ProgressEvents.create()
        runCacheableBuild(pullFromCacheResults)
        then:
        readingOperations(pullFromCacheResults).size() == 1
    }

    @ToolingApiVersion('>=3.3')
    @TargetGradleVersion('>=3.5')
    def "cacheable task generates build operations when using remote cache"() {
        TestFile localCache = file('local-cache')
        TestFile remoteCache = file('remote-cache')
        settingsFile.text = """
            buildCache {
                local {
                    directory = '${localCache.absoluteFile.toURI()}' 
                    push = true
                }
                remote(DirectoryBuildCache) {
                    directory = '${remoteCache.absoluteFile.toURI()}'
                    push = true
                }
            }
        """.stripIndent()


        when:
        def pushToCacheEvents = ProgressEvents.create()
        runCacheableBuild(pushToCacheEvents)

        then:
        writingOperations(pushToCacheEvents).size() == 2

        when:
        file("build").deleteDir()
        and:
        def pullFromCacheResults = ProgressEvents.create()
        runCacheableBuild(pullFromCacheResults)

        then:
        readingOperations(pullFromCacheResults).size() == 1
    }

    private static List<Operation> writingOperations(ProgressEvents pushToCacheEvents) {
        def pushTaskOperation = pushToCacheEvents.operation("Task :cacheable")
        def writingOperations = pushTaskOperation.descendants {
            it.descriptor.displayName =~ /Store entry .+ in (local|remote) build cache/ ||
                it.descriptor.displayName =~ /Pack build cache entry .+/
        }
        writingOperations.each {
            assert !it.children
        }
        return writingOperations
    }

    private static List<Operation> readingOperations(ProgressEvents pullFromCacheResults) {
        def pullTaskOperation = pullFromCacheResults.operation("Task :cacheable")
        def pullOperations = pullTaskOperation.descendants {
            it.descriptor.displayName =~ /Load entry .+ from (local|remote) build cache/ ||
                it.descriptor.displayName =~ /Unpack build cache entry .+/
        }
        pullOperations.each {
            assert !it.children
        }
        return pullOperations
    }

    private void runCacheableBuild(listener, String task = "cacheable") {
        withConnection {
            ProjectConnection connection ->
                connection.newBuild().withArguments("--build-cache").forTasks(task).addProgressListener(listener, EnumSet.of(OperationType.GENERIC, OperationType.TASK)).run()
        }
    }
}

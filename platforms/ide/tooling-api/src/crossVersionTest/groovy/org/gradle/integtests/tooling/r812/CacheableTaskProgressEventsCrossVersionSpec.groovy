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

package org.gradle.integtests.tooling.r812

import org.gradle.integtests.tooling.fixture.ProgressEvents
import org.gradle.integtests.tooling.fixture.ProgressEvents.Operation
import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.TextUtil
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.test.fixtures.file.TestFile
import org.gradle.tooling.ProjectConnection
import org.gradle.tooling.events.OperationType
import org.gradle.util.GradleVersion

@ToolingApiVersion(">=8.12")
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

    @TargetGradleVersion('>=3.5')
    def "cacheable task generates build operations for load and store"() {
        when:
        def pushToCacheEvents = ProgressEvents.create()
        runCacheableBuild(pushToCacheEvents)
        then:
        writingOperations(pushToCacheEvents).size() == maybeIncludeLocalBuildOperations(1)

        when:
        file("build").deleteDir()
        and:
        def pullFromCacheResults = ProgressEvents.create()
        runCacheableBuild(pullFromCacheResults)
        then:
        readingOperations(pullFromCacheResults).size() == maybeIncludeLocalBuildOperations(1)
    }

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
        writingOperations(pushToCacheEvents).size() == maybeIncludeLocalBuildOperations(2)

        when:
        file("build").deleteDir()
        and:
        def pullFromCacheResults = ProgressEvents.create()
        runCacheableBuild(pullFromCacheResults)

        then:
        readingOperations(pullFromCacheResults).size() == maybeIncludeLocalBuildOperations(1)
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

    private List<Operation> readingOperations(ProgressEvents pullFromCacheResults) {
        def pullTaskOperation = pullFromCacheResults.operation("Task :cacheable")
        def pullOperations = pullTaskOperation.descendants {
            it.descriptor.displayName ==~ /Load entry .+ from (local|remote) build cache/
        }
        def unpackOperations = pullTaskOperation.descendants {
            it.descriptor.displayName ==~ /Unpack build cache entry .+/
        }
        if (hasLocalBuildCacheOperations()) {
            unpackOperations.each {
                assert !it.children
            }
            pullOperations.each {
                if (it.descriptor.displayName.contains('local')) {
                    assert unpackOperations.containsAll(it.children)
                } else {
                    assert !it.children
                }
            }
        } else {
            (pullOperations + unpackOperations).each {
                assert !it.children
            }
        }
        return pullOperations + unpackOperations
    }

    private void runCacheableBuild(listener, String task = "cacheable") {
        withConnection {
            ProjectConnection connection ->
                connection.newBuild().withArguments("--build-cache").forTasks(task).addProgressListener(listener, EnumSet.of(OperationType.GENERIC, OperationType.TASK, OperationType.ROOT)).run()
        }
    }

    int maybeIncludeLocalBuildOperations(int expectedNumber) {
        hasLocalBuildCacheOperations()
            ? expectedNumber + 1
            : expectedNumber
    }

    private boolean hasLocalBuildCacheOperations() {
        targetVersion.baseVersion >= GradleVersion.version("8.6")
    }
}

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
import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.TextUtil
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.tooling.ProjectConnection
import org.gradle.tooling.events.OperationType

class TaskProgressEventsCrossVersionSpec extends ToolingApiSpecification {
    def setup() {
        buildFile << """
            apply plugin: 'base'

            task noactions {
            }
            
            task noncacheable {
                doLast {
                }
            }
            
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
        file("gradle.properties") << """
            org.gradle.cache.tasks=true
            systemProp.org.gradle.cache.tasks.directory=${TextUtil.escapeString(cacheDir.absolutePath)}
"""
        file("input").text = "input file"
    }

    @ToolingApiVersion('>=3.3')
    @TargetGradleVersion('>=3.5')
    def "cacheable task generates build operations for load and store"() {
        when:
        def pushToCacheEvents = new ProgressEvents()
        runCacheableBuild(pushToCacheEvents)
        then:
        assertTaskHasWritingOperation(pushToCacheEvents)

        when:
        file("build").deleteDir()
        and:
        def pullFromCacheResults = new ProgressEvents()
        runCacheableBuild(pullFromCacheResults)
        then:
        assertTaskHasReadingOperation(pullFromCacheResults)
    }

    private void assertTaskHasWritingOperation(ProgressEvents pushToCacheEvents) {
        def pushTaskOperation = pushToCacheEvents.operation("Task :cacheable")
        assert pushTaskOperation.children.find { it.descriptor.displayName =~ /Writing cache entry for .+ into cache/ }
    }

    private void assertTaskHasReadingOperation(ProgressEvents pullFromCacheResults) {
        def pullTaskOperation = pullFromCacheResults.operation("Task :cacheable")
        assert pullTaskOperation.children.find { it.descriptor.displayName =~ /Reading cache entry for .+ from cache/ }
    }

    private void runCacheableBuild(listener, String task="cacheable") {
        withConnection {
            ProjectConnection connection ->
                connection.newBuild().forTasks(task).addProgressListener(listener, EnumSet.of(OperationType.GENERIC, OperationType.TASK)).run()
        }
    }
}

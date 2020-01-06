/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.integtests.tooling.r33

import org.gradle.integtests.tooling.fixture.ProgressEvents
import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.TextUtil
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.tooling.ProjectConnection
import org.gradle.tooling.events.OperationType
import org.gradle.tooling.events.task.TaskSuccessResult

class CacheableTaskOutcomeCrossVersionSpec extends ToolingApiSpecification {
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
    def "cacheable task is reported as FROM_CACHE"() {
        when:
        def pushToCacheEvents = ProgressEvents.create()
        runCacheableBuild(pushToCacheEvents)
        then:
        !cacheableTaskResult(pushToCacheEvents).fromCache
        !cacheableTaskResult(pushToCacheEvents).upToDate

        when:
        file("build").deleteDir()
        and:
        def pullFromCacheResults = ProgressEvents.create()
        runCacheableBuild(pullFromCacheResults)
        then:
        cacheableTaskResult(pullFromCacheResults).fromCache
        cacheableTaskResult(pullFromCacheResults).upToDate
    }

    @ToolingApiVersion('<3.3 >=3.0')
    @TargetGradleVersion('>=3.5')
    def "cacheable task is reported as UP-TO-DATE on older TAPI versions"() {
        when:
        def pushToCacheEvents = ProgressEvents.create()
        runCacheableBuild(pushToCacheEvents)
        then:
        !cacheableTaskResult(pushToCacheEvents).upToDate

        when:
        file("build").deleteDir()
        and:
        def pullFromCacheResults = ProgressEvents.create()
        runCacheableBuild(pullFromCacheResults)
        then:
        cacheableTaskResult(pullFromCacheResults).upToDate
    }

    private TaskSuccessResult cacheableTaskResult(ProgressEvents events) {
        events.operations.size() == 1
        (TaskSuccessResult)events.operations[0].result
    }

    private void runCacheableBuild(listener) {
        withConnection {
            ProjectConnection connection ->
                connection.newBuild().withArguments("--build-cache").forTasks('cacheable').addProgressListener(listener, EnumSet.of(OperationType.TASK)).run()
        }
    }
}

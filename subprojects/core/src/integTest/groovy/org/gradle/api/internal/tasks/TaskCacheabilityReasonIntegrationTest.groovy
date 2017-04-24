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

package org.gradle.api.internal.tasks

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.LocalBuildCacheFixture

class TaskCacheabilityReasonIntegrationTest extends AbstractIntegrationSpec implements LocalBuildCacheFixture {
    def setup() {
        buildFile << """
            import org.gradle.api.internal.tasks.TaskOutputCachingDisabledReasonCategory
            
            gradle.addListener(new TaskExecutionAdapter() {
                void afterExecute(Task task, TaskState state) {
                    def taskOutputCaching = state.taskOutputCaching
                    assert taskOutputCaching.enabled == task.cachingEnabled
                    assert taskOutputCaching.disabledReason == task.disabledReason
                    assert taskOutputCaching.disabledReasonCategory == task.disabledReasonCategory
                }
            })

            class BaseTask extends DefaultTask {
                // these are not inputs, they're used for verification
                boolean cachingEnabled
                String disabledReason
                TaskOutputCachingDisabledReasonCategory disabledReasonCategory
            }
            
            class NotCacheable extends BaseTask {
                @Input
                String message = "Hello World"
                @OutputFile
                File outputFile = new File(temporaryDir, "output.txt")
                
                @TaskAction
                public void generate() {
                    outputFile.text = message
                }
            }
            
            @CacheableTask
            class Cacheable extends NotCacheable {
            }
        """
    }

    def "default cacheability is BUILD_CACHE_DISABLED"() {
        buildFile << """
            task cacheable(type: Cacheable) {
                cachingEnabled = false
                disabledReason = "Task output caching is disabled"
                disabledReasonCategory = TaskOutputCachingDisabledReasonCategory.BUILD_CACHE_DISABLED
            }
            task notcacheable(type: NotCacheable) {
                cachingEnabled = false
                disabledReason = "Task output caching is disabled"
                disabledReasonCategory = TaskOutputCachingDisabledReasonCategory.BUILD_CACHE_DISABLED
            }
        """
        expect:
        succeeds "cacheable", "notcacheable"
    }

    def "cacheability for non-cacheable task is NOT_ENABLED_FOR_TASK"() {
        buildFile << """
            task cacheable(type: NotCacheable) {
                cachingEnabled = false
                disabledReason = "Caching has not been enabled for the task"
                disabledReasonCategory = TaskOutputCachingDisabledReasonCategory.NOT_ENABLED_FOR_TASK
            }
        """
        expect:
        withBuildCache().succeeds "cacheable"
    }

    def "cacheability for a cacheable task is null"() {
        buildFile << """
            task cacheable(type: Cacheable) {
                cachingEnabled = true
                disabledReason = null
                disabledReasonCategory = null
            }
        """
        expect:
        withBuildCache().succeeds "cacheable"
    }

    def "cacheability for a task with no outputs is NO_OUTPUTS_DECLARED"() {
        buildFile << """
            @CacheableTask
            class NoOutputs extends BaseTask {
                @TaskAction
                void generate() {}
            }
            
            task cacheable(type: NoOutputs) {
                cachingEnabled = false
                disabledReason = "No outputs declared"
                disabledReasonCategory = TaskOutputCachingDisabledReasonCategory.NO_OUTPUTS_DECLARED
            }
        """
        expect:
        withBuildCache().succeeds "cacheable"
    }

    def "cacheability for a task with no actions is UNKNOWN"() {
        buildFile << """
            @CacheableTask
            class NoActions extends BaseTask {
            }
            
            task cacheable(type: NoActions) {
                cachingEnabled = false
                disabledReason = "Cacheability was not determined"
                disabledReasonCategory = TaskOutputCachingDisabledReasonCategory.UNKNOWN
            }
        """
        expect:
        withBuildCache().succeeds "cacheable"
    }

    def "cacheability for a task with plural outputs is PLURAL_OUTPUTS"() {
        buildFile << """
            @CacheableTask
            class PluralOutputs extends BaseTask {
                @OutputFiles
                Set<File> outputFiles = []
                
                @TaskAction
                void generate() {}
            }
            
            task cacheable(type: PluralOutputs) {
                cachingEnabled = false
                disabledReason = "Declares multiple output files for the single output property 'outputFiles' via `@OutputFiles`, `@OutputDirectories` or `TaskOutputs.files()`"
                disabledReasonCategory = TaskOutputCachingDisabledReasonCategory.PLURAL_OUTPUTS
            }
        """
        expect:
        withBuildCache().succeeds "cacheable"
    }

    def "cacheability for a task with overlapping outputs is OVERLAPPING_OUTPUTS"() {
        buildFile << """
            task cacheable(type: Cacheable) {
                cachingEnabled = true
                disabledReason = null
                disabledReasonCategory = null
            }
            
            task cacheableWithOverlap(type: Cacheable) {
                outputFile = cacheable.outputFile
                cachingEnabled = false
                disabledReason = "Outputs created by something else (e.g. '" + outputFile + "') were found in output property 'outputFile' before executing this task."
                disabledReasonCategory = TaskOutputCachingDisabledReasonCategory.OVERLAPPING_OUTPUTS
            }
        """
        expect:
        withBuildCache().succeeds "cacheable", "cacheableWithOverlap"
    }

    def "cacheability for a task with a cacheIf is CACHE_IF_SPEC_NOT_SATISFIED"() {
        buildFile << """
            task cacheable(type: Cacheable) {
                outputs.cacheIf("always false") { false }
                cachingEnabled = false
                disabledReason = "'always false' not satisfied"
                disabledReasonCategory = TaskOutputCachingDisabledReasonCategory.CACHE_IF_SPEC_NOT_SATISFIED
            }
        """
        expect:
        withBuildCache().succeeds "cacheable"
    }

    def "cacheability for a task with a doNotCacheIf is DO_NOT_CACHE_IF_SPEC_SATISFIED"() {
        buildFile << """
            task cacheable(type: Cacheable) {
                outputs.doNotCacheIf("always true") { true }
                cachingEnabled = false
                disabledReason = "'always true' satisfied"
                disabledReasonCategory = TaskOutputCachingDisabledReasonCategory.DO_NOT_CACHE_IF_SPEC_SATISFIED
            }
        """
        expect:
        withBuildCache().succeeds "cacheable"
    }

    def "cacheability for a task with onlyIf is UNKNOWN"() {
        buildFile << """
            task cacheable(type: Cacheable) {
                onlyIf { false }
                cachingEnabled = false
                disabledReason = "Cacheability was not determined"
                disabledReasonCategory = TaskOutputCachingDisabledReasonCategory.UNKNOWN
            }
        """
        expect:
        withBuildCache().succeeds "cacheable"
    }

    def "cacheability for a task with no sources is UNKNOWN"() {
        buildFile << """
            @CacheableTask
            class NoSources extends NotCacheable {
                @InputFiles
                @SkipWhenEmpty
                FileCollection empty = project.files()
            }
            
            task cacheable(type: NoSources) {
                cachingEnabled = false
                disabledReason = "Cacheability was not determined"
                disabledReasonCategory = TaskOutputCachingDisabledReasonCategory.UNKNOWN
            }
        """
        expect:
        withBuildCache().succeeds "cacheable"
    }

    def "cacheability for a cacheable task that's up-to-date"() {
        buildFile << """
            task cacheable(type: Cacheable) {
                cachingEnabled = true
                disabledReason = null
                disabledReasonCategory = null
            }
        """
        withBuildCache().succeeds "cacheable"
        expect:
        withBuildCache().succeeds "cacheable"
    }

    def "cacheability for a non-cacheable task that's up-to-date"() {
        buildFile << """
            task cacheable(type: NotCacheable) {
                cachingEnabled = false
                disabledReason = "Caching has not been enabled for the task"
                disabledReasonCategory = TaskOutputCachingDisabledReasonCategory.NOT_ENABLED_FOR_TASK
            }
        """
        withBuildCache().succeeds "cacheable"
        expect:
        withBuildCache().succeeds "cacheable"
    }

    def "cacheability for a failing cacheable task is null"() {
        buildFile << """
            task cacheable(type: Cacheable) {
                cachingEnabled = true
                disabledReason = null
                disabledReasonCategory = null
                doLast {
                    throw new GradleException("boom")
                }
            }
        """
        expect:
        withBuildCache().fails "cacheable"
        result.error.contains("boom")
    }

    def "cacheability for a failing non-cacheable task is NOT_ENABLED_FOR_TASK"() {
        buildFile << """
            task cacheable(type: NotCacheable) {
                cachingEnabled = false
                disabledReason = "Caching has not been enabled for the task"
                disabledReasonCategory = TaskOutputCachingDisabledReasonCategory.NOT_ENABLED_FOR_TASK
                doLast {
                    throw new GradleException("boom")
                }
            }
        """
        expect:
        withBuildCache().fails "cacheable"
        result.error.contains("boom")
    }
}

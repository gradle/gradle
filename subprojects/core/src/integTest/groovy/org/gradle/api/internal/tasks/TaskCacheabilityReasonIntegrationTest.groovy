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

import org.gradle.api.internal.tasks.execution.ExecuteTaskBuildOperationType
import org.gradle.api.tasks.OutputDirectories
import org.gradle.api.tasks.OutputFiles
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.BuildOperationsFixture
import org.gradle.integtests.fixtures.DirectoryBuildCacheFixture
import org.gradle.operations.execution.CachingDisabledReasonCategory
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.IntegTestPreconditions

import javax.annotation.Nullable

import static org.gradle.operations.execution.CachingDisabledReasonCategory.BUILD_CACHE_DISABLED
import static org.gradle.operations.execution.CachingDisabledReasonCategory.CACHE_IF_SPEC_NOT_SATISFIED
import static org.gradle.operations.execution.CachingDisabledReasonCategory.DO_NOT_CACHE_IF_SPEC_SATISFIED
import static org.gradle.operations.execution.CachingDisabledReasonCategory.NON_CACHEABLE_TREE_OUTPUT
import static org.gradle.operations.execution.CachingDisabledReasonCategory.NOT_ENABLED_FOR_TASK
import static org.gradle.operations.execution.CachingDisabledReasonCategory.NO_OUTPUTS_DECLARED
import static org.gradle.operations.execution.CachingDisabledReasonCategory.OVERLAPPING_OUTPUTS
import static org.gradle.operations.execution.CachingDisabledReasonCategory.UNKNOWN
import static org.gradle.operations.execution.CachingDisabledReasonCategory.VALIDATION_FAILURE

class TaskCacheabilityReasonIntegrationTest extends AbstractIntegrationSpec implements DirectoryBuildCacheFixture {
    def operations = new BuildOperationsFixture(executer, testDirectoryProvider)

    def setup() {
        buildFile """
            class UnspecifiedCacheabilityTask extends DefaultTask {
                @Input
                String message = "Hello World"
                @OutputFile
                File outputFile = new File(temporaryDir, "output.txt")

                @TaskAction
                void generate() {
                    outputFile.text = message
                }
            }

            @DisableCachingByDefault
            class NotCacheableByDefault extends UnspecifiedCacheabilityTask {}

            @DisableCachingByDefault(because = 'do-not-cache-by-default reason')
            class NotCacheableByDefaultWithReason extends UnspecifiedCacheabilityTask {}

            @UntrackedTask(because = 'untracked-task reason')
            class UntrackedTrackWithReason extends UnspecifiedCacheabilityTask {}

            @UntrackedTask(because = 'untracked-task-with-cacheable reason')
            @CacheableTask
            class UntrackedTrackWithReasonWithCacheable extends UnspecifiedCacheabilityTask {}

            @CacheableTask
            class Cacheable extends UnspecifiedCacheabilityTask {}

            class NoOutputs extends DefaultTask {
                @TaskAction
                void generate() {}
            }

        """
    }

    def "default cacheability is BUILD_CACHE_DISABLED"() {
        buildFile << """
            task cacheable(type: Cacheable) {}
            task notCacheableByDefault(type: NotCacheableByDefault) {}
            task unspecified(type: UnspecifiedCacheabilityTask) {}
            task noOutputs(type: NoOutputs) {}
        """
        when:
        run "cacheable"
        then:
        assertCachingDisabledFor BUILD_CACHE_DISABLED, "Build cache is disabled"

        when:
        run "notCacheableByDefault"
        then:
        assertCachingDisabledFor BUILD_CACHE_DISABLED, "Build cache is disabled"

        when:
        run "unspecified"
        then:
        assertCachingDisabledFor BUILD_CACHE_DISABLED, "Build cache is disabled"

        when:
        run "noOutputs"
        then:
        assertCachingDisabledFor BUILD_CACHE_DISABLED, "Build cache is disabled"
    }

    def "cacheability for task with unspecified cacheability is NOT_ENABLED_FOR_TASK"() {
        buildFile << """
            task unspecified(type: UnspecifiedCacheabilityTask) {}
        """
        when:
        withBuildCache().run "unspecified"
        then:
        assertCachingDisabledFor NOT_ENABLED_FOR_TASK, "Caching has not been enabled for the task"
    }

    def "cacheability for a cacheable task is null"() {
        buildFile << """
            task cacheable(type: Cacheable) {}
        """
        when:
        withBuildCache().run "cacheable"
        then:
        assertCachingDisabledFor null, null
    }

    def "cacheability for a non-cacheable task is NOT_ENABLED_FOR_TASK"() {
        buildFile << """
            task notCacheableByDefault(type: NotCacheableByDefault) {}
        """
        when:
        withBuildCache().run "notCacheableByDefault"
        then:
        assertCachingDisabledFor NOT_ENABLED_FOR_TASK, "Caching has been disabled for the task"
    }

    def "cacheability for a non-cacheable task with reason is NOT_ENABLED_FOR_TASK"() {
        buildFile << """
            task notCacheableByDefault(type: NotCacheableByDefaultWithReason) {}
        """
        when:
        withBuildCache().run "notCacheableByDefault"
        then:
        assertCachingDisabledFor NOT_ENABLED_FOR_TASK, "do-not-cache-by-default reason"
    }

    def "cacheability for a cacheable task with no outputs is NO_OUTPUTS_DECLARED"() {
        buildFile """
            @CacheableTask
            class CacheableNoOutputs extends DefaultTask {
                @TaskAction
                void generate() {}
            }

            task noOutputs(type: CacheableNoOutputs) {}
        """
        when:
        withBuildCache().run "noOutputs"
        then:
        assertCachingDisabledFor NO_OUTPUTS_DECLARED, "No outputs declared"
    }


    def "cacheability for a untracked task via API is NOT_ENABLED_FOR_TASK with message"() {
        buildFile """
            task untrackedTrackWithReason(type: UnspecifiedCacheabilityTask) {
                doNotTrackState("Untracked for testing from API")
            }
        """
        when:
        withBuildCache().run "untrackedTrackWithReason"
        then:
        assertCachingDisabledFor NOT_ENABLED_FOR_TASK, "Task is untracked because: Untracked for testing from API"
    }


    def "cacheability for a untracked task is NOT_ENABLED_FOR_TASK with message"() {
        buildFile """
            task untrackedTrackWithReason(type: UntrackedTrackWithReason) {}
        """
        when:
        withBuildCache().run "untrackedTrackWithReason"
        then:
        assertCachingDisabledFor NOT_ENABLED_FOR_TASK, "Task is untracked because: untracked-task reason"
    }

    def "cacheability for a untracked task is NOT_ENABLED_FOR_TASK with message when marked cacheable"() {
        buildFile """
            task untrackedTrackWithReasonWithCacheable(type: UntrackedTrackWithReasonWithCacheable) {}
        """
        when:
        withBuildCache().run "untrackedTrackWithReasonWithCacheable"
        then:
        assertCachingDisabledFor DO_NOT_CACHE_IF_SPEC_SATISFIED, "Task is untracked because: untracked-task-with-cacheable reason"
    }


    def "cacheability for a task with no outputs is NOT_ENABLED_FOR_TASK"() {
        buildFile """
            task noOutputs(type: NoOutputs) {}
        """
        when:
        withBuildCache().run "noOutputs"
        then:
        assertCachingDisabledFor NOT_ENABLED_FOR_TASK, "Caching has not been enabled for the task"
    }

    def "cacheability for a task with no actions is UNKNOWN (cacheable: #cacheable)"() {
        buildFile << """
            class NoActions extends DefaultTask {}

            task noActions {
                outputs.cacheIf { $cacheable }
            }
        """
        when:
        withBuildCache().run "noActions"
        then:
        assertCachingDisabledFor UNKNOWN, "Cacheability was not determined"

        where:
        cacheable << [true, false]
    }

    def "cacheability for a task with @#annotation file tree outputs is NON_CACHEABLE_TREE_OUTPUT"() {
        buildFile << """
            @CacheableTask
            abstract class PluralOutputs extends DefaultTask {
                @$annotation
                def outputFiles = [project.fileTree('build/some-dir')]

                @Inject
                abstract ProjectLayout getLayout()

                @TaskAction
                void generate() {
                    layout.buildDirectory.dir("some-dir").get().asFile.mkdirs()
                    layout.buildDirectory.file("some-dir/output.txt").get().asFile.text = "output"
                }
            }

            task pluralOutputs(type: PluralOutputs)
        """
        when:
        withBuildCache().run "pluralOutputs"
        then:
        assertCachingDisabledFor NON_CACHEABLE_TREE_OUTPUT, "Output property 'outputFiles\$1' contains a file tree"

        where:
        annotation << [OutputFiles.simpleName, OutputDirectories.simpleName]
    }

    def "cacheability for a task with overlapping outputs is OVERLAPPING_OUTPUTS"() {
        buildFile """
            task cacheable(type: Cacheable)
            task cacheableWithOverlap(type: Cacheable) {
                outputFile = cacheable.outputFile
            }
        """
        when:
        withBuildCache().run "cacheable", "cacheableWithOverlap"
        then:
        assertCachingDisabledFor null, null, ":cacheable"
        assertCachingDisabledFor OVERLAPPING_OUTPUTS, "Gradle does not know how file 'build${File.separator}tmp${File.separator}cacheable${File.separator}output.txt' was created (output property 'outputFile'). Task output caching requires exclusive access to output paths to guarantee correctness (i.e. multiple tasks are not allowed to produce output in the same location).", ":cacheableWithOverlap"
    }

    def "cacheability for a task with a cacheIf is CACHE_IF_SPEC_NOT_SATISFIED"() {
        buildFile """
            task cacheable(type: Cacheable) {
                outputs.cacheIf("always false") { false }
            }
        """
        when:
        withBuildCache().run "cacheable"
        then:
        assertCachingDisabledFor CACHE_IF_SPEC_NOT_SATISFIED, "'always false' not satisfied"
    }

    def "cacheability for a task with a doNotCacheIf is DO_NOT_CACHE_IF_SPEC_SATISFIED"() {
        buildFile """
            task cacheable(type: Cacheable) {
                outputs.doNotCacheIf("always true") { true }
            }
        """
        when:
        withBuildCache().run "cacheable"
        then:
        assertCachingDisabledFor DO_NOT_CACHE_IF_SPEC_SATISFIED, "'always true' satisfied"
    }

    def "cacheability for a task with onlyIf is UNKNOWN"() {
        buildFile """
            task cacheable(type: Cacheable) {
                onlyIf { false }
            }
        """
        when:
        withBuildCache().run "cacheable"
        then:
        assertCachingDisabledFor UNKNOWN, "Cacheability was not determined"
    }

    def "cacheability for a task with no sources is UNKNOWN"() {
        buildFile """
            @CacheableTask
            class NoSources extends UnspecifiedCacheabilityTask {
                @InputFiles
                @SkipWhenEmpty
                FileCollection empty = project.layout.files()
            }

            task noSources(type: NoSources)
        """
        when:
        withBuildCache().run "noSources"
        then:
        assertCachingDisabledFor UNKNOWN, "Cacheability was not determined"
    }

    def "cacheability for a cacheable task that's up-to-date"() {
        buildFile """
            task cacheable(type: Cacheable)
        """
        when:
        withBuildCache().run "cacheable"
        then:
        executedAndNotSkipped(":cacheable")
        assertCachingDisabledFor null, null

        when:
        withBuildCache().run "cacheable"
        then:
        skipped(":cacheable")
        assertCachingDisabledFor null, null
    }

    def "cacheability for a non-cacheable task that's up-to-date"() {
        buildFile """
            task unspecified(type: UnspecifiedCacheabilityTask)
        """
        when:
        withBuildCache().run "unspecified"
        then:
        executedAndNotSkipped(":unspecified")
        assertCachingDisabledFor NOT_ENABLED_FOR_TASK, "Caching has not been enabled for the task"

        when:
        withBuildCache().run "unspecified"
        then:
        skipped(":unspecified")
        assertCachingDisabledFor NOT_ENABLED_FOR_TASK, "Caching has not been enabled for the task"
    }

    def "cacheability for a failing cacheable task is null"() {
        buildFile """
            task cacheable(type: Cacheable) {
                doLast {
                    throw new GradleException("boom")
                }
            }
        """
        when:
        withBuildCache().fails "cacheable"
        failure.assertHasCause("boom")
        then:
        assertCachingDisabledFor null, null
    }

    def "cacheability for a failing task with unspecified cacheability is NOT_ENABLED_FOR_TASK"() {
        buildFile """
            task failing(type: UnspecifiedCacheabilityTask) {
                doLast {
                    throw new GradleException("boom")
                }
            }
        """
        when:
        withBuildCache().fails "failing"
        failure.assertHasCause("boom")
        then:
        assertCachingDisabledFor NOT_ENABLED_FOR_TASK, "Caching has not been enabled for the task"
    }

    // This test only works in embedded mode because of the use of validation test fixtures
    @Requires(IntegTestPreconditions.IsEmbeddedExecutor)
    def "cacheability for task with disabled optimizations is VALIDATION_FAILURE"() {
        when:
        executer.noDeprecationChecks()
        buildFile """
            import org.gradle.integtests.fixtures.validation.ValidationProblem
            import org.gradle.internal.reflect.validation.Severity

            @CacheableTask
            abstract class InvalidTask extends DefaultTask {
                @ValidationProblem(value = Severity.WARNING)
                abstract Property<String> getInput()

                @OutputFile
                abstract RegularFileProperty getOutput()

                @TaskAction
                void doSomething() {
                    output.get().asFile.text = input.get()
                }
            }

            task invalid(type: InvalidTask) {
                input = "invalid"
                output = file("out.txt")
            }
        """

        then:
        withBuildCache().succeeds("invalid")
        assertCachingDisabledFor VALIDATION_FAILURE, "Caching has been disabled to ensure correctness. Please consult deprecation warnings for more details.", ":invalid"
    }

    def "cacheability for a cacheable task can be disabled via #condition"() {
        buildFile << """
            task cacheable(type: Cacheable) {
                outputs.$condition
            }
        """
        when:
        withBuildCache().run "cacheable"
        then:
        assertCachingDisabledFor expectedReason, expectedMessage

        where:
        condition                                         | expectedReason                 | expectedMessage
        "cacheIf { false }"                               | CACHE_IF_SPEC_NOT_SATISFIED    | "'Task outputs cacheable' not satisfied"
        "cacheIf('cache-if reason') { false }"            | CACHE_IF_SPEC_NOT_SATISFIED    | "'cache-if reason' not satisfied"
        "doNotCacheIf('do-not-cache-if reason') { true }" | DO_NOT_CACHE_IF_SPEC_SATISFIED | "'do-not-cache-if reason' satisfied"
    }

    def "cacheability for a #taskType task can be enabled via #condition"() {
        buildFile << """
            task custom(type: ${taskType}) {
                outputs.$condition
            }
        """
        when:
        withBuildCache().run "custom"
        then:
        assertCachingDisabledFor null, null

        where:
        [taskType, condition] << [["UnspecifiedCacheabilityTask", "NotCacheableByDefault", "NotCacheableByDefaultWithReason"], ["cacheIf { true }", "cacheIf('cache-if reason') { true }"]].combinations()
    }

    private void assertCachingDisabledFor(@Nullable CachingDisabledReasonCategory category, @Nullable String message, @Nullable String taskPath = null) {
        operations.only(ExecuteTaskBuildOperationType, {
            if (taskPath && taskPath != it.details.taskPath) {
                return false
            }
            assert it.result.cachingDisabledReasonCategory == category?.name()
            assert it.result.cachingDisabledReasonMessage == message
            return true
        })
    }
}

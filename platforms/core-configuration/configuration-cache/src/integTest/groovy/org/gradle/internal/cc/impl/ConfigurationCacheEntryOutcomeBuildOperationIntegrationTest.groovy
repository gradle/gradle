/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.internal.cc.impl

import org.gradle.api.internal.ConfigurationCacheDegradationController
import org.gradle.integtests.fixtures.BuildOperationsFixture
import org.gradle.operations.configuration.ConfigurationCacheEntryOutcomeBuildOperationType

import javax.inject.Inject

class ConfigurationCacheEntryOutcomeBuildOperationIntegrationTest extends AbstractConfigurationCacheIntegrationTest {

    def operations = new BuildOperationsFixture(executer, temporaryFolder)

    def "emits no entry outcome operation when configuration cache is not used"() {
        when:
        run 'help'

        then:
        operations.none(ConfigurationCacheEntryOutcomeBuildOperationType)
    }

    def "emits STORED on a cache miss and REUSED on a cache hit"() {
        when:
        configurationCacheRun 'help'

        then:
        postBuildOutputContains("Configuration cache entry stored.")
        with(operations.only(ConfigurationCacheEntryOutcomeBuildOperationType).result) {
            outcome == "STORED"
            problemCount == 0
        }

        when:
        configurationCacheRun 'help'

        then:
        postBuildOutputContains("Configuration cache entry reused.")
        with(operations.only(ConfigurationCacheEntryOutcomeBuildOperationType).result) {
            outcome == "REUSED"
            problemCount == 0
        }
    }

    def "emits STORED with problem count when problems are allowed as warnings"() {
        given:
        buildFile """
            gradle.buildFinished { }
            task broken
        """

        when:
        configurationCacheRunLenient 'broken'

        then:
        postBuildOutputContains("Configuration cache entry stored with 1 problem.")
        with(operations.only(ConfigurationCacheEntryOutcomeBuildOperationType).result) {
            outcome == "STORED"
            problemCount == 1
        }
    }

    def "emits STORE_FAILED when problems fail the build"() {
        given:
        buildFile """
            gradle.buildFinished { }
            task broken
        """

        when:
        configurationCacheFails 'broken'

        then:
        outputContains("Configuration cache entry discarded with 1 problem.")
        with(operations.only(ConfigurationCacheEntryOutcomeBuildOperationType).result) {
            outcome == "STORE_FAILED"
            problemCount == 1
        }
    }

    def "emits STORE_SKIPPED when an incompatible task is scheduled"() {
        given:
        buildFile """
            task broken {
                notCompatibleWithConfigurationCache("declarative reason")
                doLast { }
            }
        """

        when:
        configurationCacheRun 'broken'

        then:
        postBuildOutputContains("Configuration cache entry discarded because incompatible task was found: 'task `:broken` of type `org.gradle.api.DefaultTask`'.")
        operations.only(ConfigurationCacheEntryOutcomeBuildOperationType).result.outcome == "STORE_SKIPPED"
    }

    def "emits STORE_FAILED on a serialization error"() {
        given:
        buildFile """
            class BrokenSerializable implements java.io.Serializable {
                private Object writeReplace() {
                    throw new RuntimeException("BOOM")
                }
            }

            class BrokenTaskType extends DefaultTask {
                final prop = new BrokenSerializable()
            }

            task broken(type: BrokenTaskType)
        """

        when:
        configurationCacheFails 'broken'

        then:
        outputContains("Configuration cache entry discarded due to serialization error.")
        operations.only(ConfigurationCacheEntryOutcomeBuildOperationType).result.outcome == "STORE_FAILED"
    }

    def "emits STORE_FAILED when there are too many problems"() {
        given:
        buildFile """
            gradle.buildFinished { }
            task broken
        """

        when:
        configurationCacheFails WARN_PROBLEMS_CLI_OPT, "$MAX_PROBLEMS_SYS_PROP=0", 'broken'

        then:
        outputContains("Configuration cache entry discarded with too many problems (1 problem).")
        with(operations.only(ConfigurationCacheEntryOutcomeBuildOperationType).result) {
            outcome == "STORE_FAILED"
            problemCount == 1
        }
    }

    def "emits STORE_SKIPPED on a cache miss in read-only mode"() {
        when:
        configurationCacheRun 'help', ENABLE_READ_ONLY_CACHE

        then:
        postBuildOutputContains("Configuration cache disabled as cache is in read-only mode.")
        with(operations.only(ConfigurationCacheEntryOutcomeBuildOperationType).result) {
            outcome == "STORE_SKIPPED"
            problemCount == 0
        }
    }

    def "emits STORE_SKIPPED when configuration caching degrades gracefully"() {
        given:
        buildFile """
            abstract class DegradingTask extends DefaultTask {
                @${Inject.name}
                abstract ${ConfigurationCacheDegradationController.name} getDegradationController()
            }

            tasks.register("degrading", DegradingTask) { task ->
                getDegradationController().requireConfigurationCacheDegradation(task, provider { "Degradation reason" })
                doLast { }
            }
        """

        when:
        configurationCacheRun 'degrading'

        then:
        postBuildOutputContains("Configuration cache disabled because incompatible task was found.")
        operations.only(ConfigurationCacheEntryOutcomeBuildOperationType).result.outcome == "STORE_SKIPPED"
    }
}

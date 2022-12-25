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

package org.gradle.api.tasks

import org.gradle.integtests.fixtures.DirectoryBuildCacheFixture

class CachedPathSensitivityIntegrationTest extends AbstractPathSensitivityIntegrationSpec implements DirectoryBuildCacheFixture {
    def setup() {
        buildFile << """
            apply plugin: 'base'
        """
    }

    @Override
    void execute(String... tasks) {
        withBuildCache().run tasks
    }

    @Override
    void cleanWorkspace() {
        run "clean"
    }

    @Override
    String getStatusForReusedOutput() {
        return "FROM-CACHE"
    }

    def "single #pathSensitivity input file loaded from cache can be used as input"() {
        file("src/data/input.txt").text = "data"

        buildFile << """
            task producer {
                outputs.cacheIf { true }
                outputs.file("build/outputs/producer.txt")
                doLast {
                    file("build/outputs/producer.txt").text = "alma"
                }
            }

            task consumer {
                dependsOn producer
                outputs.cacheIf { true }
                inputs.file("build/outputs/producer.txt")
                    .withPropertyName("producer")
                    .withPathSensitivity(PathSensitivity.$pathSensitivity)
                outputs.file("build/outputs/consumer.txt")
                    .withPropertyName("consumer")
                doLast {
                    file("build/outputs/consumer.txt").text = file("build/outputs/producer.txt").text
                }
            }
        """

        withBuildCache().run "consumer"
        run "clean"

        expect:
        withBuildCache().run "consumer"
        skipped ":producer", ":consumer"

        where:
        pathSensitivity << PathSensitivity.values()
    }
}

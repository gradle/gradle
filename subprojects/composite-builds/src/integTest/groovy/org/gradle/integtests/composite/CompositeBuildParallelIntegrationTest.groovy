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

package org.gradle.integtests.composite

class CompositeBuildParallelIntegrationTest extends AbstractCompositeBuildIntegrationTest {
    def "works when number of included builds exceeds max-workers"() {
        def maxWorkers = 1
        def totalIncludedBuilds = 2*maxWorkers
        buildA.buildFile << """
            task delegate {
                dependsOn gradle.includedBuilds*.task(":someTask")
            }
        """
        (1..totalIncludedBuilds).each {
            includedBuilds << singleProjectBuild("included$it") {
                buildFile << """
                    task someTask {
                        doLast {
                            Thread.sleep(500)
                        }
                    }
                """
            }
        }
        expect:
        execute(buildA, "delegate", "--parallel", "--max-workers=$maxWorkers")
    }
}

/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.plugins.ide.idea

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache
import org.gradle.test.fixtures.server.http.BlockingHttpServer
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.IntegTestPreconditions
import org.junit.Rule

@Requires(IntegTestPreconditions.NotParallelExecutor)
class CompositeBuildParallelIntegrationTest extends AbstractIntegrationSpec {
    @Rule BlockingHttpServer server = new BlockingHttpServer()

    @ToBeFixedForConfigurationCache
    def "builds IDE metadata artifacts in parallel"() {
        given:
        server.start()
        buildTestFixture.withBuildInSubDir()
        def buildA = singleProjectBuild("buildA") {
            buildFile << """
                apply plugin: 'java'
                apply plugin: 'idea'
            """
        }

        def included = ['buildB', 'buildC', 'buildD']
        included.each { buildName ->
            def build = singleProjectBuild(buildName) {
                buildFile << """
                    apply plugin: 'java'
                    apply plugin: 'idea'

                    ideaModule.doLast {
                        ${server.callFromBuild(buildName)}
                    }
                """
            }
            buildA.buildFile << """
                dependencies {
                    implementation "org.test:${buildName}:1.0"
                }
            """
            buildA.settingsFile << """
                includeBuild "${build.toURI()}"
            """
        }

        server.expectConcurrent(included)

        expect:
        executer.withArguments("--max-workers=4")
        executer.inDirectory(buildA)
        succeeds(":idea")
    }
}

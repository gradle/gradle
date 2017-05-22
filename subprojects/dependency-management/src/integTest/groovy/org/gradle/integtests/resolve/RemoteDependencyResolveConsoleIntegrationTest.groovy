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

package org.gradle.integtests.resolve

import org.gradle.integtests.fixtures.AbstractDependencyResolutionTest
import org.gradle.test.fixtures.ConcurrentTestUtil
import org.gradle.test.fixtures.server.http.BlockingHttpServer
import org.junit.Rule

import static org.gradle.integtests.fixtures.AbstractConsoleFunctionalSpec.workInProgressLine

class RemoteDependencyResolveConsoleIntegrationTest extends AbstractDependencyResolutionTest {
    @Rule
    BlockingHttpServer server = new BlockingHttpServer()

    def setup() {
        server.start()
    }

    def "shows work-in-progress during graph and file resolution"() {
        def m1 = mavenRepo.module("test", "one", "1.2").publish()
        def m2 = mavenRepo.module("test", "two", "1.2").publish()
        buildFile << """
            repositories { 
                maven { url '${server.uri}' }
            }
            configurations { compile }
            dependencies {
                compile "test:one:1.2"
                compile "test:two:1.2"
            }
            task resolve {
                doLast {
                    configurations.compile.each { println it.name }
                }
            }
"""

        given:
        def metaData = server.blockOnConcurrentExecutionAnyOfToResources(2, [
                server.file(m1.pom.path, m1.pom.file),
                server.file(m2.pom.path, m2.pom.file)
        ])
        def jars = server.blockOnConcurrentExecutionAnyOfToResources(2, [
                server.file(m1.artifact.path, m1.artifact.file),
                server.file(m2.artifact.path, m2.artifact.file)
        ])

        when:
        def build = executer.withTasks("resolve").withArguments("--max-workers=2", "--console=rich").start()
        metaData.waitForAllPendingCalls()

        then:
        ConcurrentTestUtil.poll {
            assert build.standardOutput.contains(workInProgressLine("> :resolve > Resolve dependencies :compile"))
        }

        when:
        metaData.releaseAll()
        jars.waitForAllPendingCalls()

        then:
        ConcurrentTestUtil.poll {
            assert build.standardOutput.contains(workInProgressLine("> :resolve > Resolve files of :compile"))
        }

        jars.releaseAll()
        build.waitForFinish()
    }
}

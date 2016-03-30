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

package org.gradle.integtests.tooling.r213
import org.gradle.integtests.tooling.fixture.CompositeToolingApiSpecification
import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.test.fixtures.file.TestFile
import org.gradle.tooling.connection.GradleConnectionBuilder
import org.gradle.tooling.model.GradleProject
/**
 * Tooling models for an integrated composite are produced by a single daemon instance.
 * We only do this for target gradle versions that support integrated composite build.
 */
@ToolingApiVersion(">=2.14")
@TargetGradleVersion(">=2.14")
class IntegratedCompositeBuildCrossVersionSpec extends CompositeToolingApiSpecification {
    TestFile rootSingle
    TestFile rootMulti
    GradleConnectionBuilder connectionBuilder

    def setup() {
        rootSingle = singleProjectBuild("A") {
                    buildFile << """
task helloA {
  doLast {
    file('hello.txt').text = "Hello world from \${project.path}"
  }
}
"""
        }
        rootMulti = multiProjectBuild("B", ['x', 'y']) {
                    buildFile << """
allprojects {
  task helloB {
    doLast {
      file('hello.txt').text = "Hello world from \${project.path}"
    }
  }
}
"""
        }

        def builder = createCompositeBuilder()
        addCompositeParticipant(builder, rootSingle)
//        addCompositeParticipant(builder, rootMulti)
        builder.integratedComposite(true)
        connectionBuilder = builder
    }

    def "can retrieve models from integrated composite"() {
        when:
        def models = withCompositeConnection(connectionBuilder) { connection ->
            unwrap(connection.getModels(GradleProject))
        }

        then:
        models.size() == 1 // 4
        models*.name.containsAll(['A'])//, 'B', 'x', 'y'])
        models*.path.containsAll([':'])//, ':x', ':y'])
    }

    def "can execute task in integrated composite"() {
        when:
        withCompositeConnection(connectionBuilder) { connection ->
            def buildLauncher = connection.newBuild()
            buildLauncher.forTasks(rootSingle, "helloA")
            buildLauncher.run()
        }

        then:
        rootSingle.file('hello.txt').assertExists().text == 'Hello world from :'
        rootMulti.file('hello.txt').assertDoesNotExist()
/*

        when:
        withCompositeConnection(connectionBuilder) { connection ->
            def buildLauncher = connection.newBuild()
            buildLauncher.forTasks(rootMulti, "helloB")
            buildLauncher.run()
        }

        then:
        rootMulti.file('hello.txt').assertExists().text == 'Hello world from :'
        rootMulti.file('x/hello.txt').assertExists().text == 'Hello world from :x'
        rootMulti.file('y/hello.txt').assertExists().text == 'Hello world from :y'
*/
    }
}

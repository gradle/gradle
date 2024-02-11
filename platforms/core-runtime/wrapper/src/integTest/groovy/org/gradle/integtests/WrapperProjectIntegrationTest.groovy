/*
 * Copyright 2007 the original author or authors.
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

package org.gradle.integtests


import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.IntegTestPreconditions
import org.hamcrest.CoreMatchers
import spock.lang.Issue

import static org.hamcrest.CoreMatchers.containsString
import static org.hamcrest.MatcherAssert.assertThat

@Requires(value = IntegTestPreconditions.NotEmbeddedExecutor, reason = NOT_EMBEDDED_REASON)
class WrapperProjectIntegrationTest extends AbstractWrapperIntegrationSpec {
    def setup() {
        file("build.gradle") << """
    task hello {
        doLast {
            println 'hello'
        }
    }

    task echoProperty {
        def food = providers.gradleProperty('fooD')
        doLast {
            println "fooD=" + food.get()
        }
    }
"""
    }

    void "has non-zero exit code on build failure"() {
        given:
        prepareWrapper()

        when:
        def failure = wrapperExecuter.withTasks('unknown').runWithFailure()

        then:
        failure.assertThatDescription(CoreMatchers.startsWith("Task 'unknown' not found in root project"))
    }

    @Issue("https://github.com/gradle/gradle/issues/16055")
    def "can run in project with a \$ in the path"() {
        given:
        def projectDir = file('foo$bar-baz')
        projectDir.mkdirs()
        prepareWrapper(distribution.binDistribution.toURI()) {
            it.inDirectory(projectDir)
        }
        projectDir.file("build.gradle") << """
            task assertProjectDirHasMeta {
                def dirName = provider { projectDir.name }
                doLast {
                    assert  dirName.get() == 'foo\$bar-baz'
                }
            }
        """
        when:
        def result = wrapperExecuter.inDirectory(projectDir).withTasks('assertProjectDirHasMeta').run()
        then:
        result.assertTaskExecuted(":assertProjectDirHasMeta")
    }

    @Issue("https://issues.gradle.org/browse/GRADLE-1871")
    void "can specify project properties containing D"() {
        given:
        prepareWrapper()

        when:
        def result = wrapperExecuter.withArguments("-PfooD=bar").withTasks('echoProperty').run()

        then:
        assertThat(result.output, containsString("fooD=bar"))
    }
}

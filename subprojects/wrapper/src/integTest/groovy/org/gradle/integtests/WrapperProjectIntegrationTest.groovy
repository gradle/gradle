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

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.executer.GradleExecuter
import org.hamcrest.Matchers
import spock.lang.Issue

import static org.hamcrest.Matchers.containsString
import static org.junit.Assert.assertThat

/**
 * @author Hans Dockter
 */
class WrapperProjectIntegrationTest extends AbstractIntegrationSpec {
    void setup() {
        assert distribution.binDistribution.exists(): "bin distribution must exist to run this test, you need to run the :distributions:binZip task"
        executer.beforeExecute(new WrapperSetup())
    }

    GradleExecuter getWrapperExecuter() {
        executer.usingExecutable('gradlew').inDirectory(testDirectory)
    }

    private prepareWrapper() {
        file("build.gradle") << """
    import org.gradle.api.tasks.wrapper.Wrapper
    task wrapper(type: Wrapper) {
        distributionUrl = '${distribution.binDistribution.toURI()}'
    }

    task hello << {
        println 'hello'
    }

    task echoProperty << {
        println "fooD=" + project.properties["fooD"]
    }
"""

        executer.withTasks('wrapper').run()
    }

    public void "has non-zero exit code on build failure"() {
        given:
        prepareWrapper()

        when:
        def failure = wrapperExecuter.withTasks('unknown').runWithFailure()

        then:
        failure.assertThatDescription(Matchers.startsWith("Task 'unknown' not found in root project"))
    }

    @Issue("http://issues.gradle.org/browse/GRADLE-1871")
    public void "can specify project properties containing D"() {
        given:
        prepareWrapper()

        when:
        def result = wrapperExecuter.withArguments("-PfooD=bar").withTasks('echoProperty').run()

        then:
        assertThat(result.output, containsString("fooD=bar"))
    }
}

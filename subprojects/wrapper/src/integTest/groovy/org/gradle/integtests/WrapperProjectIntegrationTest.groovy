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
import org.gradle.integtests.fixtures.ExecutionFailure
import org.gradle.integtests.fixtures.ExecutionResult
import org.gradle.integtests.fixtures.GradleDistributionExecuter
import org.gradle.test.fixtures.server.http.HttpServer
import org.gradle.test.fixtures.server.http.TestProxyServer
import org.gradle.util.GradleVersion
import org.gradle.util.SetSystemProperties
import org.gradle.util.TextUtil
import org.junit.Rule
import spock.lang.Issue

import static org.gradle.test.matchers.UserAgentMatcher.matchesNameAndVersion
import static org.hamcrest.Matchers.containsString
import static org.junit.Assert.assertThat

/**
 * @author Hans Dockter
 */
class WrapperProjectIntegrationTest extends AbstractIntegrationSpec {
    @Rule HttpServer server = new HttpServer()
    @Rule TestProxyServer proxyServer = new TestProxyServer(server)
    @Rule SetSystemProperties systemProperties = new SetSystemProperties()

    void setup() {
        server.start()
        server.expectUserAgent(matchesNameAndVersion("gradlew", GradleVersion.current().getVersion()))
    }

    GradleDistributionExecuter getWrapperExecuter() {
        executer.usingExecutable('gradlew').inDirectory(testDir)
    }

    private prepareWrapper(String baseUrl) {
        assert distribution.binDistribution.exists(): "bin distribution must exist to run this test, you need to run the :binZip task"

        file("build.gradle") << """
    import org.gradle.api.tasks.wrapper.Wrapper
    task wrapper(type: Wrapper) {
        archiveBase = Wrapper.PathBase.PROJECT
        archivePath = 'dist'
        distributionUrl = '${baseUrl}/gradlew/dist'
        distributionBase = Wrapper.PathBase.PROJECT
        distributionPath = 'dist'
    }

    task hello << {
        println 'hello'
    }

    task echoProperty << {
        println "fooD=" + project.properties["fooD"]
    }
"""

        executer.withTasks('wrapper').run()
        server.allowGetOrHead("/gradlew/dist", distribution.binDistribution)
    }

    public void "has non-zero exit code on build failure"() {
        given:
        prepareWrapper("http://localhost:${server.port}")

        expect:
        server.allowGetOrHead("/gradlew/dist", distribution.binDistribution)

        when:
        ExecutionFailure failure = wrapperExecuter.withTasks('unknown').runWithFailure()

        then:
        failure.assertHasDescription("Task 'unknown' not found in root project")
    }

    public void "runs sample target using wrapper"() {
        given:
        prepareWrapper("http://localhost:${server.port}")

        when:
        ExecutionResult result = wrapperExecuter.withTasks('hello').run()

        then:
        assertThat(result.output, containsString('hello'))
    }

    public void "downloads wrapper via proxy"() {
        given:
        proxyServer.start()
        prepareWrapper("http://not.a.real.domain")
        file("gradle.properties") << """
    systemProp.http.proxyHost=localhost
    systemProp.http.proxyPort=${proxyServer.port}
"""

        when:
        ExecutionResult result = wrapperExecuter.withTasks('hello').run()

        then:
        assertThat(result.output, containsString('hello'))

        and:
        proxyServer.requestCount == 1
    }

    public void "downloads wrapper via authenticated proxy"() {
        given:
        proxyServer.start()
        proxyServer.requireAuthentication('my_user', 'my_password')

        and:
        prepareWrapper("http://not.a.real.domain")
        file("gradle.properties") << """
    systemProp.http.proxyHost=localhost
    systemProp.http.proxyPort=${proxyServer.port}
    systemProp.http.proxyUser=my_user
    systemProp.http.proxyPassword=my_password
"""
        when:
        ExecutionResult result = wrapperExecuter.withTasks('hello').run()

        then:
        assertThat(result.output, containsString('hello'))

        and:
        proxyServer.requestCount == 1
    }

    @Issue("http://issues.gradle.org/browse/GRADLE-1871")
    public void "can specify project properties containing D"() {
        given:
        prepareWrapper("http://localhost:${server.port}")

        when:
        ExecutionResult result = wrapperExecuter.withArguments("-PfooD=bar").withTasks('echoProperty').run()

        then:
        assertThat(result.output, containsString("fooD=bar"))
    }

    public void "generated wrapper scripts use correct line separators"() {
        given:
        assert distribution.binDistribution.exists(): "bin distribution must exist to run this test, you need to run the :binZip task"

        file("build.gradle") << """
            import org.gradle.api.tasks.wrapper.Wrapper
            task wrapper(type: Wrapper) {
                archiveBase = Wrapper.PathBase.PROJECT
                archivePath = 'dist'
                distributionUrl = 'http://localhost:${server.port}/gradlew/dist'
                distributionBase = Wrapper.PathBase.PROJECT
                distributionPath = 'dist'
            }
        """

        when:
        run "wrapper"
        then:
        assert file("gradlew").text.split(TextUtil.unixLineSeparator).length > 1
        assert file("gradlew").text.split(TextUtil.windowsLineSeparator).length == 1
        assert file("gradlew.bat").text.split(TextUtil.windowsLineSeparator).length > 1
        noExceptionThrown()
    }
}

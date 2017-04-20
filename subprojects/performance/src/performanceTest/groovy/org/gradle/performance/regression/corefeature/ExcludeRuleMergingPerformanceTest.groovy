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

package org.gradle.performance.regression.corefeature

import org.apache.mina.util.AvailablePortFinder
import org.gradle.performance.AbstractCrossVersionPerformanceTest
import org.mortbay.jetty.Server
import org.mortbay.jetty.webapp.WebAppContext
import org.mortbay.resource.Resource

class ExcludeRuleMergingPerformanceTest extends AbstractCrossVersionPerformanceTest {

    private final static TEST_PROJECT_NAME = 'excludeRuleMergingBuild'

    Server server
    int serverPort

    def setup() {
        serverPort = AvailablePortFinder.getNextAvailable(5000)
        server = new Server(serverPort)
        WebAppContext context = new WebAppContext()
        context.setContextPath("/")
        context.setBaseResource(Resource.newResource(new File(runner.testProjectLocator.findProjectDir('excludeRuleMergingBuild'), 'repository').getAbsolutePath()))
        server.addHandler(context)
        server.start()
    }

    def cleanup() {
        server.stop()
    }

    def "merge exclude rules"() {
        given:
        runner.testProject = TEST_PROJECT_NAME
        runner.tasksToRun = ['resolveDependencies']
        runner.gradleOpts = ["-Xms1g", "-Xmx1g"]
        runner.targetVersions = ["4.0-20170419000017+0000"]
        runner.args = ['-PuseHttp', "-PhttpPort=${serverPort}"]

        when:
        def result = runner.run()

        then:
        result.assertCurrentVersionHasNotRegressed()
    }

    def "merge exclude rules (parallel)"() {
        given:
        runner.testProject = TEST_PROJECT_NAME
        runner.tasksToRun = ['resolveDependencies']
        runner.gradleOpts = ["-Xms1g", "-Xmx1g"]
        runner.args = ['-PuseHttp', "-PhttpPort=${server.port}", "--parallel"]
        runner.targetVersions = ["4.0-20170419000017+0000"]
        when:
        def result = runner.run()

        then:
        result.assertCurrentVersionHasNotRegressed()
    }
}

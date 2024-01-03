/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.integtests.tooling.r64

import org.gradle.integtests.fixtures.executer.GradleExecuter
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification

class DaemonReuseCrossVersionSpec extends ToolingApiSpecification {
    GradleExecuter executer

    def setup() {
        toolingApi.requireIsolatedDaemons()
        executer = toolingApi.createExecuter()
        executer.useOnlyRequestedJvmOpts()
        settingsFile << "rootProject.name = 'test-build'"
    }

    def "tooling API client reuses existing daemon started by TAPI"() {
        runBuildViaTAPI()
        def original = getDaemonUID()
        when:
        runBuildViaTAPI()
        then:
        assertSameDaemon(original)
    }

    def "tooling API client reuses existing daemon started by CLI"() {
        runBuildViaCLI()
        def original = getDaemonUID()
        when:
        runBuildViaTAPI()
        then:
        assertSameDaemon(original)
    }

    def "CLI reuses existing daemon started by TAPI"() {
        runBuildViaTAPI()
        def original = getDaemonUID()
        when:
        runBuildViaCLI()
        then:
        assertSameDaemon(original)
    }

    String getDaemonUID() {
        toolingApi.daemons.daemon.context.uid
    }

    void assertSameDaemon(String expectedUID) {
        assert toolingApi.daemons.daemon.context.uid == expectedUID
    }

    private void runBuildViaTAPI() {
        withConnection {
            def build = newBuild()
            build.setJvmArguments(NORMALIZED_BUILD_JVM_OPTS + "-Djava.io.tmpdir=${buildContext.getTmpDir().absolutePath}".toString())
            build.forTasks("help")
            build.run()
        }
    }

    private void runBuildViaCLI() {
        executer.withArguments("-Dorg.gradle.jvmargs=${NORMALIZED_BUILD_JVM_OPTS.join(" ")} -Djava.io.tmpdir=${buildContext.getTmpDir().absolutePath}").withTasks("help").run()
    }
}

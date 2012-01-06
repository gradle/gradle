/*
 * Copyright 2011 the original author or authors.
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

package org.gradle.integtests.tooling.m8

import org.gradle.integtests.tooling.fixture.MinTargetGradleVersion
import org.gradle.integtests.tooling.fixture.MinToolingApiVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.tooling.model.build.BuildEnvironment

@MinToolingApiVersion('1.0-milestone-8')
@MinTargetGradleVersion('1.0-milestone-8')
class BuildEnvironmentModelIntegrationTest extends ToolingApiSpecification {

    def "informs about build environment"() {
        when:
        BuildEnvironment model = withConnection { it.getModel(BuildEnvironment.class) }

        then:
        model.gradle.gradleVersion == targetDist.version
    }

    def "informs about java versions"() {
        when:
        BuildEnvironment model = withConnection { it.getModel(BuildEnvironment.class) }

        then:
        model.java.javaHome
        !model.java.jvmArguments.empty
    }

    def "configures the java settings"() {
        given:
        def connector = connector()
            .hintJavaHome(new File("hey"))
            .hintJvmArguments("-Xmx333m", "-Xms13m")

        when:
        BuildEnvironment model = withConnection(connector) {
            it.getModel(BuildEnvironment.class)
        }

        then:
        model.java.javaHome == new File("hey")
        model.java.jvmArguments.contains("-Xmx333m")
        model.java.jvmArguments.contains("-Xms13m")
    }

    def "the jvm arguments are used in the build"() {
        given:
        //this test does not make any sense in embedded mode
        toolingApi.isEmbedded = false

        def connector = connector()
            .hintJvmArguments("-Xmx333m", "-Xms13m")

        dist.file('build.gradle') << """
def inputArgs = java.lang.management.ManagementFactory.runtimeMXBean.inputArguments
assert inputArgs.contains('-Xmx333m')
assert inputArgs.contains('-Xms13m')
"""

        when:
        withConnection(connector) {
            it.newBuild().forTasks('tasks').run()
        }

        then:
        noExceptionThrown()
    }

    def "uses sensible java defaults if nulls configured"() {
        given:
        def connector = connector()
            .hintJavaHome(null)
            .hintJvmArguments(null)

        when:
        BuildEnvironment model = withConnection(connector) {
            it.getModel(BuildEnvironment.class)
        }

        then:
        model.java.javaHome
        !model.java.jvmArguments.empty
    }

    def "may use no jvm args if requested"() {
        given:
        def connector = connector()
            .hintJvmArguments(new String[0])

        when:
        BuildEnvironment model = withConnection(connector) {
            it.getModel(BuildEnvironment.class)
        }

        then:
        model.java.jvmArguments == []
    }
}

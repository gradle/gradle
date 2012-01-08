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
class JavaConfigurabilityIntegrationTest extends ToolingApiSpecification {

    def setup() {
        //this test does not make any sense in embedded mode
        //as we don't own the process
        toolingApi.isEmbedded = false
    }

    def "configures the java settings"() {
        when:
        BuildEnvironment env = withConnection {
            def model = it.model(BuildEnvironment.class)
            model
                .setJavaHome(new File("hey"))
                .setJvmArguments("-Xmx333m", "-Xms13m")
                .get()
        }

        then:
        env.java.javaHome == new File("hey")
        env.java.jvmArguments.contains("-Xmx333m")
        env.java.jvmArguments.contains("-Xms13m")
    }

    def "uses sensible java defaults if nulls configured"() {
        when:
        BuildEnvironment env = withConnection {
            def model = it.model(BuildEnvironment.class)
            model
                .setJavaHome(new File("hey"))
                .setJvmArguments("-Xmx333m", "-Xms13m")
                .get()
        }

        then:
        env.java.javaHome
        !env.java.jvmArguments.empty
    }

    def "may use no jvm args if requested"() {
        when:
        BuildEnvironment env = withConnection {
            def model = it.model(BuildEnvironment.class)
            model
                .setJvmArguments(new String[0])
                .get()
        }

        then:
        env.java.jvmArguments == []
    }
}

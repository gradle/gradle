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

import org.gradle.integtests.fixtures.AvailableJavaHomes
import org.gradle.integtests.tooling.fixture.MinTargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.integtests.tooling.fixture.TextUtil
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.tooling.model.build.BuildEnvironment
import spock.lang.IgnoreIf

@ToolingApiVersion('>=1.0-milestone-8')
@MinTargetGradleVersion('1.0-milestone-8')
class GradlePropertiesToolingApiCrossVersionSpec extends ToolingApiSpecification {

    def setup() {
        //this test does not make any sense in embedded mode
        //as we don't own the process
        toolingApi.isEmbedded = false
    }

    def "tooling api honours jvm args specified in gradle.properties"() {
        file('build.gradle') << """
assert java.lang.management.ManagementFactory.runtimeMXBean.inputArguments.contains('-Xmx16m')
assert System.getProperty('some-prop') == 'some-value'
"""
        file('gradle.properties') << "org.gradle.jvmargs=-Dsome-prop=some-value -Xmx16m"

        when:
        BuildEnvironment env = toolingApi.withConnection { connection ->
            connection.newBuild().run() //the assert
            connection.getModel(BuildEnvironment.class)
        }

        then:
        env.java.jvmArguments.contains('-Xmx16m')
    }

    @IgnoreIf({ AvailableJavaHomes.bestAlternative == null })
    def "tooling api honours java home specified in gradle.properties"() {
        File javaHome = AvailableJavaHomes.bestAlternative
        String javaHomePath = TextUtil.escapeString(javaHome.canonicalPath)

        file('build.gradle') << "assert new File(System.getProperty('java.home')).canonicalPath.startsWith('$javaHomePath')"

        file('gradle.properties') << "org.gradle.java.home=$javaHomePath"

        when:
        BuildEnvironment env = toolingApi.withConnection { connection ->
            connection.newBuild().run() //the assert
            connection.getModel(BuildEnvironment.class)
        }

        then:
        env.java.javaHome == javaHome
    }
}

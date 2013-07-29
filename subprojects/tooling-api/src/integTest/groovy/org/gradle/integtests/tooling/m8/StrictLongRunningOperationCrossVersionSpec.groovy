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
import org.gradle.integtests.tooling.fixture.MaxTargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.tooling.exceptions.UnsupportedOperationConfigurationException
import org.gradle.tooling.model.UnsupportedMethodException
import org.gradle.tooling.model.build.BuildEnvironment
import org.gradle.tooling.model.internal.Exceptions
import spock.lang.IgnoreIf

@ToolingApiVersion('>=1.0-milestone-8')
@MaxTargetGradleVersion('1.0-milestone-7') //the configuration was not supported for old versions
class StrictLongRunningOperationCrossVersionSpec extends ToolingApiSpecification {

    def setup() {
        //this test does not make any sense in embedded mode
        //as we don't own the process and we will attempt to configure system wide options.
        toolingApi.isEmbedded = false
    }

    @IgnoreIf({ AvailableJavaHomes.bestAlternative == null })
    def "fails eagerly when java home unsupported for model"() {
        def java = AvailableJavaHomes.bestAlternative
        when:
        maybeFailWithConnection {
            def model = it.model(BuildEnvironment.class)
            model.setJavaHome(java)
            model.get()
        }

        then:
        UnsupportedOperationConfigurationException e = thrown()
        assertExceptionInformative(e, "setJavaHome()")
    }

    @IgnoreIf({ AvailableJavaHomes.bestAlternative == null })
    def "fails eagerly when java home unsupported for build"() {
        def java = AvailableJavaHomes.bestAlternative
        when:
        maybeFailWithConnection {
            def build = it.newBuild()
            build.setJavaHome(java)
            build.forTasks('tasks').run()
        }

        then:
        UnsupportedOperationConfigurationException e = thrown()
        assertExceptionInformative(e, "setJavaHome()")
    }

    def "fails eagerly when java args unsupported"() {
        when:
        maybeFailWithConnection {
            def model = it.model(BuildEnvironment.class)
            model.setJvmArguments("-Xmx512m")
            model.get()
        }

        then:
        UnsupportedOperationConfigurationException e = thrown()
        assertExceptionInformative(e, "setJvmArguments()")
    }

    def "fails eagerly when standard input unsupported"() {
        when:
        maybeFailWithConnection {
            def model = it.model(BuildEnvironment.class)
            model.setStandardInput(new ByteArrayInputStream('yo!'.bytes))
            model.get()
        }

        then:
        UnsupportedOperationConfigurationException e = thrown()
        assertExceptionInformative(e, "setStandardInput()")
    }

    void assertExceptionInformative(UnsupportedOperationConfigurationException actual, String expectedMessageSubstring) {
        assert !actual.message.contains(Exceptions.INCOMPATIBLE_VERSION_HINT) //no need for hint, the message is already good
        assert actual.message.contains(expectedMessageSubstring)

        //we don't really need that exception as a cause
        //but one of the versions was released with it as a cause so to keep things compatible
        assert actual.cause instanceof UnsupportedMethodException
    }
}

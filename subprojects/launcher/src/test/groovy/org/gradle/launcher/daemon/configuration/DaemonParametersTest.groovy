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
package org.gradle.launcher.daemon.configuration

import org.gradle.StartParameter
import org.gradle.internal.jvm.Jvm
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

import static java.lang.Boolean.parseBoolean

class DaemonParametersTest extends Specification {
    @Rule final TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider()
    final DaemonParameters parameters = new DaemonParameters()

    def "has reasonable default values"() {
        expect:
        !parameters.enabled
        parameters.idleTimeout == DaemonParameters.DEFAULT_IDLE_TIMEOUT
        def baseDir = new File(StartParameter.DEFAULT_GRADLE_USER_HOME, "daemon")
        parameters.baseDir == baseDir
        parameters.systemProperties.isEmpty()
        parameters.effectiveJvmArgs.containsAll(parameters.defaultJvmArgs)
        parameters.effectiveJvmArgs.size() == parameters.defaultJvmArgs.size() + 1 // + 1 because effective JVM args contains -Dfile.encoding
    }

    def "uses default idle timeout if prop not set"() {
        when:
        parameters.configureFrom(new GradleProperties()) //empty gradle properties

        then:
        parameters.idleTimeout == DaemonParameters.DEFAULT_IDLE_TIMEOUT
    }

    def "configuring jvmargs replaces the defaults"() {
        when:
        parameters.configureFrom(Stub(GradleProperties) {
            getJvmArgs() >> "-Xmx17m"
        })

        then:
        parameters.effectiveJvmArgs.each { assert !parameters.defaultJvmArgs.contains(it) }
    }

    def "can configure jvm args combined with a system property"() {
        when:
        parameters.configureFrom(Stub(GradleProperties) {
            getJvmArgs() >> '-Xmx1024m -Dprop=value'
        })

        then:
        parameters.effectiveJvmArgs.contains('-Xmx1024m')
        !parameters.effectiveJvmArgs.contains('-Dprop=value')

        parameters.systemProperties == [prop: 'value']
    }


    def "supports 'empty' system properties"() {
        when:
        parameters.configureFrom(Stub(GradleProperties) {
            getJvmArgs() >> "-Dfoo= -Dbar"
        })

        then:
        parameters.getSystemProperties() == [foo: '', bar: '']
    }

    def "knows if not using default jvm args"() {
        given:
        assert parameters.usingDefaultJvmArgs

        when:
        parameters.setJvmArgs(["-Dfoo= -Dbar"])

        then:
        !parameters.usingDefaultJvmArgs
    }

    def "knows if not using default jvm args when configured"() {
        given:
        assert parameters.usingDefaultJvmArgs

        when:
        parameters.configureFrom(Stub(GradleProperties) {
            getJvmArgs() >> "-Dfoo= -Dbar"
        })

        then:
        !parameters.usingDefaultJvmArgs
    }

    def "knows if using default jvm args"() {
        when:
        parameters.configureFrom(Stub(GradleProperties) {
            getJavaHome() >> Jvm.current().getJavaHome()
            getJvmArgs() >> null
        })

        then:
        parameters.usingDefaultJvmArgs
    }

    def "can configure debug mode"() {
        when:
        parameters.configureFrom(Stub (GradleProperties) {
            isDebugMode() >> parseBoolean(flag)
        })

        then:
        parameters.effectiveJvmArgs.contains("-Xdebug") == parseBoolean(flag)
        parameters.effectiveJvmArgs.contains("-Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=5005") == parseBoolean(flag)

        where:
        flag << ["true", "false"]
    }

    //TODO SF add missing coverage for other properties
}

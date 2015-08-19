/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.launcher.cli.converter

import org.gradle.api.GradleException
import org.gradle.initialization.BuildLayoutParameters
import org.gradle.internal.jvm.Jvm
import org.gradle.launcher.daemon.configuration.DaemonParameters
import org.gradle.launcher.daemon.configuration.GradleProperties
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.UsesNativeServices
import org.junit.Rule
import spock.lang.Specification
import spock.lang.Unroll

import static org.gradle.launcher.daemon.configuration.DaemonUsage.*
import static org.gradle.launcher.daemon.configuration.GradleProperties.*

@UsesNativeServices
class PropertiesToDaemonParametersConverterTest extends Specification {
    @Rule
    TestNameTestDirectoryProvider temp = new TestNameTestDirectoryProvider()

    def converter = new PropertiesToDaemonParametersConverter()
    def params = new DaemonParameters(new BuildLayoutParameters())

    def "can configure jvm args combined with a system property"() {
        when:
        converter.convert([(JVM_ARGS_PROPERTY): '-Xmx512m -Dprop=value'], params)

        then:
        params.effectiveJvmArgs.contains('-Xmx512m')
        !params.effectiveJvmArgs.contains('-Dprop=value')

        params.systemProperties == [prop: 'value']
    }

    def "supports 'empty' system properties"() {
        when:
        converter.convert([(JVM_ARGS_PROPERTY): "-Dfoo= -Dbar"], params)

        then:
        params.systemProperties == [foo: '', bar: '']
    }

    def "configures from gradle properties"() {
        when:
        converter.convert([
                (JVM_ARGS_PROPERTY)       : '-Xmx256m',
                (JAVA_HOME_PROPERTY)      : Jvm.current().javaHome.absolutePath,
                (DAEMON_ENABLED_PROPERTY) : "true",
                (DAEMON_BASE_DIR_PROPERTY): new File("baseDir").absolutePath,
                (IDLE_TIMEOUT_PROPERTY)   : "115",
                (DEBUG_MODE_PROPERTY)     : "true",
        ], params)

        then:
        params.effectiveJvmArgs.contains("-Xmx256m")
        params.debug
        params.effectiveJvm == Jvm.current()
        params.daemonUsage == EXPLICITLY_ENABLED
        params.baseDir == new File("baseDir").absoluteFile
        params.idleTimeout == 115
    }

    def "shows nice message for dummy java home"() {
        when:
        converter.convert([(JAVA_HOME_PROPERTY): "/invalid/path"], params)

        then:
        def ex = thrown(GradleException)
        ex.message.contains 'org.gradle.java.home'
        ex.message.contains '/invalid/path'
    }

    def "shows nice message for invalid java home"() {
        def dummyDir = temp.createDir("foobar")
        when:
        converter.convert([(GradleProperties.JAVA_HOME_PROPERTY): dummyDir.absolutePath], params)

        then:
        def ex = thrown(GradleException)
        ex.message.contains 'org.gradle.java.home'
        ex.message.contains 'foobar'
    }

    def "shows nice message for invalid idle timeout"() {
        when:
        converter.convert((GradleProperties.IDLE_TIMEOUT_PROPERTY): 'asdf', params)

        then:
        def ex = thrown(GradleException)
        ex.message.contains 'org.gradle.daemon.idletimeout'
        ex.message.contains 'asdf'
    }

    def "does not explicitly set daemon usage if daemon system property is not specified"() {
        when:
        converter.convert([:], params)

        then:
        params.daemonUsage == IMPLICITLY_DISABLED
    }

    @Unroll
    def "explicitly sets daemon usage if daemon system property is specified"() {
        when:
        converter.convert((GradleProperties.DAEMON_ENABLED_PROPERTY): enabled.toString(), params)

        then:
        params.daemonUsage == usage

        where:
        enabled | usage
        true    | EXPLICITLY_ENABLED
        false   | EXPLICITLY_DISABLED
    }
}

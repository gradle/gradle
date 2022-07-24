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

import org.gradle.api.internal.file.TestFiles
import org.gradle.internal.jvm.Jvm
import org.gradle.launcher.configuration.BuildLayoutResult
import org.gradle.launcher.daemon.configuration.DaemonBuildOptions
import org.gradle.launcher.daemon.configuration.DaemonParameters
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

class PropertiesToDaemonParametersConverterTest extends Specification {
    @Rule
    TestNameTestDirectoryProvider temp = new TestNameTestDirectoryProvider(getClass())
    def buildLayoutResult = Stub(BuildLayoutResult) {
        getGradleUserHomeDir() >> temp.file("gradle-user-home")
    }

    def converter = new DaemonBuildOptions().propertiesConverter()
    def params = new DaemonParameters(buildLayoutResult, TestFiles.fileCollectionFactory())

    def "allows whitespace around boolean properties"() {
        when:
        converter.convert([(DaemonBuildOptions.DaemonOption.GRADLE_PROPERTY): 'false '], params)
        then:
        !params.enabled
    }

    def "can configure jvm args combined with a system property"() {
        when:
        converter.convert([(DaemonBuildOptions.JvmArgsOption.GRADLE_PROPERTY): '-Xmx512m -Dprop=value'], params)

        then:
        params.effectiveJvmArgs.contains('-Xmx512m')
        !params.effectiveJvmArgs.contains('-Dprop=value')

        params.systemProperties == [prop: 'value']
    }

    def "supports 'empty' system properties"() {
        when:
        converter.convert([(DaemonBuildOptions.JvmArgsOption.GRADLE_PROPERTY): "-Dfoo= -Dbar"], params)

        then:
        params.systemProperties == [foo: '', bar: '']
    }

    def "configures from gradle properties"() {
        when:
        converter.convert([
            (DaemonBuildOptions.JvmArgsOption.GRADLE_PROPERTY): '-Xmx256m',
            (DaemonBuildOptions.JavaHomeOption.GRADLE_PROPERTY): Jvm.current().javaHome.absolutePath,
            (DaemonBuildOptions.DaemonOption.GRADLE_PROPERTY): "false",
            (DaemonBuildOptions.BaseDirOption.GRADLE_PROPERTY): new File("baseDir").absolutePath,
            (DaemonBuildOptions.IdleTimeoutOption.GRADLE_PROPERTY): "115",
            (DaemonBuildOptions.HealthCheckOption.GRADLE_PROPERTY): "42",
            (DaemonBuildOptions.DebugOption.GRADLE_PROPERTY): "true",
        ], params)

        then:
        params.effectiveJvmArgs.contains("-Xmx256m")
        params.debug
        params.effectiveJvm == Jvm.current()
        !params.enabled
        params.baseDir == new File("baseDir").absoluteFile
        params.idleTimeout == 115
        params.periodicCheckInterval == 42
    }

    def "shows nice message for dummy java home"() {
        when:
        converter.convert([(DaemonBuildOptions.JavaHomeOption.GRADLE_PROPERTY): "/invalid/path"], params)

        then:
        def ex = thrown(IllegalArgumentException)
        ex.message.contains 'org.gradle.java.home'
        ex.message.contains '/invalid/path'
    }

    def "shows nice message for invalid java home"() {
        def dummyDir = temp.createDir("foobar")
        when:
        converter.convert([(DaemonBuildOptions.JavaHomeOption.GRADLE_PROPERTY): dummyDir.absolutePath], params)

        then:
        def ex = thrown(IllegalArgumentException)
        ex.message.contains 'org.gradle.java.home'
        ex.message.contains 'foobar'
    }

    def "shows nice message for invalid idle timeout"() {
        when:
        converter.convert((DaemonBuildOptions.IdleTimeoutOption.GRADLE_PROPERTY): 'asdf', params)

        then:
        def ex = thrown(IllegalArgumentException)
        ex.message.contains 'org.gradle.daemon.idletimeout'
        ex.message.contains 'asdf'
    }

    def "shows nice message for invalid periodic check interval"() {
        when:
        converter.convert((DaemonBuildOptions.HealthCheckOption.GRADLE_PROPERTY): 'bogus', params)

        then:
        def ex = thrown(IllegalArgumentException)
        ex.message.contains 'org.gradle.daemon.healthcheckinterval'
        ex.message.contains 'bogus'
    }

    def "explicitly sets daemon usage if daemon system property is specified - #enabled"() {
        when:
        converter.convert((DaemonBuildOptions.DaemonOption.GRADLE_PROPERTY): enabled.toString(), params)

        then:
        params.enabled == propertyValue

        where:
        enabled | propertyValue
        true    | true
        false   | false
    }
}

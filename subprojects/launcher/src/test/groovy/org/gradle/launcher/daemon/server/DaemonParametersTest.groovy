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
package org.gradle.launcher.daemon.server

import org.gradle.StartParameter
import org.gradle.api.GradleException
import org.gradle.util.TemporaryFolder
import org.junit.Rule
import spock.lang.Specification

class DaemonParametersTest extends Specification {
    @Rule final TemporaryFolder tmpDir = new TemporaryFolder()
    final DaemonParameters parameters = new DaemonParameters()

    def "has reasonable default values"() {
        expect:
        !parameters.enabled
        parameters.idleTimeout == DaemonParameters.DEFAULT_IDLE_TIMEOUT
        parameters.baseDir == new File(StartParameter.DEFAULT_GRADLE_USER_HOME, "daemon")
        parameters.systemProperties.isEmpty()
        // Not that reasonable
        parameters.jvmArgs == ['-XX:MaxPermSize=256m', '-Xmx1024m']
    }

    def "determines base dir from user home dir"() {
        def userHome = new File("some-dir")

        when:
        parameters.configureFromGradleUserHome(userHome)

        then:
        parameters.baseDir == new File(userHome, "daemon").canonicalFile
    }

    def "can configure base directory using system property"() {
        when:
        parameters.configureFromSystemProperties((DaemonParameters.BASE_DIR_SYS_PROPERTY): 'some-dir')

        then:
        parameters.baseDir == new File('some-dir').canonicalFile
    }

    def "can configure idle timeout using system property"() {
        when:
        parameters.configureFromSystemProperties((DaemonParameters.IDLE_TIMEOUT_SYS_PROPERTY): '4000')

        then:
        parameters.idleTimeout == 4000
    }

    def "nice message for invalid idle timeout"() {
        when:
        parameters.configureFromSystemProperties((DaemonParameters.IDLE_TIMEOUT_SYS_PROPERTY): 'asdf')

        then:
        def ex = thrown(GradleException)
        ex.message.contains 'org.gradle.daemon.idletimeout'
        ex.message.contains 'asdf'
    }

    def "uses default idle timeout if prop not set"() {
        when:
        parameters.configureFromSystemProperties(abc: 'def')

        then:
        parameters.idleTimeout == DaemonParameters.DEFAULT_IDLE_TIMEOUT
    }

    def "can configure jvm args using system property"() {
        when:
        parameters.configureFromSystemProperties((DaemonParameters.JVM_ARGS_SYS_PROPERTY):  '-Xmx1024m -Dprop=value')

        then:
        parameters.jvmArgs == ['-Xmx1024m',]
        parameters.systemProperties == [prop: 'value']
    }

    def "can configure jvm args using gradle.properties in root directory"() {
        given:
        tmpDir.createFile("settings.gradle")
        tmpDir.file("gradle.properties").withOutputStream { outstr ->
            new Properties((DaemonParameters.JVM_ARGS_SYS_PROPERTY): '-Xmx1024m -Dprop=value').store(outstr, "HEADER")
        }

        when:
        parameters.configureFromBuildDir(tmpDir.dir, true)

        then:
        parameters.jvmArgs == ['-Xmx1024m']
        parameters.systemProperties == [prop: 'value']
    }

    def "can configure idle timeout using gradle.properties in root directory"() {
        given:
        tmpDir.createFile("settings.gradle")
        tmpDir.file("gradle.properties").withOutputStream { outstr ->
            new Properties((DaemonParameters.IDLE_TIMEOUT_SYS_PROPERTY): '1450').store(outstr, "HEADER")
        }

        when:
        parameters.configureFromBuildDir(tmpDir.dir, true)

        then:
        parameters.idleTimeout == 1450
    }

    def "can configure parameters using gradle.properties in user home directory"() {
        given:
        tmpDir.file("gradle.properties").withOutputStream { outstr ->
            new Properties((DaemonParameters.JVM_ARGS_SYS_PROPERTY): '-Xmx1024m -Dprop=value').store(outstr, "HEADER")
        }

        when:
        parameters.configureFromGradleUserHome(tmpDir.dir)

        then:
        parameters.jvmArgs == ['-Xmx1024m']
        parameters.systemProperties == [prop: 'value']
    }

    def "can enable daemon using system property"() {
        when:
        parameters.configureFromSystemProperties((DaemonParameters.DAEMON_SYS_PROPERTY):  'true')

        then:
        parameters.enabled
    }

    def "can disable daemon using system property"() {
        when:
        parameters.configureFromSystemProperties((DaemonParameters.DAEMON_SYS_PROPERTY):  'no way')

        then:
        !parameters.enabled
    }

    def "can enable daemon using gradle.properties in root directory"() {
        given:
        tmpDir.createFile("settings.gradle")
        tmpDir.file("gradle.properties").withOutputStream { outstr ->
            new Properties((DaemonParameters.DAEMON_SYS_PROPERTY): 'true').store(outstr, "HEADER")
        }

        when:
        parameters.configureFromBuildDir(tmpDir.dir, true)

        then:
        parameters.enabled
    }
}

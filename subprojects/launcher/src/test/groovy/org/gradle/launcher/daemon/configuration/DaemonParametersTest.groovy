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
import org.gradle.api.GradleException
import org.gradle.internal.jvm.Jvm
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

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
        // Not that reasonable
        parameters.effectiveJvmArgs.containsAll(parameters.defaultJvmArgs)
        parameters.effectiveJvmArgs.size() == parameters.defaultJvmArgs.size() + 1 // + 1 because effective JVM args contains -Dfile.encoding
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

    def "configuring jvmargs replaces the defaults"() {
        when:
        parameters.configureFrom([(DaemonParameters.JVM_ARGS_SYS_PROPERTY) : "-Xmx17m"])

        then:
        parameters.effectiveJvmArgs.each { assert !parameters.defaultJvmArgs.contains(it) }
    }

    def "can configure jvm args using system property"() {
        when:
        parameters.configureFromSystemProperties((DaemonParameters.JVM_ARGS_SYS_PROPERTY):  '-Xmx1024m -Dprop=value')

        then:
        parameters.effectiveJvmArgs.contains('-Xmx1024m')
        !parameters.effectiveJvmArgs.contains('-Dprop=value')

        parameters.systemProperties == [prop: 'value']
    }

    def "can configure jvm args using gradle.properties in root directory"() {
        given:
        tmpDir.createFile("settings.gradle")
        tmpDir.file("gradle.properties").withOutputStream { outstr ->
            new Properties((DaemonParameters.JVM_ARGS_SYS_PROPERTY): '-Xmx1024m -Dprop=value').store(outstr, "HEADER")
        }

        when:
        parameters.configureFromBuildDir(tmpDir.testDirectory, true)

        then:
        parameters.effectiveJvmArgs.contains('-Xmx1024m')
        !parameters.effectiveJvmArgs.contains('-Dprop=value')

        parameters.systemProperties == [prop: 'value']
    }

    def "can configure idle timeout using gradle.properties in root directory"() {
        given:
        tmpDir.createFile("settings.gradle")
        tmpDir.file("gradle.properties").withOutputStream { outstr ->
            new Properties((DaemonParameters.IDLE_TIMEOUT_SYS_PROPERTY): '1450').store(outstr, "HEADER")
        }

        when:
        parameters.configureFromBuildDir(tmpDir.testDirectory, true)

        then:
        parameters.idleTimeout == 1450
    }

    def "can configure parameters using gradle.properties in user home directory"() {
        given:
        tmpDir.file("gradle.properties").withOutputStream { outstr ->
            new Properties((DaemonParameters.JVM_ARGS_SYS_PROPERTY): '-Xmx1024m -Dprop=value').store(outstr, "HEADER")
        }

        when:
        parameters.configureFromGradleUserHome(tmpDir.testDirectory)

        then:
        parameters.effectiveJvmArgs.contains('-Xmx1024m')
        !parameters.effectiveJvmArgs.contains('-Dprop=value')

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
        parameters.configureFromBuildDir(tmpDir.testDirectory, true)

        then:
        parameters.enabled
    }

    def "can configure java home"() {
        File jdk = Jvm.current().getJavaHome()

        when:
        parameters.configureFrom([(DaemonParameters.JAVA_HOME_SYS_PROPERTY) : jdk.toString()])

        then:
        parameters.effectiveJavaHome == jdk.canonicalFile
    }

    def "nice message for dummy java home"() {
        when:
        parameters.configureFrom([(DaemonParameters.JAVA_HOME_SYS_PROPERTY) : "/invalid/path"])

        then:
        def ex = thrown(GradleException)
        ex.message.contains 'org.gradle.java.home'
        ex.message.contains '/invalid/path'
    }

    def "nice message for invalid java home"() {
        def dummyDir = tmpDir.createDir("foobar")
        when:
        parameters.configureFrom([(DaemonParameters.JAVA_HOME_SYS_PROPERTY) : dummyDir.absolutePath])

        then:
        def ex = thrown(GradleException)
        ex.message.contains 'org.gradle.java.home'
        ex.message.contains 'foobar'
    }

    def "supports 'empty' system properties"() {
        when:
        parameters.configureFrom([(DaemonParameters.JVM_ARGS_SYS_PROPERTY) : "-Dfoo= -Dbar"])

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
    
    def "knows if using default jvm args"() {
        when:
        parameters.configureFrom([(DaemonParameters.JAVA_HOME_SYS_PROPERTY) : Jvm.current().getJavaHome()])

        then:
        parameters.usingDefaultJvmArgs
    }

    def "knows if not using default jvm args when configured"() {
        given:
        assert parameters.usingDefaultJvmArgs

        when:
        parameters.configureFrom([(DaemonParameters.JVM_ARGS_SYS_PROPERTY) : "-Dfoo= -Dbar"])

        then:
        !parameters.usingDefaultJvmArgs
    }

    def "can configure debug mode"() {
        when:
        parameters.configureFrom((DaemonParameters.DEBUG_SYS_PROPERTY): flag)

        then:
        parameters.effectiveJvmArgs.contains("-Xdebug") == Boolean.parseBoolean(flag)
        parameters.effectiveJvmArgs.contains("-Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=5005") == Boolean.parseBoolean(flag)

        where:
        flag << ["true", "false"]
    }
}

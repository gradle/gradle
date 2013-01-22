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

package org.gradle.launcher.daemon.configuration

import org.gradle.api.GradleException
import org.gradle.internal.jvm.Jvm
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

/**
 * by Szczepan Faber, created at: 1/22/13
 */
class GradlePropertiesTest extends Specification {

    @Rule final TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider()
    private properties = new GradleProperties()

    def "determines base dir from user home dir"() {
        def userHome = new File("some-dir")

        when:
        properties.configureFromGradleUserHome(userHome)

        then:
        properties.daemonBaseDir == new File(userHome, "daemon").canonicalFile
    }

    def "can configure base directory using system property"() {
        when:
        properties.configureFromSystemProperties((GradleProperties.BASE_DIR_PROPERTY): 'some-dir')

        then:
        properties.daemonBaseDir == new File('some-dir').canonicalFile
    }

    //TODO SF rework the tests to avoid duplication
    def "can configure idle timeout using system property"() {
        when:
        properties.configureFromSystemProperties((GradleProperties.IDLE_TIMEOUT_PROPERTY): '4000')

        then:
        properties.idleTimeout == 4000
    }

    def "nice message for invalid idle timeout"() {
        when:
        properties.configureFromSystemProperties((GradleProperties.IDLE_TIMEOUT_PROPERTY): 'asdf')

        then:
        def ex = thrown(GradleException)
        ex.message.contains 'org.gradle.daemon.idletimeout'
        ex.message.contains 'asdf'
    }

    def "can configure jvm args using system property"() {
        when:
        properties.configureFromSystemProperties((GradleProperties.JVM_ARGS_PROPERTY):  '-Xmx1024m -Dprop=value')

        then:
        properties.jvmArgs == '-Xmx1024m -Dprop=value'
    }

    def "can configure jvm args using gradle.properties in root directory"() {
        given:
        tmpDir.createFile("settings.gradle")
        tmpDir.file("gradle.properties").withOutputStream { outstr ->
            new Properties((GradleProperties.JVM_ARGS_PROPERTY): '-Xmx1024m -Dprop=value').store(outstr, "HEADER")
        }

        when:
        properties.configureFromBuildDir(tmpDir.testDirectory, true)

        then:
        properties.jvmArgs == '-Xmx1024m -Dprop=value'
    }

    def "can configure idle timeout using gradle.properties in root directory"() {
        given:
        tmpDir.createFile("settings.gradle")
        tmpDir.file("gradle.properties").withOutputStream { outstr ->
            new Properties((GradleProperties.IDLE_TIMEOUT_PROPERTY): '1450').store(outstr, "HEADER")
        }

        when:
        properties.configureFromBuildDir(tmpDir.testDirectory, true)

        then:
        properties.idleTimeout == 1450
    }

    def "can configure parameters using gradle.properties in user home directory"() {
        given:
        tmpDir.file("gradle.properties").withOutputStream { outstr ->
            new Properties((GradleProperties.JVM_ARGS_PROPERTY): '-Xmx1024m -Dprop=value').store(outstr, "HEADER")
        }

        when:
        properties.configureFromGradleUserHome(tmpDir.testDirectory)

        then:
        properties.jvmArgs == '-Xmx1024m -Dprop=value'
    }

    def "can enable daemon using system property"() {
        when:
        properties.configureFromSystemProperties((GradleProperties.DAEMON_ENABLED_PROPERTY):  'true')

        then:
        properties.daemonEnabled
    }

    def "can disable daemon using system property"() {
        when:
        properties.configureFromSystemProperties((GradleProperties.DAEMON_ENABLED_PROPERTY):  'no way')

        then:
        !properties.daemonEnabled
    }

    def "can enable daemon using gradle.properties in root directory"() {
        given:
        tmpDir.createFile("settings.gradle")
        tmpDir.file("gradle.properties").withOutputStream { outstr ->
            new Properties((GradleProperties.DAEMON_ENABLED_PROPERTY): 'true').store(outstr, "HEADER")
        }

        when:
        properties.configureFromBuildDir(tmpDir.testDirectory, true)

        then:
        properties.daemonEnabled
    }

    def "can configure java home"() {
        File jdk = Jvm.current().getJavaHome()

        when:
        properties.configureFrom([(GradleProperties.JAVA_HOME_PROPERTY) : jdk.toString()])

        then:
        properties.javaHome == jdk.canonicalFile
    }

    def "nice message for dummy java home"() {
        when:
        properties.configureFrom([(GradleProperties.JAVA_HOME_PROPERTY) : "/invalid/path"])

        then:
        def ex = thrown(GradleException)
        ex.message.contains 'org.gradle.java.home'
        ex.message.contains '/invalid/path'
    }

    def "nice message for invalid java home"() {
        def dummyDir = tmpDir.createDir("foobar")
        when:
        properties.configureFrom([(GradleProperties.JAVA_HOME_PROPERTY) : dummyDir.absolutePath])

        then:
        def ex = thrown(GradleException)
        ex.message.contains 'org.gradle.java.home'
        ex.message.contains 'foobar'
    }

    def "can configure debug mode"() {
        when:
        properties.configureFrom((GradleProperties.DEBUG_MODE_PROPERTY): flag)

        then:
        properties.debugMode.toString() == flag

        where:
        flag << ["true", "false"]
    }
}

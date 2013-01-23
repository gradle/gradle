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

    def "configures from gradle home dir (using jvm args as example)"() {
        given:
        tmpDir.file("gradle.properties").withOutputStream { outstr ->
            new Properties((GradleProperties.JVM_ARGS_PROPERTY): '-Xmx1024m -Dprop=value').store(outstr, "HEADER")
        }

        when:
        properties.configureFromGradleUserHome(tmpDir.testDirectory)

        then:
        properties.jvmArgs == '-Xmx1024m -Dprop=value'
    }

    def "configures from project dir (using jvm args as example)"() {
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

    def "configures from system properties (using jvm args as example)"() {
        when:
        properties.configureFromSystemProperties((GradleProperties.JVM_ARGS_PROPERTY):  '-Xmx1024m -Dprop=value')

        then:
        properties.jvmArgs == '-Xmx1024m -Dprop=value'
    }

    def "sets default daemon base dir when configuring from gradle user home"() {
        def userHome = new File("some-dir")

        when:
        properties.configureFromGradleUserHome(userHome)

        then:
        properties.daemonBaseDir == new File(userHome, "daemon").canonicalFile
    }

    def "configures daemon base dir when configuring from system properties"() {
        when:
        properties.configureFromSystemProperties((GradleProperties.BASE_DIR_PROPERTY): 'some-dir')

        then:
        properties.daemonBaseDir == new File('some-dir').canonicalFile
    }

    def "configures idle timeout"() {
        when:
        properties.configureFrom((GradleProperties.IDLE_TIMEOUT_PROPERTY): '4000')

        then:
        properties.idleTimeout == 4000
    }

    def "shows nice message for invalid idle timeout"() {
        when:
        properties.configureFrom((GradleProperties.IDLE_TIMEOUT_PROPERTY): 'asdf')

        then:
        def ex = thrown(GradleException)
        ex.message.contains 'org.gradle.daemon.idletimeout'
        ex.message.contains 'asdf'
    }

    def "configures daemon mode"() {
        when:
        properties.configureFrom((GradleProperties.DAEMON_ENABLED_PROPERTY):  flag)

        then:
        properties.daemonEnabled.toString() == flag

        where:
        flag << ["true", "false"]
    }

    def "configures java home"() {
        File jdk = Jvm.current().getJavaHome()

        when:
        properties.configureFrom([(GradleProperties.JAVA_HOME_PROPERTY) : jdk.toString()])

        then:
        properties.javaHome == jdk.canonicalFile
    }

    def "shows nice message for dummy java home"() {
        when:
        properties.configureFrom([(GradleProperties.JAVA_HOME_PROPERTY) : "/invalid/path"])

        then:
        def ex = thrown(GradleException)
        ex.message.contains 'org.gradle.java.home'
        ex.message.contains '/invalid/path'
    }

    def "shows nice message for invalid java home"() {
        def dummyDir = tmpDir.createDir("foobar")
        when:
        properties.configureFrom([(GradleProperties.JAVA_HOME_PROPERTY) : dummyDir.absolutePath])

        then:
        def ex = thrown(GradleException)
        ex.message.contains 'org.gradle.java.home'
        ex.message.contains 'foobar'
    }

    def "configures debug mode"() {
        when:
        properties.configureFrom((GradleProperties.DEBUG_MODE_PROPERTY): flag)

        then:
        properties.debugMode.toString() == flag

        where:
        flag << ["true", "false"]
    }
}

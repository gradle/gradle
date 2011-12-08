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

import spock.lang.Specification
import org.gradle.api.GradleException
import org.gradle.StartParameter


class DaemonParametersTest extends Specification {
    final DaemonParameters parameters = new DaemonParameters()

    def "has reasonable default values"() {
        expect:
        parameters.idleTimeout == DaemonParameters.DEFAULT_IDLE_TIMEOUT
        parameters.baseDir == new File(StartParameter.DEFAULT_GRADLE_USER_HOME, "daemon")
    }

    def "determines base dir from Gradle user home dir"() {
        def userHome = new File("some-dir")

        when:
        parameters.useGradleUserHomeDir(userHome)

        then:
        parameters.baseDir == new File(userHome, "daemon").canonicalFile
    }

    def "can configure base directory using system property"() {
        when:
        parameters.configureFromSystemProperties((DaemonParameters.SYSTEM_PROPERTY_KEY):  'some-dir')

        then:
        parameters.baseDir == new File('some-dir').canonicalFile
    }

    def "can configure idle timeout using system property"() {
        when:
        parameters.configureFromSystemProperties((DaemonParameters.IDLE_TIMEOUT_SYS_PROPERTY):  '4000')

        then:
        parameters.idleTimeout == 4000
    }

    def "nice message for invalid idle timeout"() {
        when:
        parameters.configureFromSystemProperties((DaemonParameters.IDLE_TIMEOUT_SYS_PROPERTY):  'asdf')

        then:
        def ex = thrown(GradleException)
        ex.message.contains 'org.gradle.daemon.idletimeout'
        ex.message.contains 'asdf'
    }

    def "uses default idle timeout if prop not set"() {
        when:
        parameters.configureFromSystemProperties(abc:  'def')

        then:
        parameters.idleTimeout == DaemonParameters.DEFAULT_IDLE_TIMEOUT
    }

}

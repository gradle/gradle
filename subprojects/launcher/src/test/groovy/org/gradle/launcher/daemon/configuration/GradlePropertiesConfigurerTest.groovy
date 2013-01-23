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

import org.gradle.StartParameter
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

import static java.util.Collections.singletonMap

/**
 * by Szczepan Faber, created at: 1/22/13
 */
class GradlePropertiesConfigurerTest extends Specification {

    @Rule private TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider()
    private configurer = Spy(GradlePropertiesConfigurer)

    def "prepares properties and configures parameters"() {
        def param = Mock(StartParameter)
        def properties = Mock(GradleProperties)

        configurer.prepareProperties(_, false, _, _) >> properties

        when:
        def daemonParams = configurer.configureParameters(param);

        then:
        //daemon params created
        daemonParams
        //start parameter updated
        1 * properties.updateStartParameter(param)
        //daemon params configured from properties
        properties.getIdleTimeout() >> 123
        daemonParams.idleTimeout == 123
    }

    def "gradle home properties take precedence over project dir properties"() {
        def projectDir = tmpDir.createDir("project")
        projectDir.file("gradle.properties") << "$GradleProperties.IDLE_TIMEOUT_PROPERTY=100"
        def gradleHome = tmpDir.createDir("gradleHome")
        gradleHome.file("gradle.properties") << "$GradleProperties.IDLE_TIMEOUT_PROPERTY=200"

        when:
        def props = configurer.prepareProperties(projectDir, false, gradleHome, [:])

        then:
        props.idleTimeout == 200
    }

    def "system property takes precedence over gradle home"() {
        def projectDir = tmpDir.createDir("project")
        def gradleHome = tmpDir.createDir("gradleHome")
        gradleHome.file("gradle.properties") << "$GradleProperties.IDLE_TIMEOUT_PROPERTY=200"

        when:
        def props = configurer.prepareProperties(projectDir, false, gradleHome, singletonMap(GradleProperties.IDLE_TIMEOUT_PROPERTY, '300'))

        then:
        props.idleTimeout == 300
    }
}

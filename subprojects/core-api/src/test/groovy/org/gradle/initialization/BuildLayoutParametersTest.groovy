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

package org.gradle.initialization

import org.gradle.StartParameter
import org.gradle.internal.SystemProperties
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.UnitTestPreconditions
import org.gradle.util.SetSystemProperties
import org.junit.Rule
import spock.lang.Issue
import spock.lang.Specification

import static org.gradle.internal.FileUtils.canonicalize

class BuildLayoutParametersTest extends Specification {

    @Rule SetSystemProperties props = new SetSystemProperties()
    @Rule TestNameTestDirectoryProvider temp = new TestNameTestDirectoryProvider(getClass())

    @Requires(UnitTestPreconditions.NotEC2Agent)
    @Issue('https://github.com/gradle/gradle-private/issues/2876')
    def "has reasonable defaults"() {
        expect:
        def params = new BuildLayoutParameters()
        params.gradleUserHomeDir == canonicalize(BuildLayoutParameters.DEFAULT_GRADLE_USER_HOME)
        params.currentDir == canonicalize(SystemProperties.instance.getCurrentDir())
        params.projectDir == null
        params.searchDir == params.currentDir
    }

    def "reads gradle user home dir from system property"() {
        def dir = temp.createDir("someGradleUserHomePath")
        System.setProperty(StartParameter.GRADLE_USER_HOME_PROPERTY_KEY, dir.absolutePath)

        when:
        def params = new BuildLayoutParameters()

        then:
        params.gradleUserHomeDir == dir
    }
}

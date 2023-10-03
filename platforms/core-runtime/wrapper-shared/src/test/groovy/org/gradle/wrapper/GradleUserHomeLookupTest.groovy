/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.wrapper

import org.gradle.internal.nativeintegration.ProcessEnvironment
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.UnitTestPreconditions
import org.gradle.testfixtures.internal.NativeServicesTestFixture
import org.gradle.util.SetSystemProperties
import org.junit.Rule
import spock.lang.Issue
import spock.lang.Specification

class GradleUserHomeLookupTest extends Specification {

    final ProcessEnvironment env = NativeServicesTestFixture.getInstance().get(ProcessEnvironment)

    @Rule
    SetSystemProperties setSystemProperties = new SetSystemProperties()

    @Requires(UnitTestPreconditions.NotEC2Agent)
    @Issue('https://github.com/gradle/gradle-private/issues/2876')
    def "returns default Gradle user home if environment variable or system property isn't defined"() {
        expect:
        GradleUserHomeLookup.gradleUserHome() == new File(GradleUserHomeLookup.DEFAULT_GRADLE_USER_HOME)
    }

    def "returns Gradle user home set by system property"() {
        when:
        String userDefinedDirName = 'some/dir'
        System.setProperty(GradleUserHomeLookup.GRADLE_USER_HOME_PROPERTY_KEY, userDefinedDirName)

        then:
        GradleUserHomeLookup.gradleUserHome() == new File(userDefinedDirName)
    }

    def "returns Gradle user home set by environment variable"() {
        when:
        String userDefinedDirName = 'some/dir'
        env.setEnvironmentVariable(GradleUserHomeLookup.GRADLE_USER_HOME_ENV_KEY, userDefinedDirName)

        then:
        GradleUserHomeLookup.gradleUserHome() == new File(userDefinedDirName)

        cleanup:
        env.removeEnvironmentVariable(GradleUserHomeLookup.GRADLE_USER_HOME_ENV_KEY)
    }

    def "Gradle user home set by system property takes precedence over environment variable"() {
        when:
        String sysPropDirName = 'some/dir'
        String envVarDirName = 'other/dir'
        System.setProperty(GradleUserHomeLookup.GRADLE_USER_HOME_PROPERTY_KEY, sysPropDirName)
        env.setEnvironmentVariable(GradleUserHomeLookup.GRADLE_USER_HOME_ENV_KEY, envVarDirName)

        then:
        GradleUserHomeLookup.gradleUserHome() == new File(sysPropDirName)

        cleanup:
        env.removeEnvironmentVariable(GradleUserHomeLookup.GRADLE_USER_HOME_ENV_KEY)
    }
}

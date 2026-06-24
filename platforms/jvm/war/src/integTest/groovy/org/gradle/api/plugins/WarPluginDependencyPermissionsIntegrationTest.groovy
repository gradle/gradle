/*
 * Copyright 2026 the original author or authors.
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
package org.gradle.api.plugins

import org.gradle.integtests.fixtures.AbstractHttpDependencyResolutionTest
import org.gradle.test.fixtures.archive.JarTestFixture
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.OsTestPreconditions
import spock.lang.Issue

class WarPluginDependencyPermissionsIntegrationTest extends AbstractHttpDependencyResolutionTest {

    @Requires(OsTestPreconditions.NotWindows)
    @Issue("https://github.com/gradle/gradle/issues/38284")
    def "external dependency JARs bundled into WEB-INF/lib respect the umask"() {
        given:
        def module = mavenHttpRepo.module('org.gradle.test', 'external', '1.0').publish()
        module.allowAll()

        settingsFile << "rootProject.name = 'test'"
        buildFile << """
            plugins {
                id 'war'
            }
            repositories {
                maven { url = '${mavenHttpRepo.uri}' }
            }
            dependencies {
                implementation 'org.gradle.test:external:1.0'
            }
        """

        when:
        succeeds "war"

        then:
        def war = new JarTestFixture(file('build/libs/test.war'))
        war.assertContainsFile('WEB-INF/lib/external-1.0.jar')
        // Before the fix the freshly downloaded artifact was cached as rw------- (0600) and that mode
        // was bundled into the WAR entry, leaving the JAR unreadable for other users.
        war.assertFileMode('WEB-INF/lib/external-1.0.jar', file("umask-reference").createFile().mode)
    }
}

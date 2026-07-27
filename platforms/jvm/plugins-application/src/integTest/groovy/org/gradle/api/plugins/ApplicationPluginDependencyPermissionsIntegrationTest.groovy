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
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.OsTestPreconditions
import spock.lang.Issue

class ApplicationPluginDependencyPermissionsIntegrationTest extends AbstractHttpDependencyResolutionTest {

    @Requires(OsTestPreconditions.NotWindows)
    @Issue("https://github.com/gradle/gradle/issues/38284")
    def "external dependency JARs installed by installDist respect the umask and are not owner-only"() {
        given:
        def module = mavenHttpRepo.module('org.gradle.test', 'external', '1.0').publish()
        module.allowAll()

        settingsFile << "rootProject.name = 'sample'"
        buildFile << """
            plugins {
                id 'application'
            }
            repositories {
                maven { url = '${mavenHttpRepo.uri}' }
            }
            dependencies {
                implementation 'org.gradle.test:external:1.0'
            }
            application {
                mainClass = 'org.gradle.test.Main'
            }
        """
        file('src/main/java/org/gradle/test/Main.java') << "package org.gradle.test; public class Main { public static void main(String[] a) {} }"

        when:
        run "installDist"

        then:
        def installedJar = file('build/install/sample/lib/external-1.0.jar')
        installedJar.assertExists()
        // Before the fix the freshly downloaded artifact was cached as rw------- and that mode was
        // carried into the distribution, making the JAR unreadable for other users.
        installedJar.permissions == file("umask-reference").createFile().permissions
    }
}

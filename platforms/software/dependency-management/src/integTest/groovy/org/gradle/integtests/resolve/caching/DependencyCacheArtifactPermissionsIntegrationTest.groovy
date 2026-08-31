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
package org.gradle.integtests.resolve.caching

import org.gradle.integtests.fixtures.AbstractHttpDependencyResolutionTest
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.OsTestPreconditions
import spock.lang.Issue

class DependencyCacheArtifactPermissionsIntegrationTest extends AbstractHttpDependencyResolutionTest {

    @Requires(OsTestPreconditions.NotWindows)
    @Issue("https://github.com/gradle/gradle/issues/38284")
    def "freshly downloaded dependency-cache artifacts respect the umask and are not owner-only"() {
        given:
        def module = mavenHttpRepo.module('org.gradle.test', 'external', '1.0').publish()
        module.allowAll()

        buildFile << """
            configurations {
                deps
            }
            repositories {
                maven { url = '${mavenHttpRepo.uri}' }
            }
            dependencies {
                deps 'org.gradle.test:external:1.0'
            }
            tasks.register('resolve') {
                def files = configurations.deps
                doLast { files.files.each { println it } }
            }
        """

        when:
        succeeds 'resolve'

        then:
        def cachedJars = []
        executer.gradleUserHomeDir.file('caches').eachFileRecurse {
            if (it.name == 'external-1.0.jar') {
                cachedJars << it
            }
        }
        cachedJars.size() == 1
        new TestFile(cachedJars[0] as File).permissions == file("umask-reference").createFile().permissions
    }
}

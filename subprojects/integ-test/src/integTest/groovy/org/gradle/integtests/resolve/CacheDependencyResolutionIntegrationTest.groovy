/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.integtests.resolve;


import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.HttpServer
import org.gradle.integtests.fixtures.IvyRepository
import org.junit.Rule

public class CacheDependencyResolutionIntegrationTest extends AbstractIntegrationSpec {
    @Rule
    public final HttpServer server = new HttpServer()

    def "setup"() {
        requireOwnUserHomeDir()
    }

    public void "cache entries are segregated between different repositories"() {
        server.start()
        given:
        def repo1 = new IvyRepository(file('ivy-repo-a'))
        def module1 = repo1.module('org.gradle', 'testproject', '1.0').publish()
        def repo2 = new IvyRepository(file('ivy-repo-b'))
        def module2 = repo2.module('org.gradle', 'testproject', '1.0').publish()
        module2.jarFile << "Some extra content"

        and:
        settingsFile << "include 'a','b'"
        buildFile << """
subprojects {
    configurations {
        test
    }
    dependencies {
        test "org.gradle:testproject:1.0"
    }
    task retrieve(type: Sync) {
        into 'build'
        from configurations.test
    }
}
project('a') {
    repositories {
        ivy { url "http://localhost:${server.port}/repo-a" }
    }
}
project('b') {
    repositories {
        ivy { url "http://localhost:${server.port}/repo-b" }
    }
}
"""

        when:
        server.expectGet('/repo-a/org.gradle/testproject/1.0/ivy-1.0.xml', module1.ivyFile)
        server.expectGet('/repo-a/org.gradle/testproject/1.0/testproject-1.0.jar', module1.jarFile)

        server.expectGet('/repo-b/org.gradle/testproject/1.0/ivy-1.0.xml.sha1', module2.sha1File(module2.ivyFile))
        server.expectGet('/repo-b/org.gradle/testproject/1.0/ivy-1.0.xml', module2.ivyFile)
        server.expectGet('/repo-b/org.gradle/testproject/1.0/testproject-1.0.jar.sha1', module2.sha1File(module2.jarFile))
        server.expectGet('/repo-b/org.gradle/testproject/1.0/testproject-1.0.jar', module2.jarFile)

        then:
        succeeds 'retrieve'

        and:
        file('a/build/testproject-1.0.jar').assertIsCopyOf(module1.jarFile)
        file('b/build/testproject-1.0.jar').assertIsCopyOf(module2.jarFile)
    }
}

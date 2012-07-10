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
package org.gradle.integtests.resolve;


public class CacheResolveIntegrationTest extends AbstractDependencyResolutionTest {

    public void "cache handles manual deletion of cached artifacts"() {
        server.start()

        given:
        def repo = ivyRepo()
        def module = repo.module('group', 'projectA', '1.2')
        module.publish()

        def cacheDir = distribution.userHomeDir.file('caches').toURI()

        and:
        buildFile << """
repositories {
    ivy { url "http://localhost:${server.port}/repo" }
}
configurations { compile }
dependencies { compile 'group:projectA:1.2' }
task listJars << {
    assert configurations.compile.collect { it.name } == ['projectA-1.2.jar']
}
task deleteCacheFiles(type: Delete) {
    delete fileTree(dir: '${cacheDir}', includes: ['**/projectA/**'])
}
"""

        and:
        server.allowGet("/repo", repo.rootDir)

        and:
        succeeds('listJars')
        succeeds('deleteCacheFiles')
        
        when:
        server.resetExpectations()
        server.expectGet('/repo/group/projectA/1.2/ivy-1.2.xml', module.ivyFile)
        server.expectGet('/repo/group/projectA/1.2/projectA-1.2.jar', module.jarFile)

        then:
        succeeds('listJars')
    }

    public void "cache entries are segregated between different repositories"() {
        server.start()
        given:
        def repo1 = ivyRepo('ivy-repo-a')
        def module1 = repo1.module('org.gradle', 'testproject', '1.0').publish()
        def repo2 = ivyRepo('ivy-repo-b')
        def module2 = repo2.module('org.gradle', 'testproject', '1.0').publishWithChangedContent()

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

        module2.expectIvyHead(server, "/repo-b")
        server.expectGet('/repo-b/org.gradle/testproject/1.0/ivy-1.0.xml.sha1', module2.sha1File(module2.ivyFile))
        server.expectGet('/repo-b/org.gradle/testproject/1.0/ivy-1.0.xml', module2.ivyFile)
        module2.expectArtifactHead(server, "/repo-b")
        server.expectGet('/repo-b/org.gradle/testproject/1.0/testproject-1.0.jar.sha1', module2.sha1File(module2.jarFile))
        server.expectGet('/repo-b/org.gradle/testproject/1.0/testproject-1.0.jar', module2.jarFile)

        then:
        succeeds 'retrieve'

        and:
        file('a/build/testproject-1.0.jar').assertIsCopyOf(module1.jarFile)
        file('b/build/testproject-1.0.jar').assertIsCopyOf(module2.jarFile)
    }

    public void "reuses a cached artifact retrieved from a different repository when sha1 matches"() {
        server.start()
        given:
        def repo1 = ivyRepo('ivy-repo-a')
        def module1 = repo1.module('org.gradle', 'testproject', '1.0').publish()
        def repo2 = ivyRepo('ivy-repo-b')
        def module2 = repo2.module('org.gradle', 'testproject', '1.0').publish()

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

        module2.expectIvyHead(server, "/repo-b")
        server.expectGet('/repo-b/org.gradle/testproject/1.0/ivy-1.0.xml.sha1', module2.sha1File(module2.ivyFile))
        module2.expectArtifactHead(server, "/repo-b")
        server.expectGet('/repo-b/org.gradle/testproject/1.0/testproject-1.0.jar.sha1', module2.sha1File(module2.jarFile))

        then:
        succeeds 'retrieve'

        and:
        file('a/build/testproject-1.0.jar').assertIsCopyOf(module1.jarFile)
        file('b/build/testproject-1.0.jar').assertIsCopyOf(module2.jarFile)
    }
}

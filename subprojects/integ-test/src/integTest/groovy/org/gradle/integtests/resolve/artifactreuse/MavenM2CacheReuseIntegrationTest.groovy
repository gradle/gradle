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
package org.gradle.integtests.resolve.artifactreuse

import org.gradle.integtests.fixture.M2Installation
import org.gradle.integtests.resolve.AbstractDependencyResolutionTest

class MavenM2CacheReuseIntegrationTest extends AbstractDependencyResolutionTest {

    def setup(){
        requireOwnUserHomeDir();
    }

    def "uses cached artifacts from maven local cache"() {
        given:
        publishAndInstallToMaven()
        server.start()

        buildFile.text = """
repositories {
    maven { url "http://localhost:${server.port}" }
}
configurations { compile }
dependencies {
    compile 'gradletest.maven.local.cache.test:foo:1.0'
}
task retrieve(type: Sync) {
    from configurations.compile
    into 'build'
}
"""

        when:
        def repoFile = file('repo')
        server.expectHead('/gradletest/maven/local/cache/test/foo/1.0/foo-1.0.pom', repoFile.file('gradletest/maven/local/cache/test/foo/1.0/foo-1.0.pom'))
        server.expectGet('/gradletest/maven/local/cache/test/foo/1.0/foo-1.0.pom.sha1', repoFile.file('gradletest/maven/local/cache/test/foo/1.0/foo-1.0.pom.sha1'))
        server.expectHead('/gradletest/maven/local/cache/test/foo/1.0/foo-1.0.jar', repoFile.file('gradletest/maven/local/cache/test/foo/1.0/foo-1.0.jar'))
        server.expectGet('/gradletest/maven/local/cache/test/foo/1.0/foo-1.0.jar.sha1', repoFile.file('gradletest/maven/local/cache/test/foo/1.0/foo-1.0.jar.sha1'))

        then:
        executer.withArguments("-Duser.home=${distribution.getUserHomeDir()}")
        run 'retrieve'
    }

    private def publishAndInstallToMaven() {
        def module1 = mavenRepo().module('gradletest.maven.local.cache.test', "foo", "1.0")
        module1.publish();
        def module2 = new M2Installation(distribution.getUserHomeDir().file(".m2")).mavenRepo().module('gradletest.maven.local.cache.test', "foo", "1.0")
        module2.publish()
    }
}

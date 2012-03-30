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

import org.gradle.integtests.fixtures.HttpServer
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.util.TestFile
import org.junit.Rule

class MavenLocalCacheReuseIntegrationTest extends AbstractIntegrationSpec {
    @Rule public final HttpServer server = new HttpServer()
    TestFile repoFile
    
    def "setup"() {
        requireOwnUserHomeDir()
        repoFile = file('repo')
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
        server.expectHead('/gradletest/maven/local/cache/test/foo/1.0/foo-1.0.pom', repoFile.file('gradletest/maven/local/cache/test/foo/1.0/foo-1.0.pom'))
        server.expectGet('/gradletest/maven/local/cache/test/foo/1.0/foo-1.0.pom.sha1', repoFile.file('gradletest/maven/local/cache/test/foo/1.0/foo-1.0.pom.sha1'))
        server.expectHead('/gradletest/maven/local/cache/test/foo/1.0/foo-1.0.jar', repoFile.file('gradletest/maven/local/cache/test/foo/1.0/foo-1.0.jar'))
        server.expectGet('/gradletest/maven/local/cache/test/foo/1.0/foo-1.0.jar.sha1', repoFile.file('gradletest/maven/local/cache/test/foo/1.0/foo-1.0.jar.sha1'))

        then:
        run 'retrieve'
    }

    private def publishAndInstallToMaven() {

        settingsFile << """
            rootProject.name = 'foo'
"""

        buildFile.text = """
apply plugin: 'java'
apply plugin: 'maven'

group = 'gradletest.maven.local.cache.test'
version = '1.0'

uploadArchives {
    repositories {
        mavenDeployer {
            repository(url: "${repoFile.toURI().toURL()}")
        }
    }
}
"""

        run 'install'
        run 'uploadArchives'
    }

}

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

import org.gradle.integtests.fixtures.AbstractHttpDependencyResolutionTest

class MavenM2CacheReuseIntegrationTest extends AbstractHttpDependencyResolutionTest {
    def "uses cached artifacts from maven local cache"() {
        given:
        def remoteModule = mavenHttpRepo.module('gradletest.maven.local.cache.test', "foo", "1.0").publish()
        m2.generateGlobalSettingsFile()
        def m2Module = m2.mavenRepo().module('gradletest.maven.local.cache.test', "foo", "1.0").publish()

        buildFile.text = """
repositories {
    maven { url = "${mavenHttpRepo.uri}" }
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
        and:
        remoteModule.pom.expectHead()
        remoteModule.pom.sha1.expectGet()
        remoteModule.artifact.expectHead()
        remoteModule.artifact.sha1.expectGet()

        when:
        using m2
        run 'retrieve'

        then:
        file('build/foo-1.0.jar').assertIsCopyOf(m2Module.artifactFile)
    }

    def "does not reuse cached artifacts from maven local cache when they are different to those in the remote repository"() {
        given:
        def remoteModule = mavenHttpRepo.module('gradletest.maven.local.cache.test', "foo", "1.0").publish()
        m2.generateGlobalSettingsFile()
        m2.mavenRepo().module('gradletest.maven.local.cache.test', "foo", "1.0").publishWithChangedContent()

        buildFile.text = """
repositories {
    maven { url = "${mavenHttpRepo.uri}" }
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
        and:
        remoteModule.pom.expectHead()
        remoteModule.pom.sha1.expectGet()
        remoteModule.pom.expectGet()
        remoteModule.artifact.expectHead()
        remoteModule.artifact.sha1.expectGet()
        remoteModule.artifact.expectGet()

        when:
        using m2
        run 'retrieve'

        then:
        file('build/foo-1.0.jar').assertIsCopyOf(remoteModule.artifactFile)
    }
}

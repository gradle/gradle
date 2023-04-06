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
package org.gradle.integtests.resolve

import org.gradle.api.internal.artifacts.ivyservice.CacheLayout
import org.gradle.cache.internal.scopes.DefaultCacheScopeMapping
import org.gradle.integtests.fixtures.AbstractHttpDependencyResolutionTest
import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache
import org.gradle.integtests.fixtures.cache.CachingIntegrationFixture

import java.nio.file.Files

class CacheResolveIntegrationTest extends AbstractHttpDependencyResolutionTest implements CachingIntegrationFixture {

    @ToBeFixedForConfigurationCache(because = "CC does not check for deleted or modified artifacts in local cache")
    void "cache handles manual deletion of cached artifacts"() {
        given:
        def module = ivyHttpRepo.module('group', 'projectA', '1.2').publish()

        def cacheDir = getUserHomeCacheDir().toURI()

        and:
        buildFile << """
repositories {
    ivy { url "${ivyHttpRepo.uri}" }
}
configurations { compile }
dependencies { compile 'group:projectA:1.2' }
task listJars {
    def files = configurations.compile
    doLast {
        assert files.collect { it.name } == ['projectA-1.2.jar']
    }
}
task deleteCacheFiles(type: Delete) {
    delete fileTree(dir: '${cacheDir}', includes: ['**/projectA/**'])
}
"""

        and:
        module.allowAll()

        and:
        succeeds('listJars')
        succeeds('deleteCacheFiles')

        when:
        server.resetExpectations()
        module.ivy.expectGet()
        module.jar.expectGet()

        then:
        succeeds('listJars')
    }

    void "cache entries are segregated between different repositories"() {
        given:
        def repo1 = ivyHttpRepo('ivy-repo-a')
        def module1 = repo1.module('org.gradle', 'testproject', '1.0').publish()
        def repo2 = ivyHttpRepo('ivy-repo-b')
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
        ivy { url "${repo1.uri}" }
    }
}
project('b') {
    repositories {
        ivy { url "${repo2.uri}" }
    }
    retrieve.dependsOn(':a:retrieve')
}
"""

        when:
        module1.ivy.expectGet()
        module1.jar.expectGet()

        module2.ivy.expectHead()
        module2.ivy.sha1.expectGet()
        module2.ivy.expectGet()
        module2.jar.expectHead()
        module2.jar.sha1.expectGet()
        module2.jar.expectGet()

        then:
        succeeds 'retrieve'

        and:
        file('a/build/testproject-1.0.jar').assertIsCopyOf(module1.jarFile)
        file('b/build/testproject-1.0.jar').assertIsCopyOf(module2.jarFile)
    }

    def 'dependency cache can be relocated'() {
        given:
        def module = ivyHttpRepo.module('group', 'projectA', '1.2').publish()

        and:
        buildFile << """
repositories {
    ivy { url "${ivyHttpRepo.uri}" }
}
configurations { compile }
dependencies { compile 'group:projectA:1.2' }
task listJars {
    def files = configurations.compile
    doLast {
        assert files.collect { it.name } == ['projectA-1.2.jar']
    }
}
"""

        and:
        module.allowAll()

        and:
        succeeds('listJars')

        when:
        server.resetExpectations()
        relocateCachesAndChangeGradleHome()

        then:
        succeeds('listJars')
    }

    def relocateCachesAndChangeGradleHome() {
        def otherHome = executer.gradleUserHomeDir.parentFile.createDir('other-home')
        def otherCacheDir = otherHome.toPath().resolve(DefaultCacheScopeMapping.GLOBAL_CACHE_DIR_NAME)
        Files.createDirectory(otherCacheDir)
        Files.move(getMetadataCacheDir().toPath(), otherCacheDir.resolve(CacheLayout.ROOT.key))
        executer.withGradleUserHomeDir(otherHome)
    }
}

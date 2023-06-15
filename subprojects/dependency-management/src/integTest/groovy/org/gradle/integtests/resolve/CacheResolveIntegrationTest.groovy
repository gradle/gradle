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
import org.gradle.internal.hash.Hashing
import org.gradle.test.fixtures.file.TestFile

import java.nio.file.Files

class CacheResolveIntegrationTest extends AbstractHttpDependencyResolutionTest implements CachingIntegrationFixture {

    @ToBeFixedForConfigurationCache
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
    doLast {
        assert configurations.compile.collect { it.name } == ['projectA-1.2.jar']
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
    doLast {
        assert configurations.compile.collect { it.name } == ['projectA-1.2.jar']
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

    def 'cannot write cache entries outside of GAV'() {
        given:
        def fakeDep = temporaryFolder.testDirectory.file('fake-repo/pwned.txt')
        fakeDep << """
Hello world!
"""
        def hash = Hashing.sha1().hashFile(fakeDep).toString()
        def hashOfBootJar = '1234' // for demo purpose
        def invalidPath = "org.spring/core/1.0/$hash/artifact-1.0./../../../../boot/2.0/$hashOfBootJar/pwned.txt"
        def invalidLocation = executer.gradleUserHomeDir.file(cachePath + invalidPath).canonicalFile

        server.allowGetOrHead("/repo/org/boot/2.0/$hashOfBootJar/pwned.txt", fakeDep)

        and:
        withValidJavaSource()
        buildWithJavaLibraryAndMavenRepoArtifactOnly()

        and:
        buildFile << """
dependencies { implementation 'org.spring:core:1.0@/../../../../boot/2.0/$hashOfBootJar/pwned.txt' }
"""

        when:
        fails('compileJava')

        then:
        failureCauseContains('is not a safe zip entry name')
        // If the build did not fail, Gradle would effectively write a file inside org.spring/boot/2.0 instead of inside org.spring/core/1.0
        // If we have the real hash of a JAR in those other coordinates, Gradle could overwrite and replace the real JAR with a malicious one
        !invalidLocation.exists()
    }

    def 'cannot write cache entries outside of dependency cache'() {
        given:
        def fakeDep = temporaryFolder.testDirectory.file('fake-repo/pwned.txt')
        fakeDep << """
Hello world!
"""
        // Code block used to verify what happens if the build succeeds
        def hash = Hashing.sha1().hashFile(fakeDep).toString()
        def invalidPath = "org.spring/../../../../../core/1.0/$hash/artifact-1.0./../../../../.ssh/pwned.txt"
        def invalidLocation = executer.gradleUserHomeDir.file(cachePath + invalidPath).canonicalFile

        server.allowGetOrHead('/repo/org/.ssh/pwned.txt', fakeDep)

        and:
        withValidJavaSource()
        buildWithJavaLibraryAndMavenRepoArtifactOnly()

        and:
        buildFile << """
dependencies { implementation 'org.spring/../../../../../:core:1.0@/../../../../.ssh/pwned.txt' }
"""

        when:
        fails('compileJava')

        then:
        failureCauseContains('is not a safe zip entry name')
        // If the build did not fail, Gradle would effectively write a file inside a folder that is a sibling to the Gradle User Home
        // If this was ~/.gradle, Gradle would have written in ~/.ssh
        !invalidLocation.exists()
    }

    def 'cannot write cache entries anywhere on disk using metadata'() {
        given:
        // Our crafty coordinates
        def pwnedDep = mavenRepo.module('org.spring/../../../../../', 'core')
        // Our abused coordinates that will see a POM request
        def abusedCoordinates = mavenHttpRepo.module('org.spring', 'core', '1.0').publish()
        // Defeat the Gradle validation that will verify metadata content match requested coordinates
        abusedCoordinates.pom.file.replace('<groupId>org.spring</groupId>', '<groupId>org.spring/../../../../../</groupId>')
        // Our test dependency that now has a crafty dependency itself
        def testDep = mavenHttpRepo.module('org.test', 'test').dependsOn(pwnedDep, type: '/../../../../.ssh/pwned.txt').publish()

        def fakeDep = temporaryFolder.testDirectory.file('fake-repo/pwned.txt')
        fakeDep << """
Hello world!
"""
        def hash = Hashing.sha1().hashFile(fakeDep).toString()
        def invalidPath = "org.spring/../../../../../core/1.0/$hash/artifact-1.0./../../../../.ssh/pwned.txt"
        def invalidLocation = executer.gradleUserHomeDir.file(cachePath + invalidPath).canonicalFile

        testDep.allowAll()
        abusedCoordinates.allowAll()
        server.allowGetOrHead('/repo/org/.ssh/pwned.txt', fakeDep)

        and:
        withValidJavaSource()
        buildWithJavaLibraryAndMavenRepo()

        and:
        buildFile << """
dependencies { implementation 'org.test:test:1.0' }
"""

        when:
        fails('compileJava')

        then:
        failureCauseContains('is not a safe zip entry name')
        // If the build did not fail, Gradle would effectively write a file inside a folder that is a sibling to the Gradle User Home
        // If this was ~/.gradle, Gradle would have written in ~/.ssh
        !invalidLocation.exists()
    }

    private String getCachePath() {
        "caches/${CacheLayout.ROOT.key}/${CacheLayout.FILE_STORE.key}/"
    }

    private void buildWithJavaLibraryAndMavenRepoArtifactOnly() {
        buildFile << """
plugins {
    id('java-library')
}

repositories {
    maven {
        url "${mavenHttpRepo.uri}"
        metadataSources {
            artifact()
        }
    }
}
"""
    }

    private void buildWithJavaLibraryAndMavenRepo() {
        buildFile << """
plugins {
    id('java-library')
}

repositories {
    maven {
        url "${mavenHttpRepo.uri}"
    }
}
"""
    }

    private TestFile withValidJavaSource() {
        temporaryFolder.testDirectory.file('src/main/java/org/test/Base.java') << """
package org.test;

public class Base {}
"""
    }

    def relocateCachesAndChangeGradleHome() {
        def otherHome = executer.gradleUserHomeDir.parentFile.createDir('other-home')
        def otherCacheDir = otherHome.toPath().resolve(DefaultCacheScopeMapping.GLOBAL_CACHE_DIR_NAME)
        Files.createDirectory(otherCacheDir)
        Files.move(getMetadataCacheDir().toPath(), otherCacheDir.resolve(CacheLayout.ROOT.key))
        executer.withGradleUserHomeDir(otherHome)
    }
}

/*
 * Copyright 2017 the original author or authors.
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

import org.gradle.integtests.fixtures.AbstractHttpDependencyResolutionTest
import org.gradle.test.fixtures.maven.MavenModule
import org.gradle.test.fixtures.maven.MavenRepository
import org.gradle.test.fixtures.server.http.MavenHttpModule

class DependencyResolveTimeoutIntegrationTest extends AbstractHttpDependencyResolutionTest {

    private static final String GROUP_ID = 'group'
    private static final String VERSION = '1.0'
    MavenHttpModule moduleA

    def setup() {
        moduleA = mavenHttpRepo.module(GROUP_ID, 'a', VERSION).publish()
    }

    def "fails single build script dependency resolution if HTTP connection exceeds timeout"() {
        buildFile << """
            buildscript {
                ${mavenRepository(mavenHttpRepo)}

                dependencies {
                    classpath '${mavenModuleCoordinates(moduleA)}'
                }
            }
        """

        when:
        moduleA.pom.expectGetBlocking()
        fails('resolve')

        then:
        assertDependencyReadTimeout(moduleA)
    }

    def "fails single application dependency resolution if HTTP connection exceeds timeout"() {
        given:
        buildFile << """
            ${mavenRepository(mavenHttpRepo)}
            ${customConfigDependencyAssignment(moduleA)}
            ${configSyncTask()}
        """

        when:
        moduleA.pom.expectGetBlocking()
        fails('resolve')

        then:
        assertDependencyReadTimeout(moduleA)
        !file('libs').isDirectory()
    }

    def "fails concurrent application dependency resolution if HTTP connection exceeds timeout"() {
        given:
        MavenHttpModule moduleB = mavenHttpRepo.module(GROUP_ID, 'b', VERSION).publish()
        MavenHttpModule moduleC = mavenHttpRepo.module(GROUP_ID, 'c', VERSION).publish()

        buildFile << """
            ${mavenRepository(mavenHttpRepo)}
            ${customConfigDependencyAssignment(moduleA, moduleB, moduleC)}
            ${configSyncTask()}
        """

        when:
        moduleA.pom.expectGetBlocking()
        moduleB.pom.expectGetBlocking()
        moduleC.pom.expectGetBlocking()
        fails('resolve', '--max-workers=3')

        then:
        assertDependencyReadTimeout(moduleA)
        assertDependencyReadTimeout(moduleB)
        assertDependencyReadTimeout(moduleC)
        !file('libs').isDirectory()
    }

    def "skips subsequent dependency resolution if HTTP connection exceeds timeout"() {
        given:
        MavenHttpModule moduleB = mavenHttpRepo.module(GROUP_ID, 'b', VERSION).publish()
        MavenHttpModule moduleC = mavenHttpRepo.module(GROUP_ID, 'c', VERSION).publish()

        buildFile << """
            ${mavenRepository(mavenHttpRepo)}
            ${customConfigDependencyAssignment(moduleA, moduleB, moduleC)}
            ${configSyncTask()}
        """

        when:
        moduleA.pom.expectGetBlocking()
        fails('resolve', '--max-workers=1')

        then:
        assertDependencyReadTimeout(moduleA)
        assertDependencySkipped(moduleB)
        assertDependencySkipped(moduleC)
        !file('libs').isDirectory()
    }

    private String mavenRepository(MavenRepository repo) {
        """
            repositories {
                maven { url "${repo.uri}"}
            }
        """
    }

    private String customConfigDependencyAssignment(MavenHttpModule... modules) {
        """
            configurations {
                deps
            }
            
            dependencies {
                deps ${modules.collect { "'${mavenModuleCoordinates(it)}'" }.join(', ')}
            }
        """
    }

    private String configSyncTask() {
        """
            task resolve(type: Sync) {
                from configurations.deps
                into "\$buildDir/libs"
            }
        """
    }

    private void assertDependencyReadTimeout(MavenModule module) {
        failure.error.contains("""> Could not resolve ${mavenModuleCoordinates(module)}.
   > Could not get resource '${mavenHttpRepo.uri.toString()}/${mavenModuleRepositoryPath(module)}.pom'.
      > Could not GET '${mavenHttpRepo.uri.toString()}/${mavenModuleRepositoryPath(module)}.pom'.
         > Read timed out""")
    }

    private void assertDependencySkipped(MavenModule module) {
        failure.error.contains("""> Could not resolve ${mavenModuleCoordinates(module)}.
  Required by:
      project :
   > Skipped due to earlier error""")
    }

    private String mavenModuleCoordinates(MavenModule module) {
        "$module.groupId:$module.artifactId:$module.version"
    }

    private String mavenModuleRepositoryPath(MavenModule module) {
        "$module.groupId/$module.artifactId/$module.artifactId-$module.version"
    }
}

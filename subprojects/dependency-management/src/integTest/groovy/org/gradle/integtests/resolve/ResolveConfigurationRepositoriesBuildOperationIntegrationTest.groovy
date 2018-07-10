/*
 * Copyright 2018 the original author or authors.
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

import com.google.common.collect.Maps
import org.gradle.api.internal.artifacts.configurations.ResolveConfigurationDependenciesBuildOperationType
import org.gradle.integtests.fixtures.AbstractHttpDependencyResolutionTest
import org.gradle.integtests.fixtures.BuildOperationNotificationsFixture
import org.gradle.integtests.fixtures.BuildOperationsFixture
import spock.lang.Unroll

class ResolveConfigurationRepositoriesBuildOperationIntegrationTest extends AbstractHttpDependencyResolutionTest {

    def operations = new BuildOperationsFixture(executer, temporaryFolder)

    @SuppressWarnings("GroovyUnusedDeclaration")
    def operationNotificationsFixture = new BuildOperationNotificationsFixture(executer, temporaryFolder)

    @Unroll
    def "repositories used when resolving configurations are exposed via build operation, and are stable (repo: #repo)"() {
        setup:
        m2.generateUserSettingsFile(m2.mavenRepo())
        using m2
        buildFile << """                
            apply plugin: 'java'
            ${repoBlock.replaceAll('<<URL>>', getMavenHttpRepo().uri.toString())}
            task resolve { doLast { configurations.compile.resolve() } }
        """

        when:
        succeeds 'resolve'

        then:
        def op = operations.first(ResolveConfigurationDependenciesBuildOperationType)
        op.details.configurationName == 'compile'
        op.details.projectPath == ":"
        op.details.buildPath == ":"
        def repos = op.details.repositories
        repos.size() == 1
        stripRepoId(repos[0]) == augmentMapWithProperties(expectedRepo, [
            'url': expectedRepo.name == 'MavenLocal' ? m2.mavenRepo().uri.toASCIIString() : getMavenHttpRepo().uri.toASCIIString(),
            'dirs': [buildFile.parentFile.file('fooDir').absolutePath]
        ])

        when:
        succeeds 'resolve'

        then: // stable
        repos == operations.first(ResolveConfigurationDependenciesBuildOperationType).details.repositories

        where:
        repo                   | repoBlock                     | expectedRepo
        'maven'                | mavenRepoBlock()              | expectedMavenRepo()
        'ivy'                  | ivyRepoBlock()                | expectedIvyRepo()
        'flat-dir'             | flatDirRepoBlock()            | expectedFlatDirRepo()
        'local maven'          | mavenLocalRepoBlock()         | expectedMavenLocalRepo()
        'maven central'        | mavenCentralRepoBlock()       | expectedMavenCentralRepo()
        'jcenter'              | jcenterRepoBlock()            | expectedJcenterRepo()
        'google'               | googleRepoBlock()             | expectedGoogleRepo()
        'gradle plugin portal' | gradlePluginPortalRepoBlock() | expectedGradlePluginPortalRepo()
    }

    private static String mavenRepoBlock() {
        "repositories { maven { url '<<URL>>' } }"
    }

    private static Map expectedMavenRepo() {
        [
            name: 'maven',
            type: 'maven',
            properties: [
                url: null,
                artifactUrls: [],
                metadataSources: ['mavenPom', 'artifact']
            ]
        ]
    }

    private static String ivyRepoBlock() {
        "repositories { ivy { url '<<URL>>' } }"
    }

    private static Map expectedIvyRepo() {
        [
            name: 'ivy',
            type: 'ivy',
            properties: [
                url: null,
                ivyPatterns: [],
                artifactPattern: [],
                metadataSources: ['ivyDescriptor', 'artifact']
            ]
        ]
    }

    private static String flatDirRepoBlock() {
        "repositories { flatDir { dirs 'fooDir' } }"
    }

    private static Map expectedFlatDirRepo() {
        [
            name: 'flatDir',
            type: 'flat_dir',
            properties: [
                dirs: null
            ]
        ]
    }

    private static String mavenLocalRepoBlock() {
        "repositories { mavenLocal() }"
    }

    private static Map expectedMavenLocalRepo() {
        [
            name: 'MavenLocal',
            type: 'maven',
            properties: [
                url: null,
                artifactUrls: [],
                metadataSources: ['mavenPom', 'artifact']
            ]
        ]
    }

    private static String mavenCentralRepoBlock() {
        "repositories { mavenCentral() }"
    }


    private static Map expectedMavenCentralRepo() {
        [
            name: 'MavenRepo',
            type: 'maven',
            properties: [
                url: 'https://repo.maven.apache.org/maven2/',
                artifactUrls: [],
                metadataSources: ['mavenPom', 'artifact']
            ]
        ]
    }

    private static String jcenterRepoBlock() {
        "repositories { jcenter() }"
    }

    private static Map expectedJcenterRepo() {
        [
            name: 'BintrayJCenter',
            type: 'maven',
            properties: [
                url: 'https://jcenter.bintray.com/',
                artifactUrls: [],
                metadataSources: ['mavenPom', 'artifact']
            ]
        ]
    }

    private static String googleRepoBlock() {
        "repositories { google() }"
    }

    private static Map expectedGoogleRepo() {
        [
            name: 'Google',
            type: 'maven',
            properties: [
                url: 'https://dl.google.com/dl/android/maven2/',
                artifactUrls: [],
                metadataSources: ['mavenPom', 'artifact']
            ]
        ]
    }

    private static String gradlePluginPortalRepoBlock() {
        "repositories { gradlePluginPortal() }"
    }

    private static Map expectedGradlePluginPortalRepo() {
        [
            name: 'Gradle Central Plugin Repository',
            type: 'maven',
            properties: [
                url: 'https://plugins.gradle.org/m2',
                artifactUrls: [],
                metadataSources: ['mavenPom', 'artifact']
            ]
        ]
    }

    private static Map<String, ?> stripRepoId(Map<String, ?> map) {
        assert map.containsKey('repositoryId')
        def returnedMap = Maps.newHashMap(map)
        returnedMap.remove('repositoryId')
        returnedMap
    }

    private static Map<String, ?> augmentMapWithProperties(Map<String, ?> map, Map<String, ?> replacements) {
        assert map.containsKey('properties')
        replacements.each { k, v ->
            if (map.get('properties').containsKey(k) && map.get('properties').get(k) == null) {
                map.get('properties').put(k, v)
            }
        }
        map
    }

}

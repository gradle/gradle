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

import org.gradle.api.internal.artifacts.configurations.ResolveConfigurationDependenciesBuildOperationType
import org.gradle.integtests.fixtures.AbstractHttpDependencyResolutionTest
import org.gradle.integtests.fixtures.BuildOperationNotificationsFixture
import org.gradle.integtests.fixtures.BuildOperationsFixture
import org.gradle.test.fixtures.plugin.PluginBuilder

class ResolveConfigurationRepositoriesBuildOperationIntegrationTest extends AbstractHttpDependencyResolutionTest {

    def operations = new BuildOperationsFixture(executer, temporaryFolder)

    @SuppressWarnings("GroovyUnusedDeclaration")
    def operationNotificationsFixture = new BuildOperationNotificationsFixture(executer, temporaryFolder)

    def "repositories used when resolving project configurations are exposed via build operation (repo: #repo)"() {
        setup:
        executer.beforeExecute { executer.withPluginRepositoryMirrorDisabled() }
        m2.generateUserSettingsFile(m2.mavenRepo())
        using m2
        buildFile << """
            apply plugin: 'java'
            ${repoBlock.replaceAll('<<URL>>', mavenHttpRepo.uri.toASCIIString())}
            task resolve {
                def files = configurations.compileClasspath
                doLast { files.files }
            }
        """
        if (deprecationWarning) {
            executer.expectDocumentedDeprecationWarning(deprecationWarning)
        }

        when:
        succeeds 'resolve'

        then:
        def op = operations.first(ResolveConfigurationDependenciesBuildOperationType)
        op.details.configurationName == 'compileClasspath'
        op.details.projectPath == ":"
        op.details.buildPath == ":"
        def repos = op.details.repositories
        repos.size() == 1
        def repo1 = repos.first()
        repo1.remove('id')
        repo1 == augmentMapWithProperties(expectedRepo, [
            URL: expectedRepo.name == 'MavenLocal' ? m2.mavenRepo().uri.toASCIIString() : mavenHttpRepo.uri.toASCIIString(),
            DIRS: [buildFile.parentFile.file('fooDir').absolutePath]
        ])

        where:
        repo                   | repoBlock                     | expectedRepo                     | deprecationWarning
        'maven'                | mavenRepoBlock()              | expectedMavenRepo()              | null
        'ivy'                  | ivyRepoBlock()                | expectedIvyRepo()                | null
        'ivy-no-url'           | ivyRepoNoUrlBlock()           | expectedIvyRepoNoUrl()           | null
        'flat-dir'             | flatDirRepoBlock()            | expectedFlatDirRepo()            | null
        'local maven'          | mavenLocalRepoBlock()         | expectedMavenLocalRepo()         | null
        'maven central'        | mavenCentralRepoBlock()       | expectedMavenCentralRepo()       | null
        'jcenter'              | jcenterRepoBlock()            | expectedJcenterRepo()            | "The RepositoryHandler.jcenter() method has been deprecated. This is scheduled to be removed in Gradle 9.0. JFrog announced JCenter's sunset in February 2021. Use mavenCentral() instead. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_6.html#jcenter_deprecation"
        'google'               | googleRepoBlock()             | expectedGoogleRepo()             | null
        'gradle plugin portal' | gradlePluginPortalRepoBlock() | expectedGradlePluginPortalRepo() | null
    }

    def "repositories used in buildscript blocks are exposed via build operation"() {
        setup:
        def module = mavenHttpRepo.module('org', 'foo')
        module.pom.expectGetBroken()
        buildFile << """
            buildscript {
                repositories { maven { url '${mavenHttpRepo.uri}' } }
                dependencies { classpath 'org:foo:1.0' }
            }
        """

        when:
        fails 'help'

        then:
        def op = operations.first(ResolveConfigurationDependenciesBuildOperationType)
        op.details.configurationName == 'classpath'
        op.details.projectPath == null
        op.details.buildPath == ':'
        def repos = op.details.repositories
        repos.size() == 1
        with(repos[0]) {
            name == 'maven'
            type == 'MAVEN'
            properties == [
                ARTIFACT_URLS: [],
                AUTHENTICATED: false,
                AUTHENTICATION_SCHEMES: [],
                URL: getMavenHttpRepo().uri.toString(),
                METADATA_SOURCES: ['mavenPom']
            ]
        }
    }

    def "repositories used in plugins blocks are exposed via build operation"() {
        setup:
        def module = mavenHttpRepo.module('my-plugin', 'my-plugin.gradle.plugin')
        module.pom.expectGetBroken()
        settingsFile << """
        pluginManagement {
            repositories { maven { url '${mavenHttpRepo.uri}' } }
        }
        """
        buildFile << """
            plugins { id 'my-plugin' version '1.0' }
        """

        when:
        fails 'help'

        then:
        def op = operations.first(ResolveConfigurationDependenciesBuildOperationType)
        op.details.configurationName == 'detachedConfiguration1'
        op.details.projectPath == null
        op.details.buildPath == ':'
        def repos = op.details.repositories
        repos.size() == 1
        with(repos[0]) {
            name == 'maven'
            type == 'MAVEN'
            properties == [
                ARTIFACT_URLS: [],
                AUTHENTICATED: false,
                AUTHENTICATION_SCHEMES: [],
                METADATA_SOURCES: ['mavenPom'],
                URL: getMavenHttpRepo().uri.toString(),
            ]
        }
    }

    def "repositories shared across repository container types are stable"() {
        setup:
        publishTestPlugin('plugin', 'org.example.plugin', 'org.example.plugin:plugin:1.0')
        publishTestPlugin('plugin2', 'org.example.plugin2', 'org.example.plugin:plugin2:1.0')
        settingsFile << """
        pluginManagement {
            repositories { maven { url = '$mavenRepo.uri' } }
        }
        """
        buildFile << """
            buildscript {
                repositories { maven { url = '$mavenRepo.uri' } }
                dependencies { classpath "org.example.plugin:plugin2:1.0" }
            }
            plugins {
                id 'org.example.plugin' version '1.0'
                id 'java'
            }
            apply plugin: 'org.example.plugin2'
            repositories { maven { url = '$mavenRepo.uri' } }
            task resolve {
                def files = configurations.compileClasspath
                doLast { files.files }
            }
        """

        when:
        succeeds 'resolve'

        then:
        def ops = operations.all(ResolveConfigurationDependenciesBuildOperationType)
        ops.size() == 3
        ops.details.repositories.flatten().unique(false).size() == 1
    }

    def "repositories shared across projects are stable"() {
        setup:
        createDirs("child")
        settingsFile << """
            include 'child'
        """
        buildFile << """
            allprojects {
                apply plugin: 'java'
                ${mavenCentralRepoBlock()}
                task resolve {
                    def files = configurations.compileClasspath
                    doLast { files.files }
                }
            }
        """

        when:
        succeeds 'resolve'

        then:
        def ops = operations.all(ResolveConfigurationDependenciesBuildOperationType)
        ops.details.repositories.size() == 2
        ops.details.repositories.unique(false).size() == 1
    }

    def "maven repository attributes are stored"() {
        setup:
        buildFile << """
            apply plugin: 'java'
            repositories {
                maven {
                    name = 'custom repo'
                    url = 'http://foo.com'
                    artifactUrls 'http://foo.com/artifacts1'
                    metadataSources { gradleMetadata(); artifact() }
                    credentials {
                        username 'user'
                        password 'pass'
                    }
                    authentication {
                        digest(DigestAuthentication)
                    }
                }
            }
            task resolve {
                def files = configurations.compileClasspath
                doLast { files.files }
            }
        """

        when:
        succeeds 'resolve'

        then:
        def ops = operations.first(ResolveConfigurationDependenciesBuildOperationType)
        ops.details.repositories.size() == 1
        def repo = ops.details.repositories[0]
        with(repo) {
            name == 'custom repo'
            type == 'MAVEN'
            properties.size() == 5
            properties.URL == 'http://foo.com'
            properties.ARTIFACT_URLS == ['http://foo.com/artifacts1']
            properties.METADATA_SOURCES == ['gradleMetadata', 'artifact']
            properties.AUTHENTICATED == true
            properties.'AUTHENTICATION_SCHEMES' == ['DigestAuthentication']
        }
    }

    def "maven repository must define a URL property"() {
        setup:
        buildFile << """
            apply plugin: 'java'
            repositories {
                maven {
                    name = 'custom repo'
                }
            }
            task resolve {
                def files = configurations.compileClasspath
                doLast { files.files }
            }
        """

        when:
        def result = fails 'resolve'

        then:
        result.assertHasCause 'You must specify a URL for a Maven repository.'
        operations.none(ResolveConfigurationDependenciesBuildOperationType)
    }

    def "ivy repository attributes are stored"() {
        setup:
        buildFile << """
            apply plugin: 'java'
            repositories {
                ivy {
                    name = 'custom repo'
                    url 'http://myCompanyBucket/ivyrepo'
                    artifactPattern 'http://myCompanyBucket/ivyrepo/[organisation]/[module]/[artifact]-[revision]'
                    ivyPattern 'http://myCompanyBucket/ivyrepo/[organisation]/[module]/ivy-[revision].xml'
                    patternLayout {
                        artifact '[module]/[organisation]/[revision]/[artifact]'
                        artifact '3rd-party/[module]/[organisation]/[revision]/[artifact]'
                        ivy '[module]/[organisation]/[revision]/ivy.xml'
                        m2compatible = true
                    }
                    metadataSources { gradleMetadata(); ivyDescriptor(); artifact() }
                    credentials {
                        username 'user'
                        password 'pass'
                    }
                    authentication {
                        basic(BasicAuthentication)
                    }
                }
            }
            task resolve {
                def files = configurations.compileClasspath
                doLast { files.files }
            }
        """

        when:
        succeeds 'resolve'

        then:
        def ops = operations.first(ResolveConfigurationDependenciesBuildOperationType)
        ops.details.repositories.size() == 1
        def repo = ops.details.repositories[0]
        with(repo) {
            name == 'custom repo'
            type == 'IVY'
            properties.size() == 8
            properties.URL == 'http://myCompanyBucket/ivyrepo'
            properties.LAYOUT_TYPE == 'Pattern'
            properties.M2_COMPATIBLE == true
            properties.IVY_PATTERNS == [
                '[module]/[organisation]/[revision]/ivy.xml',
                'http://myCompanyBucket/ivyrepo/[organisation]/[module]/ivy-[revision].xml'
            ]
            properties.ARTIFACT_PATTERNS == [
                '[module]/[organisation]/[revision]/[artifact]',
                '3rd-party/[module]/[organisation]/[revision]/[artifact]',
                'http://myCompanyBucket/ivyrepo/[organisation]/[module]/[artifact]-[revision]'
            ]
            properties.METADATA_SOURCES == ['gradleMetadata', 'ivyDescriptor', 'artifact']
            properties.AUTHENTICATED == true
            properties.AUTHENTICATION_SCHEMES == ['BasicAuthentication']
        }
    }

    def "ivy repository must define a URL property, or at least one artifact pattern"() {
        setup:
        buildFile << """
            apply plugin: 'java'
            repositories {
                ivy {
                    name = 'custom repo'
                    ${definition}
                }
            }
            task resolve {
                def files = configurations.compileClasspath
                doLast { files.files }
            }
        """

        when:
        if (success) {
            succeeds 'resolve'
        } else {
            def result = fails 'resolve'
        }


        then:
        if (success) {
            def ops = operations.first(ResolveConfigurationDependenciesBuildOperationType)
            ops.details.repositories.size() == 1
            def repo = ops.details.repositories[0]
            with(repo) {
                name == 'custom repo'
                type == 'IVY'
                if (artifactPattern) {
                    properties.size() == 7
                    properties.ARTIFACT_PATTERNS == ['[organisation]/[module]/[revision]/[artifact]-[revision](-[classifier])(.[ext])', 'foo']
                    properties.IVY_PATTERNS == ['[organisation]/[module]/[revision]/ivy-[revision].xml']
                    properties.LAYOUT_TYPE == 'Gradle'
                    properties.M2_COMPATIBLE == false
                    properties.METADATA_SOURCES == ['ivyDescriptor']
                    properties.AUTHENTICATED == false
                    properties.'AUTHENTICATION_SCHEMES' == []
                } else {
                    properties.size() == 8
                    properties.URL == 'http://foo.com'
                    properties.ARTIFACT_PATTERNS == ['[organisation]/[module]/[revision]/[artifact]-[revision](-[classifier])(.[ext])']
                    properties.IVY_PATTERNS == ['[organisation]/[module]/[revision]/ivy-[revision].xml']
                    properties.LAYOUT_TYPE == 'Gradle'
                    properties.M2_COMPATIBLE == false
                    properties.METADATA_SOURCES == ['ivyDescriptor']
                    properties.AUTHENTICATED == false
                    properties.'AUTHENTICATION_SCHEMES' == []
                }
            }
        } else {
            result.assertHasCause "You must specify a base url or at least one artifact pattern for the Ivy repository 'custom repo'."
            operations.none(ResolveConfigurationDependenciesBuildOperationType)
        }

        where:
        definition               | success | artifactPattern
        "url = 'http://foo.com'" | true    | false
        "artifactPattern 'foo'"  | true    | true
        ''                       | false   | false
    }

    def "flat-dir repository attributes are stored"() {
        setup:
        buildFile << """
            apply plugin: 'java'
            repositories {
                flatDir {
                    name = 'custom repo'
                    dirs 'lib1', 'lib2'
                }
            }
            task resolve {
                def files = configurations.compileClasspath
                doLast { files.files }
            }
        """

        when:
        succeeds 'resolve'

        then:
        def ops = operations.first(ResolveConfigurationDependenciesBuildOperationType)
        ops.details.repositories.size() == 1
        def repo = ops.details.repositories[0]
        with(repo) {
            name == 'custom repo'
            type == 'FLAT_DIR'
            properties.size() == 1
            properties.DIRS.sort() == [file('lib1').absolutePath, file('lib2').absolutePath].sort()
        }
    }

    private static String mavenRepoBlock() {
        "repositories { maven { url '<<URL>>' } }"
    }

    private static Map expectedMavenRepo() {
        [
            name: 'maven',
            type: 'MAVEN',
            properties: [
                ARTIFACT_URLS: [],
                METADATA_SOURCES: ['mavenPom'],
                AUTHENTICATED: false,
                AUTHENTICATION_SCHEMES: [],
                URL: null,
            ]
        ]
    }

    private static String mavenRepoNoUrlBlock() {
        "repositories { maven { artifactUrls 'http://artifactUrl' } }"
    }

    private static Map expectedMavenRepoNoUrl() {
        [
            id: 'maven',
            name: 'maven',
            type: 'MAVEN',
            properties: [
                ARTIFACT_URLS: ['http://artifactUrl'],
                METADATA_SOURCES: ['mavenPom'],
                AUTHENTICATED: false,
                AUTHENTICATION_SCHEMES: []
            ]
        ]
    }

    private static String ivyRepoBlock() {
        "repositories { ivy { url '<<URL>>' } }"
    }

    private static Map expectedIvyRepo() {
        [
            name: 'ivy',
            type: 'IVY',
            properties: [
                ARTIFACT_PATTERNS: ['[organisation]/[module]/[revision]/[artifact]-[revision](-[classifier])(.[ext])'],
                AUTHENTICATED: false,
                AUTHENTICATION_SCHEMES: [],
                IVY_PATTERNS: ['[organisation]/[module]/[revision]/ivy-[revision].xml'],
                LAYOUT_TYPE: 'Gradle',
                M2_COMPATIBLE: false,
                METADATA_SOURCES: ['ivyDescriptor'],
                URL: null
            ]
        ]
    }

    private static String ivyRepoNoUrlBlock() {
        "repositories { ivy { artifactPattern 'artifactPattern' } }"
    }

    private static Map expectedIvyRepoNoUrl() {
        [
            name: 'ivy',
            type: 'IVY',
            properties: [
                ARTIFACT_PATTERNS: [
                    '[organisation]/[module]/[revision]/[artifact]-[revision](-[classifier])(.[ext])',
                    'artifactPattern'
                ],
                AUTHENTICATED: false,
                AUTHENTICATION_SCHEMES: [],
                IVY_PATTERNS: ['[organisation]/[module]/[revision]/ivy-[revision].xml'],
                LAYOUT_TYPE: 'Gradle',
                M2_COMPATIBLE: false,
                METADATA_SOURCES: ['ivyDescriptor']
            ]
        ]
    }

    private static String flatDirRepoBlock() {
        "repositories { flatDir { dirs 'fooDir' } }"
    }

    private static Map expectedFlatDirRepo() {
        [
            name: 'flatDir',
            type: 'FLAT_DIR',
            properties: [
                DIRS: null
            ]
        ]
    }

    private static String mavenLocalRepoBlock() {
        "repositories { mavenLocal() }"
    }

    private static Map expectedMavenLocalRepo() {
        [
            name: 'MavenLocal',
            type: 'MAVEN',
            properties: [
                ARTIFACT_URLS: [],
                AUTHENTICATED: false,
                AUTHENTICATION_SCHEMES: [],
                METADATA_SOURCES: ['mavenPom'],
                URL: null,
            ]
        ]
    }

    private static String mavenCentralRepoBlock() {
        "repositories { mavenCentral() }"
    }

    private static Map expectedMavenCentralRepo() {
        [
            name: 'MavenRepo',
            type: 'MAVEN',
            properties: [
                ARTIFACT_URLS: [],
                AUTHENTICATED: false,
                AUTHENTICATION_SCHEMES: [],
                METADATA_SOURCES: ['mavenPom'],
                URL: 'https://repo.maven.apache.org/maven2/',
            ]
        ]
    }

    private static String jcenterRepoBlock() {
        "repositories { jcenter() }"
    }

    private static Map expectedJcenterRepo() {
        [
            name: 'BintrayJCenter',
            type: 'MAVEN',
            properties: [
                ARTIFACT_URLS: [],
                METADATA_SOURCES: ['mavenPom'],
                AUTHENTICATED: false,
                AUTHENTICATION_SCHEMES: [],
                URL: 'https://jcenter.bintray.com/',
            ]
        ]
    }

    private static String googleRepoBlock() {
        "repositories { google() }"
    }

    private static Map expectedGoogleRepo() {
        [
            name: 'Google',
            type: 'MAVEN',
            properties: [
                ARTIFACT_URLS: [],
                AUTHENTICATED: false,
                AUTHENTICATION_SCHEMES: [],
                METADATA_SOURCES: ['mavenPom'],
                URL: 'https://dl.google.com/dl/android/maven2/',
            ]
        ]
    }

    private static String gradlePluginPortalRepoBlock() {
        "repositories { gradlePluginPortal() }"
    }

    private static Map expectedGradlePluginPortalRepo() {
        [
            name: 'Gradle Central Plugin Repository',
            type: 'MAVEN',
            properties: [
                ARTIFACT_URLS: [],
                AUTHENTICATED: false,
                AUTHENTICATION_SCHEMES: [],
                METADATA_SOURCES: ['mavenPom'],
                URL: 'https://plugins.gradle.org/m2',
            ]
        ]
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

    private publishTestPlugin(String path, String id, String coordinates) {
        def pluginBuilder = new PluginBuilder(testDirectory.file(path))
        def message = 'from plugin'
        def taskName = 'pluginTask'
        pluginBuilder.addPluginWithPrintlnTask(taskName, message, id)
        pluginBuilder.publishAs(coordinates, mavenRepo, executer)
    }

}

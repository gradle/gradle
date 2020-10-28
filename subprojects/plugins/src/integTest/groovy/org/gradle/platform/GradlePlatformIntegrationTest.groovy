/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.platform

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class GradlePlatformIntegrationTest extends AbstractIntegrationSpec implements GradlePlatformSupport {
    def setup() {
        settingsFile << """
            rootProject.name = 'test'
        """
        buildFile << """
            plugins {
                id 'gradle-platform'
            }

            group = 'org.gradle'
            version = '1.0'
        """
    }

    def "can generate a Gradle platform file"() {
        withSamplePlatform()

        when:
        succeeds ':generatePlatformToml'

        then:
        expectPlatformContents 'expected1'
    }

    def "can publish a Gradle platform"() {
        withSamplePlatform()
        withPublishing()

        when:
        succeeds ':publish'

        then:
        executedAndNotSkipped ':generatePlatformToml',
            ':generateMetadataFileForMavenPublication',
            ':generatePomFileForMavenPublication',
            ':publishMavenPublicationToMavenRepository'
        def module = mavenRepo.module("org.gradle", "test", "1.0")
            .withModuleMetadata()
        module.assertPublished()
        def metadata = module.parsedModuleMetadata
        metadata.variant("gradlePlatformElements") {
            noMoreDependencies()
            assert attributes == [
                'org.gradle.category': 'platform',
                'org.gradle.usage': 'gradle-recommendations'
            ]
            assert files.name == ['test-1.0.toml']
        }
    }

    def "can generate a Gradle platform file from a dependencies configuration"() {
        buildFile << """
            dependencies {
                gradlePlatform 'org:foo:1.0'
                gradlePlatform('org:bar') {
                    version {
                        strictly '1.5'
                    }
                }
            }
        """

        when:
        succeeds ':generatePlatformToml'

        then:
        expectPlatformContents 'expected2'
    }

    def "can generate a Gradle platform file from a dependencies configuration and the extension"() {
        buildFile << """
            gradlePlatform {
                dependenciesModel {
                    bundle('my', ['foo', 'bar'])
                }
            }
            dependencies {
                gradlePlatform 'org:foo:1.0'
                gradlePlatform('org:bar') {
                    version {
                        strictly '1.5'
                    }
                }
            }
        """

        when:
        succeeds ':generatePlatformToml'

        then:
        expectPlatformContents 'expected3'
    }

    def "reasonable error message if there's a name clash between two dependencies"() {
        buildFile << """
            dependencies {
                gradlePlatform 'org1:foo:1.0'
                gradlePlatform 'org2:foo:1.0'
            }
        """

        when:
        fails ':generatePlatformToml'

        then:
        failure.assertHasCause "A dependency with alias 'foo' already exists for module 'org2:foo'. Please configure an explicit alias for this dependency."
    }

    def "can declare a different alias in case of name clash"() {
        buildFile << """
            gradlePlatform {
               configureExplicitAlias 'foo2', 'org2', 'foo'
            }
            dependencies {
                gradlePlatform 'org1:foo:1.0'
                gradlePlatform 'org2:foo:1.0'
            }
        """

        when:
        succeeds ':generatePlatformToml'

        then:
        expectPlatformContents 'expected4'
    }

    def "can declare a explicit alias without name clash"() {
        buildFile << """
            gradlePlatform {
               configureExplicitAlias 'other', 'org', 'bar'
            }
            dependencies {
                gradlePlatform 'org:foo:1.0'
                gradlePlatform 'org:bar:1.0'
            }
        """

        when:
        succeeds ':generatePlatformToml'

        then:
        expectPlatformContents 'expected5'
    }

    def "can use either dependencies or constraints"() {
        buildFile << """
            dependencies {
                gradlePlatform 'org:foo:1.0'
                constraints {
                    gradlePlatform('org:bar') {
                        version {
                            require '1.2'
                        }
                    }
                }
            }
        """

        when:
        succeeds ':generatePlatformToml'

        then:
        expectPlatformContents 'expected6'
    }

    def "can detect name clash between dependencies and constraints"() {
        buildFile << """
            dependencies {
                gradlePlatform 'org:foo:1.0'
                constraints {
                    gradlePlatform('org2:foo') {
                        version {
                            require '1.2'
                        }
                    }
                }
            }
        """

        when:
        fails ':generatePlatformToml'

        then:
        failure.assertHasCause "A dependency with alias 'foo' already exists for module 'org2:foo'. Please configure an explicit alias for this dependency."
    }

    def "can fix name clash between dependencies and constraints"() {
        buildFile << """
            gradlePlatform {
                configureExplicitAlias 'foo2', 'org2', 'foo'
            }
            dependencies {
                gradlePlatform 'org:foo:1.0'
                constraints {
                    gradlePlatform('org2:foo') {
                        version {
                            require '1.2'
                        }
                    }
                }
            }
        """

        when:
        succeeds ':generatePlatformToml'

        then:
        expectPlatformContents 'expected7'
    }

    def "can mix plugins, dependencies, constraints and model to create a platform"() {
        buildFile << """
            gradlePlatform {
                configureExplicitAlias 'foo2', 'org', 'foo'
                dependenciesModel {
                    alias('foo', 'org:from-model:1.0')
                    bundle('my', ['foo', 'foo2', 'from-script'])
                }
                plugins {
                    id('my.plugin') version '1.7'
                }
            }
            dependencies {
                gradlePlatform 'org:from-script:1.0'
                constraints {
                    gradlePlatform('org:foo') {
                        version {
                            require '1.2'
                        }
                    }
                }
            }
        """

        when:
        succeeds ':generatePlatformToml'

        then:
        expectPlatformContents 'expected8'
    }

    def "can publish a Java platform as a Gradle platform"() {
        buildFile << """apply plugin:'java-platform'
"""
        withPublishing 'javaPlatform'

        buildFile << """
            dependencies {
                constraints {
                    api 'org:api-dep:1.0'
                    runtime 'org:runtime-dep:1.4'
                }
            }
        """

        when:
        succeeds ':publish'

        then:
        executedAndNotSkipped ':generatePlatformToml',
            ':generateMetadataFileForMavenPublication',
            ':generatePomFileForMavenPublication',
            ':publishMavenPublicationToMavenRepository'
        def module = mavenRepo.module("org.gradle", "test", "1.0")
            .withModuleMetadata()
        module.assertPublished()
        def metadata = module.parsedModuleMetadata
        metadata.variant("gradlePlatformElements") {
            noMoreDependencies()
            assert attributes == [
                'org.gradle.category': 'platform',
                'org.gradle.usage': 'gradle-recommendations'
            ]
            assert files.name == ['test-1.0.toml']
        }

        and:
        expectPlatformContents 'expected9'
    }

    private void withSamplePlatform() {
        buildFile << """
            gradlePlatform {
                dependenciesModel {
                    alias("my-lib", "org:foo:1.0")
                    alias("junit4", "junit", "junit") {
                        require "[4.13.1, 5["
                        prefer "4.13.1"
                    }
                    version("lib", "1.1")
                    aliasWithVersionRef("other", "org", "bar", "lib")
                    bundle("test", ["my-lib", "junit4"])
                }
                plugins {
                    id("my.awesome.plugin") version "1.5"
                }
            }
        """
    }

    private void withPublishing(String component = 'gradlePlatform') {
        buildFile << """
            apply plugin: 'maven-publish'

            publishing {
                repositories {
                    maven {
                        url "$mavenRepo.uri"
                    }
                }
                publications {
                    maven(MavenPublication) {
                        from components.$component
                    }
                }
            }
        """
    }
}

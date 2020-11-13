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

package org.gradle.catalog

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache

class VersionCatalogIntegrationTest extends AbstractIntegrationSpec implements VersionCatalogSupport {
    def setup() {
        settingsFile << """
            rootProject.name = 'test'
        """
        buildFile << """
            plugins {
                id 'version-catalog'
            }

            group = 'org.gradle'
            version = '1.0'
        """
    }

    def "can generate a Gradle platform file"() {
        withSampleCatalog()

        when:
        succeeds ':generateCatalogAsToml'

        then:
        expectPlatformContents 'expected1'
    }

    def "can publish a Gradle platform"() {
        withSampleCatalog()
        withPublishing()

        when:
        succeeds ':publish'

        then:
        executedAndNotSkipped ':generateCatalogAsToml',
            ':generateMetadataFileForMavenPublication',
            ':generatePomFileForMavenPublication',
            ':publishMavenPublicationToMavenRepository'
        def module = mavenRepo.module("org.gradle", "test", "1.0")
            .withModuleMetadata()
        module.assertPublished()
        def metadata = module.parsedModuleMetadata
        metadata.variant("versionCatalogElements") {
            noMoreDependencies()
            assert attributes == [
                'org.gradle.category': 'platform',
                'org.gradle.usage': 'version-catalog'
            ]
            assert files.name == ['test-1.0.toml']
        }
    }

    def "can generate a Gradle platform file from a dependencies configuration"() {
        buildFile << """
            dependencies {
                versionCatalog 'org:foo:1.0'
                versionCatalog('org:bar') {
                    version {
                        strictly '1.5'
                    }
                }
            }
        """

        when:
        succeeds ':generateCatalogAsToml'

        then:
        expectPlatformContents 'expected2'
    }

    def "can generate a Gradle platform file from a dependencies configuration and the extension"() {
        buildFile << """
            catalog {
                versionCatalog {
                    bundle('my', ['foo', 'bar'])
                }
            }
            dependencies {
                versionCatalog 'org:foo:1.0'
                versionCatalog('org:bar') {
                    version {
                        strictly '1.5'
                    }
                }
            }
        """

        when:
        succeeds ':generateCatalogAsToml'

        then:
        expectPlatformContents 'expected3'
    }

    @ToBeFixedForConfigurationCache(because="doesn't failing test yet")
    def "reasonable error message if there's a name clash between two dependencies"() {
        buildFile << """
            dependencies {
                versionCatalog 'org1:foo:1.0'
                versionCatalog 'org2:foo:1.0'
            }
        """

        when:
        fails ':generateCatalogAsToml'

        then:
        failure.assertHasCause "A dependency with alias 'foo' already exists for module 'org2:foo'. Please configure an explicit alias for this dependency."
    }

    def "can declare a different alias in case of name clash"() {
        buildFile << """
            catalog {
               configureExplicitAlias 'foo2', 'org2', 'foo'
            }
            dependencies {
                versionCatalog 'org1:foo:1.0'
                versionCatalog 'org2:foo:1.0'
            }
        """

        when:
        succeeds ':generateCatalogAsToml'

        then:
        expectPlatformContents 'expected4'
    }

    def "can declare a explicit alias without name clash"() {
        buildFile << """
            catalog {
               configureExplicitAlias 'other', 'org', 'bar'
            }
            dependencies {
                versionCatalog 'org:foo:1.0'
                versionCatalog 'org:bar:1.0'
            }
        """

        when:
        succeeds ':generateCatalogAsToml'

        then:
        expectPlatformContents 'expected5'
    }

    def "can use either dependencies or constraints"() {
        buildFile << """
            dependencies {
                versionCatalog 'org:foo:1.0'
                constraints {
                    versionCatalog('org:bar') {
                        version {
                            require '1.2'
                        }
                    }
                }
            }
        """

        when:
        succeeds ':generateCatalogAsToml'

        then:
        expectPlatformContents 'expected6'
    }

    @ToBeFixedForConfigurationCache(because="doesn't failing test yet")
    def "can detect name clash between dependencies and constraints"() {
        buildFile << """
            dependencies {
                versionCatalog 'org:foo:1.0'
                constraints {
                    versionCatalog('org2:foo') {
                        version {
                            require '1.2'
                        }
                    }
                }
            }
        """

        when:
        fails ':generateCatalogAsToml'

        then:
        failure.assertHasCause "A dependency with alias 'foo' already exists for module 'org2:foo'. Please configure an explicit alias for this dependency."
    }

    def "can fix name clash between dependencies and constraints"() {
        buildFile << """
            catalog {
                configureExplicitAlias 'foo2', 'org2', 'foo'
            }
            dependencies {
                versionCatalog 'org:foo:1.0'
                constraints {
                    versionCatalog('org2:foo') {
                        version {
                            require '1.2'
                        }
                    }
                }
            }
        """

        when:
        succeeds ':generateCatalogAsToml'

        then:
        expectPlatformContents 'expected7'
    }

    def "can mix plugins, dependencies, constraints and model to create a platform"() {
        buildFile << """
            catalog {
                configureExplicitAlias 'foo2', 'org', 'foo'
                versionCatalog {
                    alias('foo').to('org:from-model:1.0')
                    bundle('my', ['foo', 'foo2', 'from-script'])
                }
                plugins {
                    id('my.plugin') version '1.7'
                }
            }
            dependencies {
                versionCatalog 'org:from-script:1.0'
                constraints {
                    versionCatalog('org:foo') {
                        version {
                            require '1.2'
                        }
                    }
                }
            }
        """

        when:
        succeeds ':generateCatalogAsToml'

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
        executedAndNotSkipped ':generateCatalogAsToml',
            ':generateMetadataFileForMavenPublication',
            ':generatePomFileForMavenPublication',
            ':publishMavenPublicationToMavenRepository'
        def module = mavenRepo.module("org.gradle", "test", "1.0")
            .withModuleMetadata()
        module.assertPublished()
        def metadata = module.parsedModuleMetadata
        metadata.variant("versionCatalogElements") {
            noMoreDependencies()
            assert attributes == [
                'org.gradle.category': 'platform',
                'org.gradle.usage': 'version-catalog'
            ]
            assert files.name == ['test-1.0.toml']
        }

        and:
        expectPlatformContents 'expected9'
    }

    private void withSampleCatalog() {
        buildFile << """
            catalog {
                versionCatalog {
                    alias("my-lib").to("org:foo:1.0")
                    alias("junit4").to("junit", "junit").version {
                        require "[4.13.1, 5["
                        prefer "4.13.1"
                    }
                    version("lib", "1.1")
                    alias("other").to("org", "bar").versionRef("lib")
                    bundle("test", ["my-lib", "junit4"])
                }
                plugins {
                    id("my.awesome.plugin") version "1.5"
                }
            }
        """
    }

    private void withPublishing(String component = 'versionCatalog') {
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

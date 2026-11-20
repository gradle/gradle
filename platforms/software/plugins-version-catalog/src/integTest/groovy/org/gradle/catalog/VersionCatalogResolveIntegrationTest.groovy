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


import org.gradle.integtests.fixtures.AbstractHttpDependencyResolutionTest
import org.gradle.test.fixtures.file.TestFile
import spock.lang.Issue


class VersionCatalogResolveIntegrationTest extends AbstractHttpDependencyResolutionTest implements VersionCatalogSupport {
    def setup() {
        settingsFile << """
            rootProject.name = 'test'
        """
        buildFile << """
            plugins {
                id 'java-library'
            }

            group = 'org.gradle.lib'
            version = '1.0'

            task checkDeps {
                def runtimeClasspath = configurations.runtimeClasspath
                doLast {
                    println("Resolved: \${runtimeClasspath.files.name.join(', ')}")
                }
            }
        """
    }

    def "can consume versions from a published Gradle platform"() {
        def platformProject = preparePlatformProject '''
            versionCatalog {
                library('my-lib', 'org.test:lib:1.1')
            }
        '''
        executer.inDirectory(platformProject).withTasks('publish').run()

        settingsFile << """
            dependencyResolutionManagement {
                repositories {
                    maven { url = "${mavenRepo.uri}" }
                }
                versionCatalogs {
                    libs {
                        from("org.gradle.test:my-platform:1.0")
                    }
                }
            }
        """

        buildFile << """
            dependencies {
                implementation libs.my.lib
            }
        """

        when:
        mavenRepo.module('org.test', 'lib', '1.1').publish()
        run ':checkDeps'

        then:
        outputContains 'Resolved: lib-1.1.jar'
    }

    def "can override versions defined in a Gradle platform"() {
        def platformProject = preparePlatformProject '''
            versionCatalog {
                def v = version('lib', '1.0')
                library('my-lib-core', 'org.test', 'lib').versionRef(v)
                library('my-lib-json', 'org.test', 'lib-json').versionRef(v)
            }
        '''
        executer.inDirectory(platformProject).withTasks('publish').run()

        settingsFile << """
            dependencyResolutionManagement {
                repositories {
                    maven { url = "${mavenRepo.uri}" }
                }
                versionCatalogs {
                    libs {
                        from("org.gradle.test:my-platform:1.0")
                        version('lib', '1.1') // override version declared in the platform, this is order sensitive
                    }
                }
            }
        """

        buildFile << """
            dependencies {
                implementation libs.my.lib.core
                implementation libs.my.lib.json
            }
        """

        when:
        mavenRepo.module('org.test', 'lib', '1.1').publish()
        mavenRepo.module('org.test', 'lib-json', '1.1').publish()
        run ':checkDeps'

        then:
        outputContains 'Resolved: lib-1.1.jar, lib-json-1.1.jar'
    }

    // This documents the existing behavior but it may change in the future
    def "can use dependency locking to resolve platform in settings"() {
        def platformProject = preparePlatformProject '''
            versionCatalog {
                library('my-lib', 'org.test:lib:1.0')
            }
        '''
        executer.inDirectory(platformProject).withTasks('publish').run()

        platformProject = preparePlatformProject '''
            versionCatalog {
                library('my-lib', 'org.test:lib:1.1')
            }
        ''', '1.1'
        executer.inDirectory(platformProject).withTasks('publish').run()

        settingsFile << """
            dependencyResolutionManagement {
                repositories {
                    maven { url = "${mavenRepo.uri}" }
                }
                versionCatalogs {
                    libs {
                        from("org.gradle.test:my-platform:+")
                    }
                }
            }
        """
        file("settings-gradle.lockfile") << """
org.gradle.test:my-platform:1.0=incomingCatalogForLibs0
"""

        buildFile << """
            dependencies {
                implementation libs.my.lib
            }
        """

        when:
        mavenRepo.module('org.test', 'lib', '1.0').publish()
        run ':checkDeps'

        then:
        outputContains 'Resolved: lib-1.0.jar'
    }

    @Issue("https://github.com/gradle/gradle/issues/30095")
    def "catalog locks are isolated from #nestedBuild with catalog #nestedCatalog"() {
        given:
        publishCatalog('root-catalog', '1.0')
        publishCatalog('nested-catalog', '1.0')
        settingsFile << """
            dependencyResolutionManagement {
                repositories { maven { url = '${mavenRepo.uri}' } }
                versionCatalogs {
                    libs { from('org.gradle.test:root-catalog:1.+') }
                }
            }
            ${nestedBuild == 'included' ? "includeBuild('included')" : ''}
        """
        file("$nestedBuild/settings.gradle") << """
            rootProject.name = '$nestedBuild'
            dependencyResolutionManagement {
                repositories { maven { url = '${mavenRepo.uri}' } }
                versionCatalogs {
                    $nestedCatalog { from('org.gradle.test:nested-catalog:1.+') }
                }
            }
        """
        file("$nestedBuild/build.gradle") << """
            tasks.register('checkCatalog') {
                def catalogVersion = ${nestedCatalog}.versions.catalog.get()
                doLast { println('Nested catalog version: ' + catalogVersion) }
            }
            ${nestedBuild == 'buildSrc' ? "tasks.named('jar') { dependsOn('checkCatalog') }" : ''}
        """
        buildFile << """
            tasks.register('checkCatalog') {
                ${nestedBuild == 'included' ? "dependsOn(gradle.includedBuild('included').task(':checkCatalog'))" : ''}
                def catalogVersion = libs.versions.catalog.get()
                doLast { println('Root catalog version: ' + catalogVersion) }
            }
        """

        when:
        run 'checkCatalog', '--write-locks'

        then:
        def rootLock = file('settings-gradle.lockfile').text
        rootLock.contains('org.gradle.test:root-catalog:1.0=incomingCatalogForLibs0')
        !rootLock.contains('nested-catalog')
        def nestedLock = file("$nestedBuild/settings-gradle.lockfile").text
        nestedLock.contains("org.gradle.test:nested-catalog:1.0=incomingCatalogFor${nestedCatalog.capitalize()}0")
        !nestedLock.contains('root-catalog')
        outputContains('Root catalog version: 1.0')
        outputContains('Nested catalog version: 1.0')

        when:
        publishCatalog('root-catalog', '1.1')
        publishCatalog('nested-catalog', '1.1')
        run 'checkCatalog', '--refresh-dependencies'

        then:
        outputContains('Root catalog version: 1.0')
        outputContains('Nested catalog version: 1.0')
        file('settings-gradle.lockfile').text == rootLock
        file("$nestedBuild/settings-gradle.lockfile").text == nestedLock

        where:
        nestedBuild | nestedCatalog
        'buildSrc'  | 'libs'
        'buildSrc'  | 'otherLibs'
        'included'  | 'libs'
        'included'  | 'otherLibs'
    }

    @Issue("https://github.com/gradle/gradle/issues/30095")
    def "local catalog in #nestedBuild does not create a root settings lockfile"() {
        given:
        if (nestedBuild == 'included') {
            settingsFile << "includeBuild('included')"
        }
        file("$nestedBuild/settings.gradle") << "rootProject.name = '$nestedBuild'"
        file("$nestedBuild/build.gradle") << ''
        file("$nestedBuild/gradle/libs.versions.toml") << '[versions]'

        when:
        run 'help', '--write-locks'

        then:
        !file('settings-gradle.lockfile').exists()
        file("$nestedBuild/settings-gradle.lockfile").text.readLines().contains('empty=incomingCatalogForLibs0')

        where:
        nestedBuild << ['buildSrc', 'included']
    }

    private void publishCatalog(String name, String version) {
        mavenRepo.module('org.gradle.test', name, version)
            .withModuleMetadata()
            .adhocVariants()
            .hasType('toml')
            .mainArtifact(content: "[versions]\ncatalog = '$version'\n".getBytes('UTF-8'))
            .variant('versionCatalogElements', ['org.gradle.category': 'platform', 'org.gradle.usage': 'version-catalog'])
            .publish()
    }

    def "reasonable error message if a platform cannot be resolved"() {
        settingsFile << """
            dependencyResolutionManagement {
                repositories {
                    maven { url = "${mavenRepo.uri}" }
                }
                versionCatalogs {
                    libs {
                        from("org.gradle.test:my-platform:1.0")
                    }
                }
            }
        """

        when:
        fails 'checkDeps'

        then:
        failure.assertHasCause "Could not find org.gradle.test:my-platform:1.0."
    }

    def "reasonable error message if a no repositories are defined in settings"() {
        settingsFile << """
            dependencyResolutionManagement {
                versionCatalogs {
                    libs {
                        from("org.gradle.test:my-platform:1.0")
                    }
                }
            }
        """

        when:
        fails 'checkDeps'

        then:
        failure.assertHasCause "Cannot resolve external dependency org.gradle.test:my-platform:1.0 because no repositories are defined."
    }

    def "can compose platforms via the version-catalog plugin"() {
        def module = mavenHttpRepo.module('org.gradle.test', 'my-platform', '1.0')
            .withModuleMetadata()

        def platformProject = preparePlatformProject '''
            versionCatalog {
                library('my-lib', 'org.test:lib:1.1')
            }
        '''
        executer.inDirectory(platformProject).withTasks('publish').run()

        settingsFile << """
            dependencyResolutionManagement {
                repositories {
                    maven { url = "${mavenHttpRepo.uri}" }
                }
            }
        """

        buildFile.text = """
            plugins {
                id 'version-catalog'
                id 'maven-publish'
            }

            group = 'org.gradle.platform'
            version = '1.0'

            catalog {
                versionCatalog {
                    from('org.gradle.test:my-platform:1.0')
                    library('other', 'org:other:1.5')
                }
            }

            publishing {
                repositories {
                    maven { url = "${mavenRepo.uri}" }
                }
                publications {
                    maven(MavenPublication) {
                        from components.versionCatalog
                    }
                }
            }

        """

        when: "platform isn't resolved"
        succeeds ":help"

        then:
        noExceptionThrown()

        when:
        module.pom.expectGet()
        module.moduleMetadata.expectGet()
        module.getArtifact(type: 'toml').expectGet()

        succeeds ':publish'

        then:
        expectPlatformContents 'composed-platform'
    }

    private TestFile preparePlatformProject(String platformSpec = "", String version = "1.0") {
        def platformDir = file('platform')
        platformDir.file("settings.gradle").text = """
            rootProject.name = "my-platform"
        """
        platformDir.file("build.gradle").text = """
            plugins {
                id 'version-catalog'
                id 'maven-publish'
            }

            group = 'org.gradle.test'
            version = '$version'

            publishing {
                publishing {
                    repositories {
                        maven {
                            url = "${mavenRepo.uri}"
                        }
                    }
                }
                publications {
                    maven(MavenPublication) {
                        from components.versionCatalog
                    }
                }
            }

            catalog {
                $platformSpec
            }
        """

        return platformDir
    }
}

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

package org.gradle.integtests.resolve.central

import org.gradle.integtests.fixtures.UnsupportedWithConfigurationCache
import org.gradle.integtests.resolve.PluginDslSupport
import spock.lang.Issue
import spock.lang.Unroll

class DependenciesExtensionIntegrationTest extends AbstractCentralDependenciesIntegrationTest implements PluginDslSupport {

    @Unroll
    @UnsupportedWithConfigurationCache(because = "the test uses an extension directly in the task body")
    def "dependencies declared in settings trigger the creation of an extension (notation=#notation)"() {
        settingsFile << """
            dependencyResolutionManagement {
                versionCatalogs {
                    libs {
                        $notation
                    }
                }
            }
        """

        buildFile << """
            apply plugin: 'java-library'

            tasks.register("verifyExtension") {
                doLast {
                    def lib = libs.foo
                    assert lib instanceof Provider
                    def dep = lib.get()
                    assert dep instanceof MinimalExternalModuleDependency
                    assert dep.module.group == 'org.gradle.test'
                    assert dep.module.name == 'lib'
                    assert dep.versionConstraint.requiredVersion == '1.0'
                }
            }
        """

        when:
        run 'verifyExtension'

        then:
        operations.hasOperation("Executing generation of dependency accessors for libs")

        when: "no change in settings"
        run 'verifyExtension'

        then: "extension is not regenerated"
        !operations.hasOperation("Executing generation of dependency accessors for libs")

        when: "adding a library to the model"
        settingsFile << """
            dependencyResolutionManagement {
                versionCatalogs {
                    libs {
                        alias("bar") to "org.gradle.test:bar:1.0"
                    }
                }
            }
        """
        run 'verifyExtension'
        then: "extension is regenerated"
        operations.hasOperation("Executing generation of dependency accessors for libs")

        when: "updating a version in the model"
        settingsFile.text = settingsFile.text.replace('org.gradle.test:bar:1.0', 'org.gradle.test:bar:1.1')
        run 'verifyExtension'

        then: "extension is not regenerated"
        !operations.hasOperation("Executing generation of dependency accessors for libs")
        outputContains 'Type-safe dependency accessors is an incubating feature.'

        where:
        notation << [
            'alias("foo").to("org.gradle.test:lib:1.0")',
            'alias("foo").to("org.gradle.test", "lib").version { require "1.0" }',
            'alias("foo").to("org.gradle.test", "lib").version("1.0")'
        ]
    }

    def "can use the generated extension to declare a dependency"() {
        settingsFile << """
            dependencyResolutionManagement {
                versionCatalogs {
                    libs {
                        alias("myLib").to("org.gradle.test", "lib").version {
                            require "1.0"
                        }
                    }
                }
            }
        """
        def lib = mavenHttpRepo.module("org.gradle.test", "lib", "1.0").publish()
        buildFile << """
            apply plugin: 'java-library'

            dependencies {
                implementation libs.myLib
            }
        """

        when:
        lib.pom.expectGet()
        lib.artifact.expectGet()

        then:
        run ':checkDeps'

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                module('org.gradle.test:lib:1.0')
            }
        }
    }

    def "can use the version catalog getter to register catalogs"() {
        settingsFile << """
            dependencyResolutionManagement {
                versionCatalogs.create('libs') {
                    alias("myLib").to("org.gradle.test", "lib").version {
                        require "1.0"
                    }
                }
            }
        """
        def lib = mavenHttpRepo.module("org.gradle.test", "lib", "1.0").publish()
        buildFile << """
            apply plugin: 'java-library'

            dependencies {
                implementation libs.myLib
            }
        """

        when:
        lib.pom.expectGet()
        lib.artifact.expectGet()

        then:
        run ':checkDeps'

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                module('org.gradle.test:lib:1.0')
            }
        }
    }

    def "can use the generated extension to declare a dependency constraint"() {
        settingsFile << """
            dependencyResolutionManagement {
                versionCatalogs {
                    libs {
                        alias("myLib").to("org.gradle.test", "lib").version {
                            require "1.0"
                        }
                    }
                }
            }
        """
        def lib = mavenHttpRepo.module("org.gradle.test", "lib", "1.0").publish()
        buildFile << """
            apply plugin: 'java-library'

            dependencies {
                implementation "org.gradle.test:lib" // intentional!
                constraints {
                    implementation(libs.myLib)
                }
            }
        """

        when:
        lib.pom.expectGet()
        lib.artifact.expectGet()

        then:
        run ':checkDeps'

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                constraint('org.gradle.test:lib:1.0')
                edge('org.gradle.test:lib', 'org.gradle.test:lib:1.0')
            }
        }
    }

    def "can use the generated extension to declare a dependency and override the version"() {
        settingsFile << """
            dependencyResolutionManagement {
                versionCatalogs {
                    libs {
                        alias("myLib").to("org.gradle.test", "lib").version {
                            require "1.0"
                        }
                    }
                }
            }
        """
        def lib = mavenHttpRepo.module("org.gradle.test", "lib", "1.1").publish()
        buildFile << """
            apply plugin: 'java-library'

            dependencies {
                implementation(libs.myLib) {
                    version {
                        require '1.1'
                    }
                }
            }
        """

        when:
        lib.pom.expectGet()
        lib.artifact.expectGet()

        then:
        run ':checkDeps'

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                module('org.gradle.test:lib:1.1')
            }
        }
    }

    def "can use the generated extension to declare a dependency constraint and override the version"() {
        settingsFile << """
            dependencyResolutionManagement {
                versionCatalogs {
                    libs {
                        alias("myLib").to("org.gradle.test", "lib").version {
                            require "1.0"
                        }
                    }
                }
            }
        """
        def lib = mavenHttpRepo.module("org.gradle.test", "lib", "1.1").publish()
        buildFile << """
            apply plugin: 'java-library'

            dependencies {
                implementation "org.gradle.test:lib" // intentional!
                constraints {
                    implementation(libs.myLib) {
                        version {
                            require '1.1'
                        }
                    }
                }
            }
        """

        when:
        lib.pom.expectGet()
        lib.artifact.expectGet()

        then:
        run ':checkDeps'

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                constraint('org.gradle.test:lib:1.1')
                edge('org.gradle.test:lib', 'org.gradle.test:lib:1.1')
            }
        }
    }

    void "can add several dependencies at once using a bundle"() {
        settingsFile << """
            dependencyResolutionManagement {
                versionCatalogs {
                    libs {
                        alias("lib").to("org.gradle.test", "lib").version {
                            require "1.0"
                        }
                        alias("lib2").to("org.gradle.test:lib2:1.0")
                        bundle("myBundle", ["lib", "lib2"])
                    }
                }
            }
        """
        def lib = mavenHttpRepo.module("org.gradle.test", "lib", "1.0").publish()
        def lib2 = mavenHttpRepo.module("org.gradle.test", "lib2", "1.0").publish()
        buildFile << """
            apply plugin: 'java-library'

            dependencies {
                implementation(libs.bundles.myBundle)
            }
        """

        when:
        lib.pom.expectGet()
        lib.artifact.expectGet()
        lib2.pom.expectGet()
        lib2.artifact.expectGet()

        then:
        run ':checkDeps'

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                module('org.gradle.test:lib:1.0')
                module('org.gradle.test:lib2:1.0')
            }
        }
    }

    void "overriding the version of a bundle overrides the version of all dependencies of the bundle"() {
        settingsFile << """
            dependencyResolutionManagement {
                versionCatalogs {
                    libs {
                        alias("lib").to("org.gradle.test", "lib").version {
                            require "1.0"
                        }
                        alias("lib2").to("org.gradle.test:lib2:1.0")
                        bundle("myBundle", ["lib", "lib2"])
                    }
                }
            }
        """
        def lib = mavenHttpRepo.module("org.gradle.test", "lib", "1.1").publish()
        def lib2 = mavenHttpRepo.module("org.gradle.test", "lib2", "1.1").publish()
        buildFile << """
            apply plugin: 'java-library'

            dependencies {
                implementation(libs.bundles.myBundle) {
                    version {
                        require '1.1'
                    }
                }
            }
        """

        when:
        lib.pom.expectGet()
        lib.artifact.expectGet()
        lib2.pom.expectGet()
        lib2.artifact.expectGet()

        then:
        run ':checkDeps'

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                module('org.gradle.test:lib:1.1')
                module('org.gradle.test:lib2:1.1')
            }
        }
    }

    def "can configure a different libraries extension"() {
        settingsFile << """
            dependencyResolutionManagement {
                versionCatalogs {
                    libraries {
                        alias("myLib").to("org.gradle.test", "lib").version {
                            require "1.0"
                        }
                    }
                }
            }
        """
        def lib = mavenHttpRepo.module("org.gradle.test", "lib", "1.0").publish()
        buildFile << """
            apply plugin: 'java-library'

            dependencies {
                implementation libraries.myLib
            }
        """

        when:
        lib.pom.expectGet()
        lib.artifact.expectGet()

        then:
        run ':checkDeps'

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                module('org.gradle.test:lib:1.0')
            }
        }
    }

    def "can configure multiple libraries extension"() {
        settingsFile << """
            dependencyResolutionManagement {
                versionCatalogs {
                    libraries {
                        alias("myLib").to("org.gradle.test", "lib").version {
                            require "1.0"
                        }
                    }
                    other {
                        alias("great").to("org.gradle.test", "lib2").version {
                            require "1.1"
                        }
                    }
                }
            }
        """
        def lib = mavenHttpRepo.module("org.gradle.test", "lib", "1.0").publish()
        def lib2 = mavenHttpRepo.module("org.gradle.test", "lib2", "1.1").publish()
        buildFile << """
            apply plugin: 'java-library'

            dependencies {
                implementation libraries.myLib
                implementation other.great
            }
        """

        when:
        lib.pom.expectGet()
        lib.artifact.expectGet()
        lib2.pom.expectGet()
        lib2.artifact.expectGet()

        then:
        run ':checkDeps'

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                module('org.gradle.test:lib:1.0')
                module('org.gradle.test:lib2:1.1')
            }
        }
    }

    // makes sure the extension with a different name is created even if a
    // different one with same contents exists
    def "can configure multiple libraries extension with same contents"() {
        settingsFile << """
            dependencyResolutionManagement {
                versionCatalogs {
                    libraries {
                        alias("myLib").to("org.gradle.test", "lib").version {
                            require "1.0"
                        }
                    }
                    other {
                        alias("myLib").to("org.gradle.test", "lib").version {
                            require "1.0"
                        }
                    }
                }
            }
        """
        def lib = mavenHttpRepo.module("org.gradle.test", "lib", "1.0").publish()
        buildFile << """
            apply plugin: 'java-library'

            dependencies {
                implementation libraries.myLib
                implementation other.myLib
            }
        """

        when:
        lib.pom.expectGet()
        lib.artifact.expectGet()

        then:
        run ':checkDeps'

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                module('org.gradle.test:lib:1.0')
            }
        }
    }

    def "extension can be used in any subproject"() {
        settingsFile << """
            dependencyResolutionManagement {
                versionCatalogs {
                    libs {
                        alias("lib").to("org.gradle.test:lib:1.0")
                    }
                }
            }
            include 'other'
        """
        def lib = mavenHttpRepo.module("org.gradle.test", "lib", "1.0").publish()
        buildFile << """
            apply plugin: 'java-library'

            dependencies {
                implementation project(":other")
            }
        """

        file("other/build.gradle") << """
            plugins {
                id 'java-library'
            }

            dependencies {
                implementation libs.lib
            }
        """

        when:
        lib.pom.expectGet()
        lib.artifact.expectGet()

        then:
        run ':checkDeps'

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                project(":other", "test:other:") {
                    module('org.gradle.test:lib:1.0')
                }
            }
        }
    }

    def "libraries extension is not visible in buildSrc"() {
        settingsFile << """
            dependencyResolutionManagement {
                versionCatalogs {
                    libs {
                        alias("lib").to("org.gradle.test:lib:1.0")
                    }
                }
            }
        """
        file("buildSrc/build.gradle") << """
            dependencies {
                classpath libs.lib
            }
        """

        when:
        fails ':help'

        then: "extension is not generated if there are no libraries defined"
        failure.assertHasCause("Could not get unknown property 'libs' for object of type org.gradle.api.internal.artifacts.dsl.dependencies.DefaultDependencyHandler")
    }

    def "buildSrc and main project have different libraries extensions"() {
        settingsFile << """
            dependencyResolutionManagement {
                versionCatalogs {
                    libs {
                        alias("lib").to("org.gradle.test:lib:1.0")
                    }
                }
            }
        """
        file("buildSrc/build.gradle") << """
            repositories {
                maven { url "${mavenHttpRepo.uri}" }
            }

            dependencies {
                implementation libs.build.src.lib
            }
        """
        file("buildSrc/settings.gradle") << """
            dependencyResolutionManagement {
                versionCatalogs {
                    libs {
                        alias("build-src-lib").to("org.gradle.test:buildsrc-lib:1.0")
                    }
                }
            }
        """
        buildFile << """
            apply plugin: 'java-library'

            dependencies {
                implementation libs.lib
            }
        """

        def buildSrcLib = mavenHttpRepo.module('org.gradle.test', 'buildsrc-lib', '1.0').publish()
        def lib = mavenHttpRepo.module('org.gradle.test', 'lib', '1.0').publish()

        when:
        buildSrcLib.pom.expectGet()
        buildSrcLib.artifact.expectGet()
        lib.pom.expectGet()
        lib.artifact.expectGet()

        succeeds ':checkDeps'

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                module('org.gradle.test:lib:1.0')
            }
        }
    }

    def "included builds use their own libraries extension"() {
        file("included/build.gradle") << """
            plugins {
                id 'java-library'
            }

            group = 'com.acme'
            version = 'zloubi'

            dependencies {
                implementation libs.from.included
            }
        """
        file("included/settings.gradle") << """
            rootProject.name = 'included'

            dependencyResolutionManagement {
                repositories {
                    maven { url "${mavenHttpRepo.uri}" }
                }
                versionCatalogs {
                    libs {
                        alias('from-included').to('org.gradle.test:other:1.1')
                    }
                }
            }
        """

        settingsFile << """
            includeBuild 'included'
        """

        buildFile << """
            apply plugin: 'java-library'

            dependencies {
                implementation 'com.acme:included:1.0'
            }
        """
        def lib = mavenHttpRepo.module('org.gradle.test', 'other', '1.1').publish()

        when:
        lib.pom.expectGet()
        lib.artifact.expectGet()
        succeeds ':checkDeps'

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                edge("com.acme:included:1.0", "project :included", "com.acme:included:zloubi") {
                    compositeSubstitute()
                    configuration = "runtimeElements"
                    module('org.gradle.test:other:1.1')
                }
            }
        }
    }

    def "can declare a version with a name and reference it"() {
        settingsFile << """
            dependencyResolutionManagement {
                versionCatalogs {
                    libs {
                        version("myVersion") {
                            require "1.0"
                        }
                        alias("myLib").to("org.gradle.test", "lib").versionRef("myVersion")
                        alias("myOtherLib").to("org.gradle.test", "lib2").versionRef("myOtherVersion")
                        version("myOtherVersion") {
                            // intentionnally declared AFTER
                            strictly "1.1"
                        }
                    }
                }
            }
        """
        def lib = mavenHttpRepo.module("org.gradle.test", "lib", "1.0").publish()
        def lib2 = mavenHttpRepo.module("org.gradle.test", "lib2", "1.1").publish()
        buildFile << """
            apply plugin: 'java-library'

            dependencies {
                implementation libs.myLib
                implementation libs.myOtherLib
            }
        """

        when:
        lib.pom.expectGet()
        lib.artifact.expectGet()
        lib2.pom.expectGet()
        lib2.artifact.expectGet()

        then:
        run ':checkDeps'

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                module('org.gradle.test:lib:1.0')
                edge('org.gradle.test:lib2:{strictly 1.1}', 'org.gradle.test:lib2:1.1')
            }
        }
    }

    def "multiple libraries can use the same version reference"() {
        settingsFile << """
            dependencyResolutionManagement {
                versionCatalogs {
                        libs {
                        def myVersion = version("myVersion") {
                            require "1.0"
                        }
                        alias("myLib").to("org.gradle.test", "lib").versionRef("myVersion")
                        alias("myOtherLib").to("org.gradle.test", "lib2").versionRef(myVersion)
                    }
                }
            }
        """
        def lib = mavenHttpRepo.module("org.gradle.test", "lib", "1.0").publish()
        def lib2 = mavenHttpRepo.module("org.gradle.test", "lib2", "1.0").publish()
        buildFile << """
            apply plugin: 'java-library'

            dependencies {
                implementation libs.myLib
                implementation libs.myOtherLib
            }
        """

        when:
        lib.pom.expectGet()
        lib.artifact.expectGet()
        lib2.pom.expectGet()
        lib2.artifact.expectGet()

        then:
        run ':checkDeps'

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                module('org.gradle.test:lib:1.0')
                module('org.gradle.test:lib2:1.0')
            }
        }
    }

    @Unroll
    def "can generate a version accessor and use it in a build script"() {
        settingsFile << """
            dependencyResolutionManagement {
                versionCatalogs {
                    libs {
                        version("libVersion") {
                            $notation
                        }
                    }
                }
            }
        """
        def lib = mavenHttpRepo.module("org.gradle.test", "lib", "1.0").publish()
        buildFile << """
            apply plugin: 'java-library'

            dependencies {
                implementation "org.gradle.test:lib:\${libs.versions.libVersion.get()}"
            }
        """

        when:
        lib.pom.expectGet()
        lib.artifact.expectGet()

        then:
        run ':checkDeps'

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                // always 1.0 because calling `getLibVersion` will always loose the rich aspect
                // of the version model
                module("org.gradle.test:lib:1.0")
            }
        }

        where:
        notation << [
            "require '1.0'",
            "strictly '1.0'",
            "prefer '1.0'"
        ]
    }

    def "can use the generated extension to select the test fixtures of a dependency"() {
        settingsFile << """
            dependencyResolutionManagement {
                versionCatalogs {
                    libs {
                        alias("myLib").to("org.gradle.test", "lib").version {
                            require "1.1"
                        }
                    }
                }
            }
        """
        def lib = mavenHttpRepo.module("org.gradle.test", "lib", "1.1")
            .withModuleMetadata()
            .variant("testFixturesApiElements", ['org.gradle.usage': 'java-api', 'org.gradle.libraryelements': 'jar']) {
                capability('org.gradle.test', 'lib-test-fixtures', '1.1')
                artifact("lib-1.1-test-fixtures.jar")
            }
            .variant("testFixturesRuntimeElements", ['org.gradle.usage': 'java-runtime', 'org.gradle.libraryelements': 'jar']) {
                capability('org.gradle.test', 'lib-test-fixtures', '1.1')
                artifact("lib-1.1-test-fixtures.jar")
            }
            .publish()
        buildFile << """
            apply plugin: 'java-library'

            dependencies {
                implementation(testFixtures(libs.myLib))
            }
        """

        when:
        lib.pom.expectGet()
        lib.moduleMetadata.expectGet()
        lib.getArtifact(classifier: 'test-fixtures').expectGet()

        then:
        run ':checkDeps'

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                module('org.gradle.test:lib:1.1') {
                    variant('testFixturesRuntimeElements', [
                        'org.gradle.status': 'release',
                        'org.gradle.usage': 'java-runtime',
                        'org.gradle.libraryelements': 'jar'])
                    artifact(classifier: 'test-fixtures')
                }
            }
        }
    }

    def "can use the generated extension to select the platform variant of a dependency"() {
        settingsFile << """
            dependencyResolutionManagement {
                versionCatalogs {
                    libs {
                        alias("myLib").to("org.gradle.test", "lib").version {
                            require "1.1"
                        }
                    }
                }
            }
        """
        def lib = mavenHttpRepo.module("org.gradle.test", "lib", "1.1")
            .publish()
        buildFile << """
            apply plugin: 'java-library'

            dependencies {
                implementation(platform(libs.myLib))
            }
        """

        when:
        lib.pom.expectGet()

        then:
        run ':checkDeps'

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                module('org.gradle.test:lib:1.1') {
                    variant('platform-runtime', [
                        'org.gradle.status': 'release',
                        'org.gradle.usage': 'java-runtime',
                        'org.gradle.category': 'platform'])
                    noArtifacts()
                }
            }
        }
    }

    def "can use the generated extension to select a classified dependency"() {
        settingsFile << """
            dependencyResolutionManagement {
                versionCatalogs {
                    libs {
                        alias("myLib").to("org.gradle.test", "lib").version {
                            require "1.1"
                        }
                    }
                }
            }
        """
        def lib = mavenHttpRepo.module("org.gradle.test", "lib", "1.1")
            .publish()

        lib.getArtifactFile(classifier: 'test-fixtures').bytes = []

        buildFile << """
            apply plugin: 'java-library'

            dependencies {
                implementation(variantOf(libs.myLib) { classifier('test-fixtures') })
            }
        """

        when:
        lib.pom.expectGet()
        lib.getArtifact(classifier: 'test-fixtures').expectGet()

        then:
        run ':checkDeps'

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                module('org.gradle.test:lib:1.1') {
                    artifact(classifier: 'test-fixtures')
                }
            }
        }
    }

    def "can use the generated extension to select an artifact with different type"() {
        settingsFile << """
            dependencyResolutionManagement {
                versionCatalogs {
                    libs {
                        alias("myLib").to("org.gradle.test", "lib").version {
                            require "1.1"
                        }
                    }
                }
            }
        """
        def lib = mavenHttpRepo.module("org.gradle.test", "lib", "1.1")
            .publish()

        lib.getArtifactFile(type: 'txt').bytes = "test".bytes

        buildFile << """
            apply plugin: 'java-library'

            dependencies {
                implementation(variantOf(libs.myLib) { artifactType('txt') })
            }
        """

        when:
        lib.pom.expectGet()
        lib.getArtifact(type: 'txt').expectGet()

        then:
        run ':checkDeps'

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                module('org.gradle.test:lib:1.1') {
                    artifact(type: 'txt')
                }
            }
        }
    }

    def "reasonable error message if an alias clashes with a group of dependencies"() {
        settingsFile << """
            dependencyResolutionManagement {
                versionCatalogs {
                    libs {
                        alias("top").to("org:test:1.0") // would generate libs.top as a Provider<Dependency>
                        alias("top.bottom").to("org:bottom:1.0") // would generate libs.top as a factory of dependencies
                    }
                }
            }
        """

        when:
        fails ":help"

        then:
        failure.assertHasCause "Cannot generate top level accessors because it contains both aliases and groups of the same name: [top]"
    }

    def "reasonable error message if an alias clashes with a sub-group of dependencies"() {
        settingsFile << """
            dependencyResolutionManagement {
                versionCatalogs {
                    libs {
                        alias("top.middle").to("org:test:1.0") // would generate libs.top.middle as a Provider<Dependency>
                        alias("top.middle.bottom").to("org:bottom:1.0") // would generate libs.top.middle as a factory of dependencies
                    }
                }
            }
        """

        when:
        fails ":help"

        then:
        failure.assertHasCause "Cannot generate accessors for top because it contains both aliases and groups of the same name: [middle]"
    }

    def "can access all version catalogs with optional API"() {
        settingsFile << """
            dependencyResolutionManagement {
                versionCatalogs {
                    libs {
                        alias("lib").to("org:test:1.0")
                        alias("lib2").to("org:test2:1.0")
                        bundle("all", ["lib", "lib2"])
                    }
                    other {
                        version("ver", "1.1")
                        alias("lib").to("org", "test2").versionRef("ver")
                    }
                }
            }
        """

        buildFile << """
            def catalogs = project.extensions.getByType(VersionCatalogsExtension)
            tasks.register("verifyCatalogs") {
                doLast {
                    def libs = catalogs.named("libs")
                    def other = catalogs.find("other").get()
                    assert !catalogs.find("missing").present
                    def lib = libs.findDependency('lib')
                    assert lib.present
                    assert lib.get() instanceof Provider
                    assert !libs.findDependency('missing').present
                    assert libs.findBundle('all').present
                    assert !libs.findBundle('missing').present
                    assert other.findVersion('ver').present
                    assert !other.findVersion('missing').present
                }
            }
        """

        when:
        run 'verifyCatalogs'

        then:
        noExceptionThrown()
    }

    @Issue("https://github.com/gradle/gradle/issues/15382")
    def "can add a dependency in a project via a buildSrc plugin"() {
        settingsFile << """
            dependencyResolutionManagement {
                versionCatalogs {
                    libs {
                        alias("lib").to("org:test:1.0")
                    }
                }
            }
        """
        file("buildSrc/src/main/groovy/MyPlugin.groovy") << """
            import org.gradle.api.*
            import groovy.transform.CompileStatic
            import org.gradle.api.artifacts.VersionCatalogsExtension

            @CompileStatic // to make sure we don't rely on dynamic APIs
            class MyPlugin implements Plugin<Project> {
                void apply(Project p) {
                    def libs = p.extensions.getByType(VersionCatalogsExtension).named('libs')
                    p.dependencies.addProvider("implementation", libs.findDependency('lib').get())
                }
            }
        """

        buildFile << """
            apply plugin: 'java-library'
            apply plugin: MyPlugin
        """
        def lib = mavenHttpRepo.module('org', 'test', '1.0').publish()

        when:
        lib.pom.expectGet()
        lib.artifact.expectGet()

        then:
        run ':checkDeps'

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                module('org:test:1.0')
            }
        }
    }

    @Issue("https://github.com/gradle/gradle/issues/15382")
    def "can add a dependency in a project via a precompiled script plugin"() {
        settingsFile << """
            dependencyResolutionManagement {
                versionCatalogs {
                    libs {
                        alias("lib").to("org:test:1.0")
                    }
                }
            }
        """
        file("buildSrc/build.gradle") << """
            plugins {
                id 'groovy-gradle-plugin'
            }
        """
        file("buildSrc/src/main/groovy/my.plugin.gradle") << """
            pluginManager.withPlugin('java') {
                def libs = extensions.getByType(VersionCatalogsExtension).named('libs')
                dependencies.addProvider("implementation", libs.findDependency('lib').get())
            }
        """

        buildFile << """
            apply plugin: 'java-library'
        """

        withPlugin('my.plugin')

        def lib = mavenHttpRepo.module('org', 'test', '1.0').publish()

        when:
        lib.pom.expectGet()
        lib.artifact.expectGet()

        then:
        run ':checkDeps'

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                module('org:test:1.0')
            }
        }
    }
}

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

package org.gradle.integtests.resolve.catalog

import org.gradle.api.internal.catalog.problems.VersionCatalogErrorMessages
import org.gradle.api.internal.catalog.problems.VersionCatalogProblemId
import org.gradle.api.internal.catalog.problems.VersionCatalogProblemTestFor
import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache
import org.gradle.integtests.resolve.PluginDslSupport
import spock.lang.Issue

class VersionCatalogExtensionIntegrationTest extends AbstractVersionCatalogIntegrationTest implements PluginDslSupport, VersionCatalogErrorMessages {

    def setup() {
        enableProblemsApiCheck()
    }

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
                def lib = libs.foo
                assert lib instanceof Provider
                doLast {
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
                        library("bar", "org.gradle.test:bar:1.0")
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
        outputDoesNotContain 'Type-safe dependency accessors is an incubating feature.'

        where:
        notation << [
            'library("foo", "org.gradle.test:lib:1.0")',
            'library("foo", "org.gradle.test", "lib").version { require "1.0" }',
            'library("foo", "org.gradle.test", "lib").version("1.0")'
        ]
    }

    def "dependencies declared in settings will fail if left uninitialized"() {
        settingsFile << """
            dependencyResolutionManagement {
                versionCatalogs {
                    libs {
                        library("my-great-lib", "org.gradle.test", "lib")
                    }
                }
            }
        """

        when:
        fails()

        then:
        verifyContains(failure.error, aliasNotFinished {
            inCatalog("libs")
            alias("my.great.lib")
        })

        and:
        verifyAll(receivedProblem) {
            fqid == 'dependency-version-catalog:alias-not-finished'
            contextualLabel == 'Problem: In version catalog libs, dependency alias builder \'my.great.lib\' was not finished.'
            details == 'A version was not set or explicitly declared as not wanted'
            solutions == [
                'Call `.version()` to give the alias a version',
                'Call `.withoutVersion()` to explicitly declare that the alias should not have a version',
            ]
        }
    }

    def "logs contain a message indicating if an unfinished builder is overwritten with one that finishes"() {
        settingsFile << """
            dependencyResolutionManagement {
                versionCatalogs {
                    libs {
                        // Even though this is unfinished, it will not trigger an error
                        // It should log a message though.
                        library("my-great-lib", "org.gradle.test", "lib")

                        library("my-great-lib", "org.gradle.test", "lib").version("1.0")
                    }
                }
            }
        """

        def lib = mavenHttpRepo.module("org.gradle.test", "lib", "1.0").publish()
        buildFile << """
            apply plugin: 'java-library'

            dependencies {
                implementation libs.my.great.lib
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
        outputContains("Duplicate alias builder registered for my.great.lib")
    }

    def "can use the generated extension to declare a dependency"() {
        settingsFile << """
            dependencyResolutionManagement {
                versionCatalogs {
                    libs {
                        library("myLib", "org.gradle.test", "lib").version {
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

    def "can use nested accessors to declare a dependency version"() {
        settingsFile << """
            dependencyResolutionManagement {
                versionCatalogs {
                    libs {
                        version("my-great-lib", "1.0")
                        library("my-great-lib", "org.gradle.test", "lib").versionRef("my-great-lib")
                    }
                }
            }
        """
        def lib = mavenHttpRepo.module("org.gradle.test", "lib", "1.0").publish()
        buildFile << """
            apply plugin: 'java-library'

            dependencies {
                implementation libs.my.great.lib
            }

            assert libs.versions.my.great.lib.get() == "1.0"
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
                    library("myLib", "org.gradle.test", "lib").version {
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

    def "can use the generated extension to declare a dependency constraint with and without sub-group"() {
        settingsFile << """
            dependencyResolutionManagement {
                versionCatalogs {
                    libs {
                        version("myLib") {
                            strictly "[1.0,1.1)"
                        }
                        library("myLib", "org.gradle.test", "lib-core").versionRef("myLib")
                        library("myLib-ext", "org.gradle.test", "lib-ext").versionRef("myLib")
                    }
                }
            }
        """
        def publishLib = { String artifactId, String version ->
            def lib = mavenHttpRepo.module("org.gradle.test", artifactId, version)
                .withModuleMetadata()
                .publish()
            lib.moduleMetadata.expectGet()
            lib.pom.expectGet()
            return lib
        }
        publishLib("lib-core", "1.0").with {
            it.rootMetaData.expectGet()
            it.artifact.expectGet()
        }
        publishLib("lib-core", "1.1")
        publishLib("lib-ext", "1.0").with {
            it.rootMetaData.expectGet()
            it.artifact.expectGet()
        }
        buildFile << """
            apply plugin: 'java-library'

            dependencies {
                implementation "org.gradle.test:lib-core:1.+" // intentional!
                implementation "org.gradle.test:lib-ext" // intentional!
                constraints {
                    implementation libs.myLib //.asProvider() as a workaround
                    implementation libs.myLib.ext
                }
            }
        """

        when:
        run ':checkDeps'

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                constraint("org.gradle.test:lib-core:{strictly [1.0,1.1)}", "org.gradle.test:lib-core:1.0")
                constraint("org.gradle.test:lib-ext:{strictly [1.0,1.1)}", "org.gradle.test:lib-ext:1.0")
                edge("org.gradle.test:lib-core:1.+", "org.gradle.test:lib-core:1.0") {
                    notRequested()
                    byReasons(["rejected version 1.1", "constraint"])
                }
                edge("org.gradle.test:lib-ext", "org.gradle.test:lib-ext:1.0") {
                    byConstraint()
                }
            }
        }
    }

    @Issue("https://github.com/gradle/gradle/issues/22650")
    def "can use the generated extension to declare a dependency constraint with and without sub-group using bundles"() {
        settingsFile << """
            dependencyResolutionManagement {
                versionCatalogs {
                    libs {
                        version("myLib") {
                            strictly "[1.0,1.1)"
                        }
                        library("myLib", "org.gradle.test", "lib-core").versionRef("myLib")
                        library("myLib-ext", "org.gradle.test", "lib-ext").versionRef("myLib")
                        bundle("myBundle", ["myLib"])
                        bundle("myBundle-ext", ["myLib-ext"])
                    }
                }
            }
        """
        def publishLib = { String artifactId, String version ->
            def lib = mavenHttpRepo.module("org.gradle.test", artifactId, version)
                .withModuleMetadata()
                .publish()
            lib.moduleMetadata.expectGet()
            lib.pom.expectGet()
            return lib
        }
        publishLib("lib-core", "1.0").with {
            it.rootMetaData.expectGet()
            it.artifact.expectGet()
        }
        publishLib("lib-core", "1.1")
        publishLib("lib-ext", "1.0").with {
            it.rootMetaData.expectGet()
            it.artifact.expectGet()
        }
        buildFile << """
            apply plugin: 'java-library'

            dependencies {
                implementation "org.gradle.test:lib-core:1.+" // intentional!
                implementation "org.gradle.test:lib-ext" // intentional!
                constraints {
                    implementation libs.bundles.myBundle
                    implementation libs.bundles.myBundle.ext
                }
            }
        """

        when:
        run ':checkDeps'

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                constraint("org.gradle.test:lib-core:{strictly [1.0,1.1)}", "org.gradle.test:lib-core:1.0")
                constraint("org.gradle.test:lib-ext:{strictly [1.0,1.1)}", "org.gradle.test:lib-ext:1.0")
                edge("org.gradle.test:lib-core:1.+", "org.gradle.test:lib-core:1.0") {
                    notRequested()
                    byReasons(["rejected version 1.1", "constraint"])
                }
                edge("org.gradle.test:lib-ext", "org.gradle.test:lib-ext:1.0") {
                    byConstraint()
                }
            }
        }
    }

    def "can use the generated extension to declare a dependency and override the version"() {
        settingsFile << """
            dependencyResolutionManagement {
                versionCatalogs {
                    libs {
                        library("myLib", "org.gradle.test", "lib").version {
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
                        library("myLib", "org.gradle.test", "lib").version {
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
                edge('org.gradle.test:lib', 'org.gradle.test:lib:1.1') {
                    byConstraint()
                }
            }
        }
    }

    void "can add several dependencies at once using a bundle"() {
        settingsFile << """
            dependencyResolutionManagement {
                versionCatalogs {
                    libs {
                        library("lib", "org.gradle.test", "lib").version {
                            require "1.0"
                        }
                        library("lib2", "org.gradle.test:lib2:1.0")
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

    void "bundles can use nested accessors"() {
        settingsFile << """
            dependencyResolutionManagement {
                versionCatalogs {
                    libs {
                        library("lib", "org.gradle.test", "lib").version {
                            require "1.0"
                        }
                        library("lib2", "org.gradle.test:lib2:1.0")
                        bundle("my-great-bundle", ["lib", "lib2"])
                    }
                }
            }
        """
        def lib = mavenHttpRepo.module("org.gradle.test", "lib", "1.0").publish()
        def lib2 = mavenHttpRepo.module("org.gradle.test", "lib2", "1.0").publish()
        buildFile << """
            apply plugin: 'java-library'

            dependencies {
                implementation(libs.bundles.my.great.bundle)
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
                        library("lib", "org.gradle.test", "lib").version {
                            require "1.0"
                        }
                        library("lib2", "org.gradle.test:lib2:1.0")
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
                    myLibs {
                        library("myLib", "org.gradle.test", "lib").version {
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
                implementation myLibs.myLib
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
                    myLibs {
                        library("myLib", "org.gradle.test", "lib").version {
                            require "1.0"
                        }
                    }
                    otherLibs {
                        library("great", "org.gradle.test", "lib2").version {
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
                implementation myLibs.myLib
                implementation otherLibs.great
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
                    myLibs {
                        library("myLib", "org.gradle.test", "lib").version {
                            require "1.0"
                        }
                    }
                    otherLibs {
                        library("myLib", "org.gradle.test", "lib").version {
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
                implementation myLibs.myLib
                implementation otherLibs.myLib
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
                        library("lib", "org.gradle.test:lib:1.0")
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
        disableProblemsApiCheck()
        settingsFile << """
            dependencyResolutionManagement {
                versionCatalogs {
                    libs {
                        library("lib", "org.gradle.test:lib:1.0")
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
                        library("lib", "org.gradle.test:lib:1.0")
                    }
                }
            }
        """
        file("buildSrc/build.gradle") << """
            repositories {
                maven { url = "${mavenHttpRepo.uri}" }
            }

            dependencies {
                implementation libs.build.src.lib
            }
        """
        file("buildSrc/settings.gradle") << """
            dependencyResolutionManagement {
                versionCatalogs {
                    libs {
                        library("build-src-lib", "org.gradle.test:buildsrc-lib:1.0")
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
                    maven { url = "${mavenHttpRepo.uri}" }
                }
                versionCatalogs {
                    libs {
                        library('from-included', 'org.gradle.test:other:1.1')
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
                edge("com.acme:included:1.0", ":included", "com.acme:included:zloubi") {
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
                        library("myLib", "org.gradle.test", "lib").versionRef("myVersion")
                        library("myOtherLib", "org.gradle.test", "lib2").versionRef("myOtherVersion")
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
                        library("myLib", "org.gradle.test", "lib").versionRef("myVersion")
                        library("myOtherLib", "org.gradle.test", "lib2").versionRef(myVersion)
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
                // always 1.0 because calling `getLibVersion` will always lose the rich aspect
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

    def "can use the generated extension to select the test fixtures of a dependency with and without sub-accessors"() {
        settingsFile << """
            dependencyResolutionManagement {
                versionCatalogs {
                    libs {
                        library("myLib", "org.gradle.test", "lib").version {
                            require "1.1"
                        }
                        library("myLib-subgroup", "org.gradle.test", "lib.subgroup").version {
                            require "1.1"
                        }
                    }
                }
            }
        """
        def publishLib = { String artifactId ->
            def lib = mavenHttpRepo.module("org.gradle.test", artifactId, "1.1")
                .withModuleMetadata()
                .variant("testFixturesApiElements", ['org.gradle.usage': 'java-api', 'org.gradle.libraryelements': 'jar']) {
                    capability('org.gradle.test', "$artifactId-test-fixtures", '1.1')
                    artifact("$artifactId-1.1-test-fixtures.jar")
                }
                .variant("testFixturesRuntimeElements", ['org.gradle.usage': 'java-runtime', 'org.gradle.libraryelements': 'jar']) {
                    capability('org.gradle.test', "$artifactId-test-fixtures", '1.1')
                    artifact("$artifactId-1.1-test-fixtures.jar")
                }
                .publish()
            lib.pom.expectGet()
            lib.moduleMetadata.expectGet()
            lib.getArtifact(classifier: 'test-fixtures').expectGet()
        }
        publishLib("lib")
        publishLib("lib.subgroup")
        buildFile << """
            apply plugin: 'java-library'

            dependencies {
                implementation(testFixtures(libs.myLib))
                implementation(testFixtures(libs.myLib.subgroup))
            }
        """

        when:
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
                module('org.gradle.test:lib.subgroup:1.1') {
                    variant('testFixturesRuntimeElements', [
                        'org.gradle.status': 'release',
                        'org.gradle.usage': 'java-runtime',
                        'org.gradle.libraryelements': 'jar'])
                    artifact(classifier: 'test-fixtures')
                }
            }
        }
    }

    def "can use the generated extension to select the platform variant of a dependency with and without sub-accessors"() {
        settingsFile << """
            dependencyResolutionManagement {
                versionCatalogs {
                    libs {
                        library("myLib", "org.gradle.test", "lib").version {
                            require "1.1"
                        }
                        library("myLib-subgroup", "org.gradle.test", "lib.subgroup").version {
                            require "1.1"
                        }
                    }
                }
            }
        """
        mavenHttpRepo.module("org.gradle.test", "lib", "1.1")
            .publish()
            .pom.expectGet()
        mavenHttpRepo.module("org.gradle.test", "lib.subgroup", "1.1")
            .publish()
            .pom.expectGet()
        buildFile << """
            apply plugin: 'java-library'

            dependencies {
                implementation(platform(libs.myLib))
                implementation(platform(libs.myLib.subgroup))
            }
        """

        when:
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
                module('org.gradle.test:lib.subgroup:1.1') {
                    variant('platform-runtime', [
                        'org.gradle.status': 'release',
                        'org.gradle.usage': 'java-runtime',
                        'org.gradle.category': 'platform'])
                    noArtifacts()
                }
            }
        }
    }

    @Issue("https://github.com/gradle/gradle/issues/17849")
    def "can use the generated extension to select the enforced-platform variant of a dependency with and without sub-accessors"() {
        settingsFile << """
            dependencyResolutionManagement {
                versionCatalogs {
                    libs {
                        library("myLib", "org.gradle.test", "lib").version {
                            require "1.1"
                        }
                        library("myLib-subgroup", "org.gradle.test", "lib.subgroup").version {
                            require "1.1"
                        }
                    }
                }
            }
        """
        mavenHttpRepo.module("org.gradle.test", "lib", "1.1")
            .publish()
            .pom.expectGet()
        mavenHttpRepo.module("org.gradle.test", "lib.subgroup", "1.1")
            .publish()
            .pom.expectGet()
        buildFile << """
            apply plugin: 'java-library'

            dependencies {
                implementation(enforcedPlatform(libs.myLib))
                implementation(enforcedPlatform(libs.myLib.subgroup))
            }
        """

        when:
        run ':checkDeps'

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                edge('org.gradle.test:lib:{strictly 1.1}', 'org.gradle.test:lib:1.1') {
                    variant('enforced-platform-runtime', [
                        'org.gradle.status': 'release',
                        'org.gradle.usage': 'java-runtime',
                        'org.gradle.category': 'enforced-platform'])
                    noArtifacts()
                }
                edge('org.gradle.test:lib.subgroup:{strictly 1.1}', 'org.gradle.test:lib.subgroup:1.1') {
                    variant('enforced-platform-runtime', [
                        'org.gradle.status': 'release',
                        'org.gradle.usage': 'java-runtime',
                        'org.gradle.category': 'enforced-platform'])
                    noArtifacts()
                }
            }
        }
    }

    def "can use the generated extension to select a classified dependency with and without sub-accessors"() {
        settingsFile << """
            dependencyResolutionManagement {
                versionCatalogs {
                    libs {
                        library("myLib", "org.gradle.test", "lib").version {
                            require "1.1"
                        }
                        library("myLib-subgroup", "org.gradle.test", "lib.subgroup").version {
                            require "1.1"
                        }
                    }
                }
            }
        """
        def lib = mavenHttpRepo.module("org.gradle.test", "lib", "1.1")
            .publish()
        lib.getArtifactFile(classifier: 'test-fixtures').bytes = []
        lib.pom.expectGet()
        lib.getArtifact(classifier: 'test-fixtures').expectGet()
        def libSubgroup = mavenHttpRepo.module("org.gradle.test", "lib.subgroup", "1.1")
            .publish()
        libSubgroup.getArtifactFile(classifier: 'test-fixtures').bytes = []
        libSubgroup.pom.expectGet()
        libSubgroup.getArtifact(classifier: 'test-fixtures').expectGet()

        buildFile << """
            apply plugin: 'java-library'

            dependencies {
                implementation(variantOf(libs.myLib) { classifier('test-fixtures') })
                implementation(variantOf(libs.myLib.subgroup) { classifier('test-fixtures') })
            }
        """

        when:
        run ':checkDeps'

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                module('org.gradle.test:lib:1.1') {
                    artifact(classifier: 'test-fixtures')
                }
                module('org.gradle.test:lib.subgroup:1.1') {
                    artifact(classifier: 'test-fixtures')
                }
            }
        }
    }

    def "can use the generated extension to select an artifact with different type with and without sub-accessors"() {
        settingsFile << """
            dependencyResolutionManagement {
                versionCatalogs {
                    libs {
                        library("myLib", "org.gradle.test", "lib").version {
                            require "1.1"
                        }
                        library("myLib-subgroup", "org.gradle.test", "lib.subgroup").version {
                            require "1.1"
                        }
                    }
                }
            }
        """
        def lib = mavenHttpRepo.module("org.gradle.test", "lib", "1.1")
            .publish()
        lib.getArtifactFile(type: 'txt').bytes = "test".bytes
        lib.pom.expectGet()
        lib.getArtifact(type: 'txt').expectGet()
        def libSubgroup = mavenHttpRepo.module("org.gradle.test", "lib.subgroup", "1.1")
            .publish()
        libSubgroup.getArtifactFile(type: 'txt').bytes = "test".bytes
        libSubgroup.pom.expectGet()
        libSubgroup.getArtifact(type: 'txt').expectGet()


        buildFile << """
            apply plugin: 'java-library'

            dependencies {
                implementation(variantOf(libs.myLib) { artifactType('txt') })
                implementation(variantOf(libs.myLib.subgroup) { artifactType('txt') })
            }
        """

        when:
        run ':checkDeps'

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                module('org.gradle.test:lib:1.1') {
                    artifact(type: 'txt')
                }
                module('org.gradle.test:lib.subgroup:1.1') {
                    artifact(type: 'txt')
                }
            }
        }
    }

    def "supports aliases which also have children"() {
        settingsFile << """
            dependencyResolutionManagement {
                versionCatalogs {
                    libs {
                        library("top", "org:top:1.0")
                        library("top.bottom", "org:bottom:1.0")
                    }
                }
            }
        """
        def top = mavenHttpRepo.module("org", "top", "1.0").publish()
        def bottom = mavenHttpRepo.module("org", "bottom", "1.0").publish()
        buildFile << """
            apply plugin: 'java-library'

            dependencies {
                implementation libs.top
                implementation libs.top.bottom
            }
        """

        when:
        top.pom.expectGet()
        bottom.pom.expectGet()
        top.artifact.expectGet()
        bottom.artifact.expectGet()

        then:
        run ':checkDeps'

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                module('org:top:1.0')
                module('org:bottom:1.0')
            }
        }
    }

    def "supports aliases which also have children using intermediate leaves"() {
        settingsFile << """
            dependencyResolutionManagement {
                versionCatalogs {
                    libs {
                        library("top.middle", "org:top:1.0")
                        library("top.middle.bottom", "org:bottom:1.0")
                    }
                }
            }
        """
        def top = mavenHttpRepo.module("org", "top", "1.0").publish()
        def bottom = mavenHttpRepo.module("org", "bottom", "1.0").publish()
        buildFile << """
            apply plugin: 'java-library'

            dependencies {
                implementation libs.top.middle
                implementation libs.top.middle.bottom
            }
        """

        when:
        top.pom.expectGet()
        bottom.pom.expectGet()
        top.artifact.expectGet()
        bottom.artifact.expectGet()

        then:
        run ':checkDeps'

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                module('org:top:1.0')
                module('org:bottom:1.0')
            }
        }
    }

    def "supports aliases which also have children using empty intermediate level"() {
        settingsFile << """
            dependencyResolutionManagement {
                versionCatalogs {
                    libs {
                        library("top", "org:top:1.0")
                        library("top.middle.bottom", "org:bottom:1.0")
                    }
                }
            }
        """
        def top = mavenHttpRepo.module("org", "top", "1.0").publish()
        def bottom = mavenHttpRepo.module("org", "bottom", "1.0").publish()
        buildFile << """
            apply plugin: 'java-library'

            dependencies {
                implementation libs.top
                implementation libs.top.middle.bottom
            }
        """

        when:
        top.pom.expectGet()
        bottom.pom.expectGet()
        top.artifact.expectGet()
        bottom.artifact.expectGet()

        then:
        run ':checkDeps'

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                module('org:top:1.0')
                module('org:bottom:1.0')
            }
        }
    }

    def "supports aliases which also have children using empty complex intermediate levels (separator = #separator)"() {
        settingsFile << """
            dependencyResolutionManagement {
                versionCatalogs {
                    libs {
                        library("foo${separator}bar${separator}baz${separator}a", "org:a:1.0")
                        library("foo${separator}bar${separator}baz${separator}b", "org:b:1.0")
                        library("foo${separator}bar", "org:bar:1.0")
                    }
                }
            }
        """
        def a = mavenHttpRepo.module("org", "a", "1.0").publish()
        def bar = mavenHttpRepo.module("org", "bar", "1.0").publish()
        buildFile << """
            apply plugin: 'java-library'

            dependencies {
                implementation libs.foo.bar.baz.a
                implementation libs.foo.bar
            }
        """

        when:
        a.pom.expectGet()
        bar.pom.expectGet()
        a.artifact.expectGet()
        bar.artifact.expectGet()

        then:
        run ':checkDeps'

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                module('org:a:1.0')
                module('org:bar:1.0')
            }
        }

        where:
        separator << ['.', '_', '-']
    }

    def "can access all version catalogs with optional API"() {
        settingsFile << """
            dependencyResolutionManagement {
                versionCatalogs {
                    libs {
                        library("lib", "org:test:1.0")
                        library("lib2", "org:test2:1.0")
                        plugin("plug", "org.test2").version("1.0")
                        bundle("all", ["lib", "lib2"])
                    }
                    otherLibs {
                        version("ver", "1.1")
                        library("lib", "org", "test2").versionRef("ver")
                    }
                }
            }
        """

        buildFile << """
            def catalogs = project.extensions.getByType(VersionCatalogsExtension)
            tasks.register("verifyCatalogs") {
                doLast {
                    def libs = catalogs.named("libs")
                    def other = catalogs.find("otherLibs").get()
                    assert !catalogs.find("missing").present
                    def lib = libs.findLibrary('lib')
                    assert lib.present
                    assert lib.get() instanceof Provider
                    assert !libs.findLibrary('missing').present
                    assert libs.findPlugin('plug').present
                    assert libs.findBundle('all').present
                    assert !libs.findBundle('missing').present
                    assert other.findVersion('ver').present
                    assert !other.findVersion('missing').present

                    assert libs.libraryAliases == ['lib', 'lib2']
                    assert libs.bundleAliases == ['all']
                    assert libs.versionAliases == []

                    assert other.libraryAliases == ['lib']
                    assert other.bundleAliases == []
                    assert other.versionAliases == ['ver']

                }
            }
        """

        when:
        run 'verifyCatalogs'

        then:
        noExceptionThrown()
    }

    def "can access versions with find methods without normalized aliases with optional API"() {
        settingsFile << """
            dependencyResolutionManagement {
                versionCatalogs {
                    libs {
                        version("my-ver", "1.1")
                        library("my-lib", "org:test:1.0")
                        plugin("my-plug", "org.test2").version("1.0")
                        bundle("my-all", ["my-lib"])
                    }
                }
            }
        """

        buildFile << """
            def catalogs = project.extensions.getByType(VersionCatalogsExtension)
            tasks.register("verifyCatalogs") {
                doLast {
                    def libs = catalogs.named("libs")
                    assert libs.findVersion('my-ver').present
                    assert libs.findVersion('my_ver').present
                    assert libs.findVersion('my.ver').present

                    assert libs.findLibrary('my-lib').present
                    assert libs.findLibrary('my_lib').present
                    assert libs.findLibrary('my.lib').present

                    assert libs.findBundle('my-all').present
                    assert libs.findBundle('my_all').present
                    assert libs.findBundle('my.all').present

                    assert libs.findPlugin('my-plug').present
                    assert libs.findPlugin('my_plug').present
                    assert libs.findPlugin('my.plug').present
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
                        library("lib", "org:test:1.0")
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
                    p.dependencies.addProvider("implementation", libs.findLibrary('lib').get())
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
                        library("lib", "org:test:1.0")
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
                dependencies.addProvider("implementation", libs.findLibrary('lib').get())
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

    def "supports versions which also have children"() {
        settingsFile << """
            dependencyResolutionManagement {
                versionCatalogs {
                    libs {
                        version("my", "1.0")
                        version("my.bottom", "1.1")
                    }
                }
            }
        """

        buildFile """
            tasks.register("dumpVersions") {
                def first = libs.versions.my.asProvider()
                def second = libs.versions.my.bottom
                doLast {
                    println "First: \${first.get()}"
                    println "Second: \${second.get()}"
                }
            }
        """

        when:
        succeeds 'dumpVersions'

        then:
        outputContains """First: 1.0
Second: 1.1"""
    }

    def "supports versions which also have children using intermediate leaves"() {
        settingsFile << """
            dependencyResolutionManagement {
                versionCatalogs {
                    libs {
                        version("my.middle", "1.0")
                        version("my.middle.bottom", "1.1")
                    }
                }
            }
        """

        buildFile """
            tasks.register("dumpVersions") {
                def first = libs.versions.my.middle.asProvider()
                def second = libs.versions.my.middle.bottom
                doLast {
                    println "First: \${first.get()}"
                    println "Second: \${second.get()}"
                }
            }
        """

        when:
        succeeds 'dumpVersions'

        then:
        outputContains """First: 1.0
Second: 1.1"""
    }

    def "supports versions which also have children using empty intermediate level"() {
        settingsFile << """
            dependencyResolutionManagement {
                versionCatalogs {
                    libs {
                        version("my", "1.0")
                        version("my.middle.bottom", "1.1")
                    }
                }
            }
        """

        buildFile """
            tasks.register("dumpVersions") {
                def first = libs.versions.my.asProvider()
                def second = libs.versions.my.middle.bottom
                doLast {
                    println "First: \${first.get()}"
                    println "Second: \${second.get()}"
                }
            }
        """

        when:
        succeeds 'dumpVersions'

        then:
        outputContains """First: 1.0
Second: 1.1"""
    }

    def "supports bundles which also have children"() {
        settingsFile << """
            dependencyResolutionManagement {
                versionCatalogs {
                    libs {
                        library("lib1", "org:lib1:1.0")
                        library("lib2", "org:lib2:1.0")
                        bundle("my", ["lib1", "lib2"])
                        bundle("my.other", ["lib1", "lib2"])
                    }
                }
            }
        """

        def lib1 = mavenHttpRepo.module("org", "lib1", "1.0").publish()
        def lib2 = mavenHttpRepo.module("org", "lib2", "1.0").publish()
        buildFile << """
            apply plugin: 'java-library'

            dependencies {
                implementation libs.bundles.my
                implementation libs.bundles.my.other
            }
        """

        when:
        lib1.pom.expectGet()
        lib2.pom.expectGet()
        lib1.artifact.expectGet()
        lib2.artifact.expectGet()

        then:
        run ':checkDeps'

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                module('org:lib1:1.0')
                module('org:lib2:1.0')
            }
        }
    }

    def "supports bundles which also have children using intermediate leaves"() {
        settingsFile << """
            dependencyResolutionManagement {
                versionCatalogs {
                    libs {
                        library("lib1", "org:lib1:1.0")
                        library("lib2", "org:lib2:1.0")
                        bundle("my.middle", ["lib1", "lib2"])
                        bundle("my.middle.other", ["lib1", "lib2"])
                    }
                }
            }
        """

        def lib1 = mavenHttpRepo.module("org", "lib1", "1.0").publish()
        def lib2 = mavenHttpRepo.module("org", "lib2", "1.0").publish()
        buildFile << """
            apply plugin: 'java-library'

            dependencies {
                implementation libs.bundles.my.middle
                implementation libs.bundles.my.middle.other
            }
        """

        when:
        lib1.pom.expectGet()
        lib2.pom.expectGet()
        lib1.artifact.expectGet()
        lib2.artifact.expectGet()

        then:
        run ':checkDeps'

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                module('org:lib1:1.0')
                module('org:lib2:1.0')
            }
        }
    }

    def "supports bundles which also have children using empty intermediate leaves"() {
        settingsFile << """
            dependencyResolutionManagement {
                versionCatalogs {
                    libs {
                        library("lib1", "org:lib1:1.0")
                        library("lib2", "org:lib2:1.0")
                        bundle("my", ["lib1", "lib2"])
                        bundle("my.middle.other", ["lib1", "lib2"])
                    }
                }
            }
        """

        def lib1 = mavenHttpRepo.module("org", "lib1", "1.0").publish()
        def lib2 = mavenHttpRepo.module("org", "lib2", "1.0").publish()
        buildFile << """
            apply plugin: 'java-library'

            dependencies {
                implementation libs.bundles.my
                implementation libs.bundles.my.middle.other
            }
        """

        when:
        lib1.pom.expectGet()
        lib2.pom.expectGet()
        lib1.artifact.expectGet()
        lib2.artifact.expectGet()

        then:
        run ':checkDeps'

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                module('org:lib1:1.0')
                module('org:lib2:1.0')
            }
        }
    }

    @VersionCatalogProblemTestFor(
        VersionCatalogProblemId.RESERVED_ALIAS_NAME
    )
    @Issue("https://github.com/gradle/gradle/issues/16888")
    def "disallows aliases which have a name clash with Java methods"() {
        settingsFile << """
            dependencyResolutionManagement {
                versionCatalogs {
                    libs {
                        library("$reserved", "org:lib1:1.0")
                    }
                }
            }
        """

        when:
        executer.withStacktraceEnabled()
        fails "help"

        then:
        verifyContains(failure.error, reservedAlias {
            inCatalog("libs")
            alias(reserved)
            reservedAliases "extensions", "convention"
        })

        and:
        verifyAll(receivedProblem) {
            fqid == 'dependency-version-catalog:reserved-alias-name'
            contextualLabel == "Problem: In version catalog libs, alias '$reserved' is not a valid alias."
            details == "Alias '$reserved' is a reserved name in Gradle which prevents generation of accessors."
            solutions == [ 'Use a different alias which doesn\'t contain any of \'convention\' or \'extensions\'.' ]
        }

        where:
        reserved << [
            "extensions",
            "convention"
        ]
    }

    @VersionCatalogProblemTestFor(
        VersionCatalogProblemId.RESERVED_ALIAS_NAME
    )
    @Issue("https://github.com/gradle/gradle/issues/23106")
    def "disallows aliases which contain a name that clashes with Java methods"() {
        settingsFile << """
            dependencyResolutionManagement {
                versionCatalogs {
                    libs {
                        library("$reserved", "org:lib1:1.0")
                    }
                }
            }
        """

        when:
        executer.withStacktraceEnabled()
        fails "help"

        then:
        verifyContains(failure.error, reservedAlias {
            inCatalog("libs")
            shouldNotContain(reserved)
            reservedNames "class"
        })

        and:
        verifyAll(receivedProblem) {
            fqid == 'dependency-version-catalog:reserved-alias-name'
            contextualLabel == "Problem: In version catalog libs, alias '$reserved' is not a valid alias."
            details == "Alias '$reserved' is a reserved name in Gradle which prevents generation of accessors."
            solutions == [ 'Use a different alias which doesn\'t contain \'class\'.' ]
        }

        where:
        reserved << [
            "class",
            "my-class",
            "my-class-lib"
        ]
    }

    @VersionCatalogProblemTestFor(
        VersionCatalogProblemId.RESERVED_ALIAS_NAME
    )
    def "disallows aliases for dependency which prefix clash with reserved words"() {
        settingsFile << """
            dependencyResolutionManagement {
                versionCatalogs {
                    libs {
                        library("$reservedName", "org:lib1:1.0")
                    }
                }
            }
        """

        when:
        executer.withStacktraceEnabled()
        fails "help"

        then:
        verifyContains(failure.error, reservedAlias {
            inCatalog("libs")
            alias(reservedName).shouldNotBeEqualTo(prefix)
            reservedAliasPrefix('bundles', 'plugins', 'versions')
        })

        and:
        verifyAll(receivedProblem) {
            fqid == 'dependency-version-catalog:reserved-alias-name'
            contextualLabel == "Problem: In version catalog libs, alias '$reservedName' is not a valid alias."
            details == "Prefix for dependency shouldn\'t be equal to '$prefix'"
            solutions == [ 'Use a different alias which prefix is not equal to \'bundles\', \'plugins\', or \'versions\'' ]
        }

        where:
        reservedName  | prefix
        "bundles"     | "bundles"
        "versions"    | "versions"
        "plugins"     | "plugins"
        "bundles-my"  | "bundles"
        "versions-my" | "versions"
        "plugins-my"  | "plugins"
    }

    @VersionCatalogProblemTestFor(
        VersionCatalogProblemId.RESERVED_ALIAS_NAME
    )
    def "aliases for dependencies, plugins and versions do not clash with version catalog methods"() {
        settingsFile << """
            dependencyResolutionManagement {
                versionCatalogs {
                    libs {
                        version("$reservedName", "1.0")
                        library("$reservedName", "org:lib1:1.0")
                        plugin("$reservedName", "org:lib1").version("1.0")
                    }
                }
            }
        """

        when:
        succeeds "help"

        then:
        noExceptionThrown()

        where:
        reservedName << [
            "bundleAliases",
            "versionAliases",
            "pluginAliases",
            "dependencyAliases",
            "libraryAliases",
            "findPlugin",
            "findDependency",
            "findLibrary",
            "findVersion",
            "findBundle"
        ]
    }

    @VersionCatalogProblemTestFor(
        VersionCatalogProblemId.RESERVED_ALIAS_NAME
    )
    def "allow aliases for plugins and versions which have are reserved words for dependencies"() {
        settingsFile << """
            dependencyResolutionManagement {
                versionCatalogs {
                    libs {
                        version("$reservedName", "1.0")
                        plugin("$reservedName", "org:lib1").version("1.0")
                    }
                }
            }
        """

        when:
        succeeds "help"

        then:
        noExceptionThrown()

        where:
        reservedName << [
            "bundles",
            "versions",
            "plugins"
        ]
    }

    @Issue("https://github.com/gradle/gradle/issues/16768")
    def "the artifact notation doesn't require to set 'name'"() {
        settingsFile << """
            dependencyResolutionManagement {
                versionCatalogs {
                    libs {
                        library("myLib", "org.gradle.test", "lib").version {
                            require "1.0"
                        }
                    }
                }
            }
        """

        def lib = mavenHttpRepo.module("org.gradle.test", "lib", "1.0")
        lib.artifact(classifier: 'classy')
        lib.publish()

        buildFile << """
            apply plugin: 'java-library'

            dependencies {
                implementation(libs.myLib) {
                    artifact {
                        classifier = 'classy'
                    }
                }
            }
        """

        when:
        lib.pom.expectGet()
        lib.getArtifact(classifier: 'classy').expectGet()

        then:
        run ':checkDeps'

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                module('org.gradle.test:lib:1.0') {
                    artifact(classifier: 'classy')
                }
            }
        }
    }

    def "elements accessed with optional API have useful toString()"() {
        settingsFile << """
            dependencyResolutionManagement {
                versionCatalogs {
                    libs {
                        version("ver", "1.5")
                        library("lib", "org:test:1.0")
                        library("lib2", "org", "test2").version {
                            require "1.0.0"
                            prefer "1.1.0"
                            reject "1.0.5"
                        }
                        library("lib3", "org", "test3").withoutVersion()
                        bundle("all", ["lib", "lib2"])
                        plugin('greeter', 'com.acme.greeter').version('1.4')
                        plugin('greeter2', 'com.acme.greeter2').version {
                            require "1.0.0"
                            prefer "1.1.0"
                            reject "1.0.5"
                        }
                    }
                }
            }
        """

        buildFile << """
            def catalog = project.extensions.getByType(VersionCatalogsExtension).named("libs")
            tasks.register("printCatalog") {
                doLast {
                    catalog.findVersion("ver").ifPresent {
                        println("Found version: '\${it.toString()}'.")
                    }
                    catalog.findLibrary("lib").ifPresent {
                        println("Found dependency: '\${it.get().toString()}'.")
                    }
                    catalog.findLibrary("lib2").ifPresent {
                        println("Found dependency: '\${it.get().toString()}'.")
                    }
                    catalog.findLibrary("lib3").ifPresent {
                        println("Found dependency: '\${it.get().toString()}'.")
                    }
                    catalog.findBundle("all").ifPresent {
                        println("Found bundle: '\${it.get().toString()}'.")
                    }
                    catalog.findPlugin("greeter").ifPresent {
                        println("Found plugin: '\${it.get().toString()}'.")
                    }
                    catalog.findPlugin("greeter2").ifPresent {
                        println("Found plugin: '\${it.get().toString()}'.")
                    }
                }
            }
        """

        when:
        run 'printCatalog'

        then:
        outputContains "Found version: '1.5'."
        outputContains "Found dependency: 'org:test:1.0'."
        outputContains "Found dependency: 'org:test2:{require 1.0.0; prefer 1.1.0; reject 1.0.5}'."
        outputContains "Found dependency: 'org:test3'."
        outputContains "Found bundle: '[org:test:1.0, org:test2:{require 1.0.0; prefer 1.1.0; reject 1.0.5}]'."
        outputContains "Found plugin: 'com.acme.greeter:1.4'."
        outputContains "Found plugin: 'com.acme.greeter2:{require 1.0.0; prefer 1.1.0; reject 1.0.5}'."
    }

    @Issue("https://github.com/gradle/gradle/issues/17874")
    def "supports version catalogs in force method of resolutionStrategy"() {
        settingsFile << """
            dependencyResolutionManagement {
                versionCatalogs {
                    libs {
                        library("myLib", "org.gradle.test:lib:3.0.5")
                        library("myLib-subgroup", "org.gradle.test:lib2:3.0.5")
                    }
                }
            }
        """

        def lib = mavenHttpRepo.module("org.gradle.test", "lib", "3.0.5").publish()
        def lib2 = mavenHttpRepo.module("org.gradle.test", "lib2", "3.0.5").publish()

        buildFile << """
            apply plugin: 'java-library'
            dependencies {
                implementation "org.gradle.test:lib:3.0.6"
                implementation "org.gradle.test:lib2:3.0.6"
                configurations.all {
                    resolutionStrategy {
                        force(libs.myLib)
                        force(libs.myLib.subgroup)
                    }
                }
            }
        """

        when:
        lib.pom.expectGet()
        lib.artifact.expectGet()
        lib2.pom.expectGet()
        lib2.artifact.expectGet()
        succeeds ':checkDeps'

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                edge("org.gradle.test:lib:3.0.6", "org.gradle.test:lib:3.0.5") {
                    forced()
                }
                edge("org.gradle.test:lib2:3.0.6", "org.gradle.test:lib2:3.0.5") {
                    forced()
                }
            }
        }
    }

    @Issue("https://github.com/gradle/gradle/issues/17874")
    def "doesn't support rich versions from version catalogs in force method of resolutionStrategy"() {
        disableProblemsApiCheck()
        settingsFile << """
            dependencyResolutionManagement {
                versionCatalogs {
                    libs {
                        library("myLib", "org.gradle.test", "lib").version {
                            strictly "[3.0, 4.0["
                            prefer "3.0.5"
                        }
                    }
                }
            }
        """

        buildFile << """
            apply plugin: 'java-library'
            dependencies {
                implementation "org.gradle.test:lib:3.0.6"
                configurations.all {
                    resolutionStrategy {
                        force(libs.myLib)
                    }
                }
            }
        """

        when:
        fails ':checkDeps'

        then:
        failure.assertHasCause("Cannot convert a version catalog entry: 'org.gradle.test:lib:{strictly [3.0, 4.0[; prefer 3.0.5}' to an object of type ModuleVersionSelector. Rich versions are not supported for 'force()'.")
    }

    @Issue("https://github.com/gradle/gradle/issues/17874")
    def "fails if plugin, version or bundle is used in force of resolution strategy"() {
        disableProblemsApiCheck()

        settingsFile << """
            dependencyResolutionManagement {
                versionCatalogs {
                    libs {
                        version("myVersion", "1.0")
                        library("myLib", "org.gradle.test:lib:3.0.5")
                        bundle("myBundle", ["myLib"])
                        plugin("myPlugin", "org.gradle.test").version("1.0")
                    }
                }
            }
        """

        buildFile << """
            apply plugin: 'java-library'
            dependencies {
                implementation "org.gradle.test:lib:3.0.6"
                configurations.all {
                    resolutionStrategy {
                        force(libs.$catalogEntry)
                    }
                }
            }
        """

        when:
        fails ':checkDeps'

        then:
        failure.assertHasCause("Cannot convert a version catalog entry '$catalogEntryAsString' to an object of type ModuleVersionSelector. Only dependency accessors are supported but not plugin, bundle or version accessors for 'force()'.")

        where:
        catalogEntry         | catalogEntryAsString
        "versions.myVersion" | "1.0"
        "plugins.myPlugin"   | "org.gradle.test:1.0"
        "bundles.myBundle"   | "[org.gradle.test:lib:3.0.5]"
    }

    @Issue("https://github.com/gradle/gradle/issues/23096")
    @ToBeFixedForConfigurationCache(because = "task uses Configuration API")
    def 'all properties of version catalog dependencies are copied when the dependency is copied'() {
        disableProblemsApiCheck()
        given:
        buildFile << """
            configurations {
                implementation
                destination1
                destination2
            }

            dependencies {
                implementation(libs.test1) {
                    because("reason1")

                    exclude(group: "test-group", module: "test-module")
                    artifact {
                        name = "test-name"
                        classifier = "test-classifier"
                        extension = "test-ext"
                        type = "test-type"
                        url = "test-url"
                    }
                    transitive = true
                    endorseStrictVersions()

                    version {
                        branch = "branch"
                        strictly("123")
                        prefer("789")
                        reject("aaa")
                    }

                    changing = true
                }
                implementation(libs.test2) {
                    transitive = false
                    targetConfiguration = "abc"
                    doNotEndorseStrictVersions()

                    version {
                        require("456")
                    }

                    changing = false
                }
                implementation(libs.test3) {
                    attributes {
                        attribute(Attribute.of('foo', String), 'bar')
                    }
                    capabilities {
                        requireCapability("org:test-cap:1.1")
                    }
                }
            }

            def verifyDep(original, copied) {
                // Dependency
                assert original.group == copied.group
                assert original.name == copied.name
                assert original.version == copied.version
                assert original.reason == copied.reason

                // ModuleDependency
                assert original.excludeRules == copied.excludeRules
                assert original.artifacts == copied.artifacts
                assert original.transitive == copied.transitive
                assert original.targetConfiguration == copied.targetConfiguration
                assert original.attributes == copied.attributes
                assert original.capabilitySelectors == copied.capabilitySelectors
                assert original.endorsingStrictVersions == copied.endorsingStrictVersions

                // ExternalDependency + ExternalModuleDependency
                assert original.changing == copied.changing
                assert original.versionConstraint == copied.versionConstraint
            }

            def getOriginal(dep) {
                configurations.implementation.dependencies.find { it.name == dep.name }
            }

            task copyAndVerifyDependencies {
                configurations.implementation.dependencies.each {
                    project.dependencies.add("destination1", it)
                    configurations.destination2.dependencies.add(it)
                }

                doLast {
                    configurations.destination1.dependencies.each {
                        verifyDep(getOriginal(it), it)
                    }

                    configurations.destination2.dependencies.each {
                        verifyDep(getOriginal(it), it)
                    }

                    configurations.implementation.copy().dependencies.each {
                        verifyDep(getOriginal(it), it)
                    }
                }
            }
        """

        file("gradle/libs.versions.toml") << """[libraries]
test1 = { module = 'org:test1', version = '1.0' }
test2 = { module = 'org:test2', version = '1.0' }
test3 = { module = 'org:test3', version = '1.0' }
"""

        expect:
        succeeds "copyAndVerifyDependencies"
    }
}

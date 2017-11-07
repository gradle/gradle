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
import org.gradle.integtests.fixtures.resolve.ResolveTestFixture
import org.gradle.test.fixtures.HttpRepository
import org.gradle.test.fixtures.Module
import org.gradle.test.fixtures.maven.MavenFileRepository
import spock.lang.Unroll

abstract class DependencyMetadataRulesIntegrationTest extends AbstractHttpDependencyResolutionTest {
    def resolve = new ResolveTestFixture(buildFile, variantToTest)
    def moduleA, moduleB

    abstract HttpRepository getRepo()

    abstract String getRepoDeclaration()

    abstract Module setupCustomVariantsForModule(Module module)

    abstract String getVariantToTest()

    /**
     * Does the published metadata provide variants with attributes? Eventually all metadata should do that.
     * For Ivy and Maven POM metadata, the variants and attributes should be derived from configurations and scopes.
     */
    abstract boolean getPublishedModulesHaveAttributes()

    def setup() {
        resolve.prepare()
        moduleA = setupCustomVariantsForModule(repo.module("org.test", "moduleA").allowAll()).publish()
        moduleB = repo.module("org.test", "moduleB").allowAll().publish()

        settingsFile << """
            rootProject.name = 'testproject'
        """
        buildFile << """
            $repoDeclaration
            
            configurations { $variantToTest { attributes { attribute(Attribute.of('format', String), 'custom') } } }
            
            dependencies {
                $variantToTest group: 'org.test', name: 'moduleA', version: '1.0' ${publishedModulesHaveAttributes ? "" : ", configuration: '$variantToTest'"}
            }
        """
    }

    @Unroll
    def "a dependency can be added using #notation notation"() {
        when:
        buildFile << """
            dependencies {
                components {
                    withModule('org.test:moduleA') {
                        withVariant("$variantToTest") { 
                            withDependencies {
                                add $dependency
                            }
                        }
                    }
                }
            }
        """

        then:
        succeeds 'checkDep'
        def variantToTest = variantToTest
        resolve.expectGraph {
            root(':', ':testproject:') {
                module("org.test:moduleA:1.0:$variantToTest") {
                    module('org.test:moduleB:1.0')
                }
            }
        }

        where:
        notation | dependency
        "string" | "'org.test:moduleB:1.0'"
        "map"    | "group: 'org.test', name: 'moduleB', version: '1.0'"
    }

    @Unroll
    def "a dependency can be added and configured using #notation notation"() {
        when:
        buildFile << """
            dependencies {
                components {
                    withModule('org.test:moduleA') {
                        withVariant("$variantToTest") {
                            withDependencies {
                                add($dependency) {
                                    it.version = '1.0'
                                }
                            }
                        }
                    }
                }
            }
        """

        then:
        succeeds 'checkDep'
        def variantToTest = variantToTest
        resolve.expectGraph {
            root(':', ':testproject:') {
                module("org.test:moduleA:1.0:$variantToTest") {
                    module('org.test:moduleB:1.0')
                }
            }
        }

        where:
        notation | dependency
        "string" | "'org.test:moduleB:1.0'"
        "map"    | "group: 'org.test', name: 'moduleB'"
    }

    def "a dependency can be removed"() {
        given:
        moduleA.dependsOn(moduleB).publish()

        when:
        buildFile << """
            dependencies {
                components {
                    all {
                        withVariant("$variantToTest") {
                            withDependencies {
                                removeAll { it.version == '1.0' }
                            }
                        }
                    }
                }
            }
        """

        then:
        succeeds 'checkDep'
        def variantToTest = variantToTest
        resolve.expectGraph {
            root(':', ':testproject:') {
                module("org.test:moduleA:1.0:$variantToTest")
            }
        }
    }

    def "dependency modifications are visible in the next rule"() {
        when:
        buildFile << """
            dependencies {
                components {
                    withModule('org.test:moduleA') {
                        withVariant("$variantToTest") { 
                            withDependencies { dependencies ->
                                assert dependencies.size() == 0
                                dependencies.add 'org.test:moduleB:1.0'
                            }
                        }
                    }
                    withModule('org.test:moduleA') {
                        withVariant("$variantToTest") {
                            withDependencies { dependencies ->
                                assert dependencies.size() == 1
                                dependencies.removeAll { true }
                            }
                        }
                    }
                    all {
                        withVariant("$variantToTest") { 
                            withDependencies { dependencies ->
                                assert dependencies.size() == 0
                            }
                        }
                    }
                }
            }
        """

        then:
        succeeds 'checkDep'
        def variantToTest = variantToTest
        resolve.expectGraph {
            root(':', ':testproject:') {
                module("org.test:moduleA:1.0:$variantToTest")
            }
        }
    }

    def "can set version on dependency"() {
        given:
        moduleA.dependsOn('org.test', 'moduleB', '2.0').publish()

        when:
        buildFile << """
            dependencies {
                components {
                    withModule('org.test:moduleA') {
                        withVariant("$variantToTest") { 
                            withDependencies {
                                it.each {
                                    it.version = '1.0'
                                }
                            }
                        }
                    }
                }
            }
        """

        then:
        succeeds 'checkDep'
        def variantToTest = variantToTest
        resolve.expectGraph {
            root(':', ':testproject:') {
                module("org.test:moduleA:1.0:$variantToTest") {
                    module('org.test:moduleB:1.0')
                }
            }
        }
    }

    def "changing dependencies in one variant leaves other variants untouched"() {
        when:
        buildFile << """
            dependencies {
                components {
                    withModule('org.test:moduleA') {
                        withVariant("default") {
                            withDependencies {
                                add('org.test:moduleB:1.0')
                            }
                        }
                    }
                }
            }
        """

        then:
        succeeds 'checkDep'
        def variantToTest = variantToTest
        resolve.expectGraph {
            root(':', ':testproject:') {
                module("org.test:moduleA:1.0:$variantToTest")
            }
        }
    }

    def "dependencies of transitive dependencies can be changed"() {
        given:
        setupCustomVariantsForModule(moduleB).publish()
        moduleA.dependsOn(moduleB).publish()
        repo.module("org.test", "moduleC").allowAll().publish()

        when:
        buildFile << """
            dependencies {
                components {
                    withModule('org.test:moduleB') {
                        withVariant('$variantToTest') {
                            withDependencies {
                                add('org.test:moduleC:1.0')
                            }
                        }
                    }
                }
            }
        """

        then:
        succeeds 'checkDep'
        def variantToTest = variantToTest
        resolve.expectGraph {
            root(':', ':testproject:') {
                module("org.test:moduleA:1.0:$variantToTest") {
                    module("org.test:moduleB:1.0") {
                        module('org.test:moduleC:1.0')
                    }
                }
            }
        }
    }

    def "attribute matching is used to select a variant of the dependency's target if the dependency was added by a rule"() {
        given:
        setupCustomVariantsForModule(moduleB).publish()
        moduleA.dependsOn(moduleB).publish()
        repo.module("org.test", "moduleC").allowAll()
        repo.module("org.test", "moduleD").allowAll().publish()

        def mavenGradleRepo = new MavenFileRepository(file("maven-gradle-repo"))
        buildFile << """
            repositories {
                maven {
                    url "$mavenGradleRepo.uri"
                    useGradleMetadata()
                }
            }
        """

        //All dependencies added by rules are of type GradleDependencyMetadata and thus attribute matching is used for selecting the variant/configuration of the dependency's target.
        //Here we add a module with Gradle metadata which defines a variant that uses the same attributes declared in the build script (format: "custom").
        //The dependency to this module is then added using the rule and thus is matched correctly.
        mavenGradleRepo.module("org.test", "moduleC").withModuleMetadata().variant("anotherVariantWithFormatCustom", [format: "custom"]).publish()

        when:
        buildFile << """
            dependencies {
                components {
                    withModule('org.test:moduleB') {
                        withVariant('$variantToTest') {
                            withDependencies {
                                add('org.test:moduleC:1.0')
                            }
                        }
                    }
                    withModule('org.test:moduleC') { //this second rule is here to test that the correct variant was chosen, which is the one adding the dependency to moduleD
                        withVariant('anotherVariantWithFormatCustom') {
                            withDependencies {
                                add('org.test:moduleD:1.0')
                            }
                        }
                    }
                }
            }
        """

        then:
        succeeds 'checkDep'
        def variantToTest = variantToTest
        resolve.expectGraph {
            root(':', ':testproject:') {
                module("org.test:moduleA:1.0:$variantToTest") {
                    module("org.test:moduleB:1.0") {
                        module('org.test:moduleC:1.0') {
                            module('org.test:moduleD:1.0')
                        }
                    }
                }
            }
        }
    }

    def "fails when attempting to select a variant that does not exist"() {
        when:
        buildFile << """
            dependencies {
                components {
                    withModule('org.test:moduleA') {
                        withVariant("testBlue") { }
                    }
                }
            }
        """

        then:
        fails 'checkDep'
        failure.assertHasCause("Variant testBlue is not declared for org.test:moduleA:1.0")
    }

    def "resolving one configuration does not influence the result of resolving another configuration."() {
        given:
        moduleA.dependsOn(moduleB).publish()

        when:
        buildFile << """
            $repoDeclaration
            
            configurations { anotherConfiguration { attributes { attribute(Attribute.of('format', String), 'custom') } } }
            
            dependencies {
                anotherConfiguration group: 'org.test', name: 'moduleA', version: '1.0' ${publishedModulesHaveAttributes ? "" : ", configuration: '$variantToTest'"}
            }

            dependencies {
                components {
                    withModule('org.test:moduleA') {
                        withVariant("$variantToTest") {
                            withDependencies {
                                //check that the dependency has not been removed already during resolution of the other configuration
                                assert it.size() == 1
                                removeAll { true }
                            }
                        }
                    }
                }
            }
        """

        then:
        succeeds 'dependencies'
    }
}

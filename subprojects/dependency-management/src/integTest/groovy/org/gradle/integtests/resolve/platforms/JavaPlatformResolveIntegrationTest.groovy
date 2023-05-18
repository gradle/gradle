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

package org.gradle.integtests.resolve.platforms

import org.gradle.api.JavaVersion
import org.gradle.api.attributes.Category
import org.gradle.integtests.fixtures.AbstractHttpDependencyResolutionTest
import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache
import org.gradle.integtests.fixtures.resolve.ResolveTestFixture
import org.hamcrest.Matchers
import spock.lang.Issue

class JavaPlatformResolveIntegrationTest extends AbstractHttpDependencyResolutionTest {

    ResolveTestFixture resolve

    def setup() {
        settingsFile << "rootProject.name = 'test'"
        buildFile << """
            apply plugin: 'java-library'

            allprojects {
                repositories {
                    maven { url "${mavenHttpRepo.uri}" }
                }
                group = 'org.test'
                version = '1.9'
            }
        """
    }

    def "can get recommendations from a platform subproject"() {
        def module = mavenHttpRepo.module("org", "foo", "1.1").publish()

        given:
        platformModule("""
            constraints {
                api "org:foo:1.1"
            }
        """)

        buildFile << """
            dependencies {
                api platform(project(":platform"))
                api "org:foo"
            }
        """
        checkConfiguration("compileClasspath")

        when:
        module.pom.expectGet()
        module.artifact.expectGet()

        run ":checkDeps"

        then:
        resolve.expectGraph {
            root(":", "org.test:test:1.9") {
                project(":platform", "org.test:platform:1.9") {
                    variant("apiElements", ['org.gradle.usage': 'java-api', 'org.gradle.category': 'platform'])
                    constraint("org:foo:1.1")
                    noArtifacts()
                }
                edge('org:foo', 'org:foo:1.1') {
                    byConstraint()
                }
            }
        }
    }

    def "can get different recommendations from a platform runtime subproject"() {
        def module1 = mavenHttpRepo.module("org", "foo", "1.1").publish()
        def module2 = mavenHttpRepo.module("org", "bar", "1.2").publish()

        given:
        platformModule("""
            constraints {
                api "org:foo:1.1"
                runtime "org:bar:1.2"
            }
        """)

        buildFile << """
            dependencies {
                api platform(project(":platform"))
                api "org:foo"
                runtimeOnly "org:bar"
            }
        """
        checkConfiguration("runtimeClasspath")

        when:
        module1.pom.expectGet()
        module1.artifact.expectGet()
        module2.pom.expectGet()
        module2.artifact.expectGet()

        run ":checkDeps"

        then:
        resolve.expectGraph {
            root(":", "org.test:test:1.9") {
                project(":platform", "org.test:platform:1.9") {
                    variant("runtimeElements", ['org.gradle.usage': 'java-runtime', 'org.gradle.category': 'platform'])
                    constraint("org:foo:1.1")
                    constraint("org:bar:1.2")
                    noArtifacts()
                }
                edge('org:foo', 'org:foo:1.1') {
                    configuration = "runtime"
                    byConstraint()
                }
                edge('org:bar', 'org:bar:1.2') {
                    configuration = "runtime"
                    byConstraint()
                }
            }
        }
    }

    def "fails when using a regular project dependency instead of a platform dependency"() {
        def module = mavenHttpRepo.module("org", "foo", "1.1").publish()

        given:
        platformModule("""
            constraints {
                api "org:foo:1.1"
            }
        """)

        buildFile << """
            java {
                targetCompatibility = JavaVersion.VERSION_1_8
                sourceCompatibility = JavaVersion.VERSION_1_8
            }
            dependencies {
                api project(":platform")
                api "org:foo"
            }
        """
        checkConfiguration("compileClasspath")

        when:
        fails ":checkDeps"

        then:
        failure.assertThatCause(Matchers.startsWith("No matching variant of project :platform was found."))
    }

    def "can enforce a local platform dependency"() {
        def module1 = mavenHttpRepo.module("org", "foo", "1.1").publish()
        def module2 = mavenHttpRepo.module("org", "foo", "1.2").publish()

        given:
        platformModule("""
            constraints {
                api "org:foo:1.1"
            }
        """)

        buildFile << """
            dependencies {
                api enforcedPlatform(project(":platform"))
                api "org:foo:1.2"
            }
        """
        checkConfiguration("compileClasspath")

        when:
        module1.pom.expectGet()
        module2.pom.expectGet()
        module1.artifact.expectGet()

        run ":checkDeps"

        then:
        resolve.expectGraph {
            root(":", "org.test:test:1.9") {
                project(":platform", "org.test:platform:1.9") {
                    variant("enforcedApiElements", ['org.gradle.usage': 'java-api', 'org.gradle.category': 'enforced-platform'])
                    constraint("org:foo:1.1")
                    noArtifacts()
                }
                edge('org:foo:1.2', 'org:foo:1.1') {
                    forced()
                    byConstraint()
                }
            }
        }
    }

    // When publishing a platform, the Gradle metadata will _not_ contain enforced platforms
    // as those are synthetic platforms generated at runtime. This test is here to make sure
    // this is the case
    def "can enforce a published platform"() {
        def platform = mavenHttpRepo.module("org", "platform", "1.0")
            .asGradlePlatform()
            .dependsOn("org", "foo", "1.0")
            .publish()
        def foo10 = mavenHttpRepo.module("org", "foo", "1.0").withModuleMetadata().publish()
        def foo11 = mavenHttpRepo.module("org", "foo", "1.1").withModuleMetadata().publish()

        buildFile << """
            dependencies {
                api enforcedPlatform("org:platform:1.0")
                api "org:foo:1.1"
            }
        """
        checkConfiguration("compileClasspath")

        when:
        platform.pom.expectGet()
        platform.moduleMetadata.expectGet()
        foo11.pom.expectGet()
        foo11.moduleMetadata.expectGet()
        foo10.pom.expectGet()
        foo10.moduleMetadata.expectGet()
        foo10.artifact.expectGet()

        run ":checkDeps"

        then:
        resolve.expectGraph {
            root(":", "org.test:test:1.9") {
                edge("org:platform:{strictly 1.0}", "org:platform:1.0") {
                    configuration = "enforcedApi"
                    variant("enforcedApi", [
                        'org.gradle.usage': 'java-api',
                        'org.gradle.category': 'enforced-platform',
                        'org.gradle.status': 'release',
                    ])
                    module("org:foo:1.0")
                    noArtifacts()
                }
                edge('org:foo:1.1', 'org:foo:1.0') {
                    forced()
                    configuration = 'api'
                }
            }
        }
    }

    @Issue("gradle/gradle#8312")
    def "can resolve a platform with a constraint to determine the platform version"() {
        def platform = mavenHttpRepo.module("org", "platform", "1.0")
            .hasType("pom")
            .allowAll()
            .publish()

        when:
        buildFile << """
            dependencies {
                constraints {
                   api "org:platform:1.0"
                }
                api platform("org:platform") // no version, will select the "platform" component
            }
        """
        checkConfiguration("compileClasspath")

        run ":checkDeps"

        then:
        resolve.expectGraph {
            root(":", "org.test:test:1.9") {
                edge("org:platform", "org:platform:1.0") {
                    variant("platform-compile", [
                        'org.gradle.usage': 'java-api',
                        'org.gradle.category': 'platform',
                        'org.gradle.status': 'release',
                    ])
                    byConstraint()
                    noArtifacts()
                }
                constraint("org:platform:1.0", "org:platform:1.0") {
                    variant("platform-compile", [
                        'org.gradle.usage': 'java-api',
                        'org.gradle.category': 'platform',
                        'org.gradle.status': 'release',
                    ])
                }
            }
        }

    }

    @ToBeFixedForConfigurationCache(because = "serializes the incorrect artifact in ArtifactCollection used by resolve fixture")
    @Issue("gradle/gradle#8312")
    def "can resolve a platform with a constraint to determine the platform version via a transitive constraint"() {
        def platform = mavenHttpRepo.module("org", "platform", "1.0")
            .hasType("pom")
            .allowAll()
            .publish()

        settingsFile << """
            include 'sub'
        """

        when:
        buildFile << """
            dependencies {
                api platform("org:platform") // no version, will select the "platform" component
                api project(":sub")
            }
            project(":sub") {
                apply plugin: 'java-library'
                dependencies {
                    constraints {
                       api "org:platform:1.0"
                    }
                }
            }
        """
        checkConfiguration("compileClasspath")

        run ":checkDeps"

        then:
        resolve.expectGraph {
            root(":", "org.test:test:1.9") {
                edge("org:platform", "org:platform:1.0") {
                    variant("platform-compile", [
                        'org.gradle.usage': 'java-api',
                        'org.gradle.category': 'platform',
                        'org.gradle.status': 'release',
                    ])
                    byConstraint()
                    noArtifacts()
                }
                project(":sub", "org.test:sub:1.9") {
                    variant("apiElements", ['org.gradle.category': 'library',
                                            'org.gradle.dependency.bundling': 'external',
                                            'org.gradle.jvm.version': JavaVersion.current().majorVersion,
                                            'org.gradle.usage': 'java-api',
                                            'org.gradle.libraryelements': 'jar'])
                    constraint("org:platform:1.0", "org:platform:1.0") {
                        variant("platform-compile", [
                            'org.gradle.usage': 'java-api',
                            'org.gradle.category': 'platform',
                            'org.gradle.status': 'release',
                        ])
                    }
                    artifact name: 'main', version: '', extension: '', type: 'java-classes-directory'
                }
            }
        }
    }

    @Issue("gradle/gradle#8548")
    def "enforced platforms should not have any dependency"() {
        def top = mavenHttpRepo.module("org", "top", "1.0")
            .dependsOn("org", "leaf", "1.0")
            .publish()
        def leaf = mavenHttpRepo.module("org", "leaf", "1.0").publish()

        when:
        buildFile << """
            dependencies {
                api enforcedPlatform("org:top:1.0")
            }
        """
        checkConfiguration("compileClasspath")

        top.pom.expectGet()
        run ":checkDeps"

        then:
        resolve.expectGraph {
            root(":", "org.test:test:1.9") {
                edge("org:top:{strictly 1.0}", "org:top:1.0") {
                    variant("enforced-platform-compile", [
                        'org.gradle.category': 'enforced-platform',
                        'org.gradle.status': 'release',
                        'org.gradle.usage': 'java-api'])
                    noArtifacts()
                }
            }
        }
    }

    @Issue("gradle/gradle#11091")
    def "resolves to runtime platform variant of a platform with gradle metadata if no attributes are requested"() {
        def platform = mavenHttpRepo.module("org", "platform", "1.0").asGradlePlatform().publish()

        when:
        buildFile << """
            configurations { conf }
            dependencies {
                conf "org:platform:1.0"
            }
        """
        checkConfiguration("conf")

        platform.pom.expectGet()
        platform.moduleMetadata.expectGet()
        run ":checkDeps"

        then:
        resolve.expectGraph {
            root(":", "org.test:test:1.9") {
                module("org:platform:1.0") {
                    variant("runtime", [
                        'org.gradle.category': 'platform',
                        'org.gradle.status': 'release',
                        'org.gradle.usage': 'java-runtime'])
                    noArtifacts()
                }
            }
        }
    }

    @Issue("gradle/gradle#11091")
    def "can enforce a platform that is already on the dependency graph on the #classpath classpath"() {
        def platform = mavenHttpRepo.module("org", "platform", "1.0").asGradlePlatform().publish()

        when:
        buildFile << """
            dependencies {
                api platform("org:platform:1.0")
                api enforcedPlatform("org:platform:1.0")
            }
        """
        checkConfiguration("${classpath}Classpath")

        platform.pom.expectGet()
        platform.moduleMetadata.expectGet()
        run ":checkDeps"

        then:
        def javaUsage = "java-${usage}"
        def regularVariant = "${usage}"
        def enforcedVariant = "enforced${usage.capitalize()}"
        resolve.expectGraph {
            root(":", "org.test:test:1.9") {
                edge("org:platform:{strictly 1.0}", "org:platform:1.0") {
                    variant(regularVariant, [
                        'org.gradle.category': 'platform',
                        'org.gradle.status': 'release',
                        'org.gradle.usage': javaUsage])
                    noArtifacts()
                }
                module("org:platform:1.0") {
                    variant(enforcedVariant, [
                        'org.gradle.category': 'enforced-platform',
                        'org.gradle.status': 'release',
                        'org.gradle.usage': javaUsage])
                    noArtifacts()
                }
            }
        }

        where:
        classpath | usage
        'compile' | 'api'
        'runtime' | 'runtime'
    }

    def "Can handle a published platform dependency that is resolved to a local platform project"() {
        given:
        file("src/main/java/SomeClass.java") << "public class SomeClass {}"
        platformModule('')
        mavenHttpRepo.module("org.test", "platform", "1.9").asGradlePlatform().publish()
        def moduleA = mavenHttpRepo.module("org.test", "b", "1.9").withModuleMetadata()
            .withVariant("runtime") {
                dependsOn("org.test", "platform", "1.9") {
                    attribute(Category.CATEGORY_ATTRIBUTE.name, Category.REGULAR_PLATFORM)
                }
            }.withVariant("api") {
            dependsOn("org.test", "platform", "1.9") {
                attribute(Category.CATEGORY_ATTRIBUTE.name, Category.REGULAR_PLATFORM)
            }
        }.publish()

        when:
        buildFile << """
            dependencies {
                implementation platform(project(":platform"))
                implementation "org.test:b:1.9"
            }
        """


        moduleA.pom.expectGet()
        moduleA.moduleMetadata.expectGet()
        moduleA.artifact.expectGet()

        then:
        succeeds ":compileJava"
    }

    def 'constraint from platform does not erase excludes (platform: #platform)'() {
        given:
        platformModule("""
        constraints {
            api 'org:foo:1.0'
        }
""")
        def foobaz = mavenHttpRepo.module('org', 'foobaz', '1.0').publish()
        def foobar = mavenHttpRepo.module('org', 'foobar', '1.0').publish()
        def foo = mavenHttpRepo.module('org', 'foo', '1.0').dependsOn(foobar).dependsOn(foobaz).publish()
        def platformGMM = mavenHttpRepo.module("org", "other-platform", "1.0")
            .asGradlePlatform()
            .dependencyConstraint(foo)
            .publish()
        def mavenBom = mavenHttpRepo.module("org", "bom-platform", "1.0")
            .hasType("pom")
            .dependencyConstraint(foo)
            .publish()

        def bar = mavenHttpRepo.module('org', 'bar', '1.0').dependsOn([exclusions: [[module: 'foobaz']]], foo).withModuleMetadata().publish()

        when:
        buildFile << """
            dependencies {
                implementation platform($platform)
                implementation 'org:bar:1.0'
            }
"""
        checkConfiguration("runtimeClasspath")
        platformGMM.allowAll()
        bar.allowAll()
        foo.allowAll()
        foobar.allowAll()
        foobaz.allowAll()
        mavenBom.allowAll()
        run ':checkDeps'

        then:
        resolve.expectGraph {
            root(':', 'org.test:test:1.9') {
                if (platform == "project(':platform')") {
                    project(":platform", "org.test:platform:1.9") {
                        variant("runtimeElements", ['org.gradle.usage': 'java-runtime', 'org.gradle.category': 'platform'])
                        constraint("org:foo:1.0")
                        noArtifacts()
                    }
                } else if (platform == "'org:other-platform:1.0'") {
                    module('org:other-platform:1.0') {
                        variant("runtime", ['org.gradle.usage': 'java-runtime', 'org.gradle.category': 'platform', 'org.gradle.status': 'release'])
                        constraint("org:foo:1.0")
                        noArtifacts()
                    }
                } else if (platform == "'org:bom-platform:1.0'") {
                    module('org:bom-platform:1.0') {
                        variant("platform-runtime", ['org.gradle.usage': 'java-runtime', 'org.gradle.category': 'platform', 'org.gradle.status': 'release'])
                        constraint("org:foo:1.0")
                        noArtifacts()
                    }
                }
                module('org:bar:1.0') {
                    configuration = 'runtime'
                    module('org:foo:1.0') {
                        configuration = 'runtime'
                        byConstraint()
                        module('org:foobar:1.0') {
                            configuration = 'runtime'
                        }
                    }
                }
            }
        }

        where:
        platform << ["project(':platform')", "'org:other-platform:1.0'", "'org:bom-platform:1.0'"]
    }

    def 'platform deselection / reselection does not cause orphan edges'() {
        given:
        def depExcluded = mavenHttpRepo.module('org.test', 'excluded', '1.0').publish()
        def depA = mavenHttpRepo.module('org.test', 'depA', '1.0').publish()
        def platform = mavenHttpRepo.module('org.test', 'platform', '1.0').asGradlePlatform().dependencyConstraint(depA).dependencyConstraint(depExcluded).publish()
        def depC = mavenHttpRepo.module('org.test', 'depC', '1.0').withModuleMetadata()
            .withVariant('runtime') {
                dependsOn('org.test', 'platform', '1.0') {
                    attribute(Category.CATEGORY_ATTRIBUTE.name, Category.REGULAR_PLATFORM)
                }
            }.publish()
        def depB = mavenHttpRepo.module('org.test', 'depB', '1.0').dependsOn([exclusions: [[module: 'excluded']]], depC).publish()
        def depF = mavenHttpRepo.module('org.test', 'depF', '1.0').dependsOn(depC).publish()
        def depE = mavenHttpRepo.module('org.test', 'depE', '1.0').dependsOn(depF).publish()
        def depD = mavenHttpRepo.module('org.test', 'depD', '1.0').dependsOn(depE).publish()

        depExcluded.allowAll()
        depA.allowAll()
        depB.allowAll()
        depC.allowAll()
        depD.allowAll()
        depE.allowAll()
        depF.allowAll()
        platform.allowAll()

        buildFile << """
            configurations {
                conf.dependencies.clear()
            }

            dependencies {
                conf 'org.test:depA'
                conf 'org.test:depB:1.0'
                conf 'org.test:depD:1.0'
            }
"""
        checkConfiguration("conf")
        resolve.expectDefaultConfiguration("runtime")

        when:
        succeeds 'checkDeps'

        then:
        resolve.expectGraph {
            root(":", "org.test:test:1.9") {
                edge("org.test:depA", "org.test:depA:1.0") {
                    byConstraint()
                }
                module("org.test:depB:1.0") {
                    module("org.test:depC:1.0") {
                        module('org.test:platform:1.0') {
                            noArtifacts()
                            constraint('org.test:depA:1.0')
                        }
                    }
                }
                module('org.test:depD:1.0') {
                    module('org.test:depE:1.0') {
                        module('org.test:depF:1.0') {
                            module('org.test:depC:1.0')
                        }
                    }
                }
            }
        }
    }

    def 'platform deselection does not cause orphan edges'() {
        given:
        def depA = mavenHttpRepo.module('org.test', 'depA', '1.0').withModuleMetadata()
            .withVariant('runtime') {
                dependsOn('org.test', 'platform', '1.0') {
                    attribute(Category.CATEGORY_ATTRIBUTE.name, Category.REGULAR_PLATFORM)
                }
                dependsOn('org.test', 'depB', '1.0')
            }.publish()
        def depA11 = mavenHttpRepo.module('org.test', 'depA', '1.1').withModuleMetadata()
            .withVariant('runtime') {
                dependsOn('org.test', 'platform', '1.0') {
                    attribute(Category.CATEGORY_ATTRIBUTE.name, Category.REGULAR_PLATFORM)
                }
                dependsOn('org.test', 'depB', '1.0')
            }.publish()
        def depB = mavenHttpRepo.module('org.test', 'depB', '1.0').withModuleMetadata()
            .withVariant('runtime') {
                dependsOn('org.test', 'platform', '1.0') {
                    attribute(Category.CATEGORY_ATTRIBUTE.name, Category.REGULAR_PLATFORM)
                }
            }.publish()
        def depE = mavenHttpRepo.module('org.test', 'depE', '1.0').withModuleMetadata()
            .withVariant('runtime') {
                dependsOn('org.test', 'otherPlatform', '1.1') {
                    attribute(Category.CATEGORY_ATTRIBUTE.name, Category.REGULAR_PLATFORM)
                }
            }.publish()
        def depD = mavenHttpRepo.module('org.test', 'depD', '1.0').dependsOn(depE).publish()
        def depC = mavenHttpRepo.module('org.test', 'depC', '1.0').dependsOn(depD).publish()
        def depTest = mavenHttpRepo.module('org.test', 'test', '1.9') // Not published as not resolved
        def platform = mavenHttpRepo.module('org.test', 'platform', '1.0').asGradlePlatform().dependencyConstraint(depA11).dependencyConstraint(depB).publish()
        def otherPlatform10 = mavenHttpRepo.module('org.test', 'otherPlatform', '1.0').asGradlePlatform().dependencyConstraint(depD).dependencyConstraint(depTest).publish()
        def otherPlatform11 = mavenHttpRepo.module('org.test', 'otherPlatform', '1.1').asGradlePlatform().dependencyConstraint(depD).dependencyConstraint(depTest).publish()

        depA.allowAll()
        depA11.allowAll()
        depB.allowAll()
        depC.allowAll()
        depD.allowAll()
        depE.allowAll()
        platform.allowAll()
        otherPlatform10.allowAll()
        otherPlatform11.allowAll()

        buildFile << """
            configurations {
                conf.dependencies.clear()
            }

            dependencies {
                conf 'org.test:depA:1.0'
                conf platform('org.test:otherPlatform:1.0')
                conf 'org.test:depC:1.0'
            }
"""
        checkConfiguration("conf")
        resolve.expectDefaultConfiguration("runtime")

        expect:
        succeeds 'checkDeps'
        //Shape of the graph is not checked as bug was failing resolution altogether
    }

    def 'platform upgrade does not leave orphaned edges'() {
        given:
        def depA = mavenHttpRepo.module('org.test', 'depA', '1.0').withModuleMetadata()
            .withVariant('runtime') {
                dependsOn('org.test', 'platform', '1.0') {
                    attribute(Category.CATEGORY_ATTRIBUTE.name, Category.REGULAR_PLATFORM)
                }
            }.publish()
        def depB = mavenHttpRepo.module('org.test', 'depB', '1.0') // Not published as not resolved
        def depA11 = mavenHttpRepo.module('org.test', 'depA', '1.1').withModuleMetadata()
            .withVariant('runtime') {
                dependsOn('org.test', 'platform', '1.1') {
                    attribute(Category.CATEGORY_ATTRIBUTE.name, Category.REGULAR_PLATFORM)
                }
            }.publish()

        def depB11 = mavenHttpRepo.module('org.test', 'depB', '1.1').withModuleMetadata()
            .withVariant('runtime') {
                dependsOn('org.test', 'platform', '1.1') {
                    attribute(Category.CATEGORY_ATTRIBUTE.name, Category.REGULAR_PLATFORM)
                }
                dependsOn('org.test', 'depA', '1.1')
            }.publish()
        def platform = mavenHttpRepo.module('org.test', 'platform', '1.0').asGradlePlatform().dependencyConstraint(depA).dependencyConstraint(depB).publish()
        def platform11 = mavenHttpRepo.module('org.test', 'platform', '1.1').asGradlePlatform().dependencyConstraint(depA11).dependencyConstraint(depB11).publish()

        depA.allowAll()
        depA11.allowAll()
        depB11.allowAll()
        platform.allowAll()
        platform11.allowAll()

        buildFile << """
            configurations {
                conf.dependencies.clear()
            }

            dependencies {
                conf 'org.test:depA:1.0'
                conf 'org.test:depB:1.1'
            }
"""
        checkConfiguration("conf")
        resolve.expectDefaultConfiguration("runtime")

        expect:
        succeeds 'checkDeps'
        //Shape of the graph is not checked as bug was failing resolution altogether
    }

    def "multiple platform deselection - reselection does not leave pending constraints in graph"() {
        given:
        def depCommonsOther = mavenHttpRepo.module('commons', 'other', '2.0').publish()
        def depCommons2 = mavenHttpRepo.module('commons', 'commons', '2.0').publish()
        def depSpring = mavenHttpRepo.module('spring', 'core', '1.0').dependsOn(depCommonsOther).publish()
        def dep = mavenHttpRepo.module('org.test', 'dep', '1.0').dependsOn(depSpring).publish()
        def depsPlatform = mavenHttpRepo.module('org.test', 'deps', '1.0').asGradlePlatform().dependencyConstraint(dep).publish()
        def extPlatform = mavenHttpRepo.module('org.test', 'platform', '1.0').asGradlePlatform()
            .dependencyConstraint(depCommonsOther).dependencyConstraint(depCommons2)
            .withVariant('api') {
                dependsOn('jack', 'bom', '1.0') {
                    endorseStrictVersions = true
                    attribute(Category.CATEGORY_ATTRIBUTE.name, Category.REGULAR_PLATFORM)
                }
                dependsOn('spring', 'bom', '2.0') {
                    endorseStrictVersions = true
                    attribute(Category.CATEGORY_ATTRIBUTE.name, Category.REGULAR_PLATFORM)
                }
            }
            .withVariant('runtime') {
                dependsOn('jack', 'bom', '1.0') {
                    endorseStrictVersions = true
                    attribute(Category.CATEGORY_ATTRIBUTE.name, Category.REGULAR_PLATFORM)
                }
                dependsOn('spring', 'bom', '2.0') {
                    endorseStrictVersions = true
                    attribute(Category.CATEGORY_ATTRIBUTE.name, Category.REGULAR_PLATFORM)
                }
            }.publish()
        def depSpring2 = mavenHttpRepo.module('spring', 'core', '2.0').publish()
        def springBom = mavenHttpRepo.module('spring', 'bom', '2.0').asGradlePlatform().dependencyConstraint(depSpring2).publish()
        def depJackDb1 = mavenHttpRepo.module('jack', 'db', '1.0') //Not published as not resolved
        def jackBom = mavenHttpRepo.module('jack', 'bom', '1.0').asGradlePlatform().dependencyConstraint(depJackDb1).publish()
        def depJackDb = mavenHttpRepo.module('jack', 'db', '2.0').withModuleMetadata()
            .withVariant('runtime') {
                dependsOn('jack', 'bom', '2.0') {
                    attribute(Category.CATEGORY_ATTRIBUTE.name, Category.REGULAR_PLATFORM)
                }
            }.publish()
        def jack2Bom = mavenHttpRepo.module('jack', 'bom', '2.0').asGradlePlatform().dependencyConstraint(depJackDb).publish()
        def depCommons = mavenHttpRepo.module('commons', 'commons', '1.0').publish()
        def depSwagCore = mavenHttpRepo.module('org.test', 'swag-core', '1.0').dependsOn(depCommons).dependsOn(depJackDb).publish()
        def depSwagInt = mavenHttpRepo.module('org.test', 'swag-int', '1.0').dependsOn(depSwagCore).publish()
        def depSwag = mavenHttpRepo.module('org.test', 'swag', '1.0').dependsOn(depJackDb).dependsOn(depSwagInt).publish()


        depsPlatform.allowAll()
        extPlatform.allowAll()
        springBom.allowAll()
        jackBom.allowAll()
        jack2Bom.allowAll()
        depSwag.allowAll()
        depJackDb.allowAll()
        depSwagInt.allowAll()
        depSwagCore.allowAll()
        dep.allowAll()
        depCommons.allowAll()
        depCommons2.allowAll()
        depCommonsOther.allowAll()
        depSpring.allowAll()
        depSpring2.allowAll()

        buildFile << """
            configurations {
                conf.dependencies.clear()
            }

            dependencies {
                conf 'org.test:dep'
                conf 'org.test:swag:1.0'
                conf(platform('org.test:deps:1.0'))
                conf(platform('org.test:platform:1.0'))
            }
"""
        checkConfiguration("conf")
        resolve.expectDefaultConfiguration("runtime")

        expect:
        succeeds 'checkDeps'
        //Shape of the graph is not checked as bug was failing resolution altogether
    }

    @Issue("https://github.com/gradle/gradle/issues/20684")
    @ToBeFixedForConfigurationCache(because = "task uses Configuration API")
    def "multiple platform deselection - reselection does not leave pending constraints in graph - different issue"() {
        given:
        def depJackDb20 = mavenHttpRepo.module('jack', 'db', '2.0').withModuleMetadata()
            .withVariant('runtime') {
                dependsOn('jack', 'bom', '2.0') {
                    endorseStrictVersions = true
                    attribute(Category.CATEGORY_ATTRIBUTE.name, Category.REGULAR_PLATFORM)
                }
            }.publish()
        def depJackDb201 = mavenHttpRepo.module('jack', 'db', '2.0.1').withModuleMetadata()
            .withVariant('runtime') {
                dependsOn('jack', 'bom', '2.0.1') {
                    endorseStrictVersions = true
                    attribute(Category.CATEGORY_ATTRIBUTE.name, Category.REGULAR_PLATFORM)
                }
            }.publish()
        def depJackDb202 = mavenHttpRepo.module('jack', 'db', '2.0.2').withModuleMetadata()
            .withVariant('runtime') {
                dependsOn('jack', 'bom', '2.0') {
                    endorseStrictVersions = true
                    attribute(Category.CATEGORY_ATTRIBUTE.name, Category.REGULAR_PLATFORM)
                }
            }.publish()
        def depJackDx20 = mavenHttpRepo.module('jack', 'dx', '2.0').withModuleMetadata()
            .withVariant('runtime') {
                dependsOn('jack', 'db', '2.0')
                dependsOn('jack', 'bom', '2.0') {
                    endorseStrictVersions = true
                    attribute(Category.CATEGORY_ATTRIBUTE.name, Category.REGULAR_PLATFORM)
                }
            }.publish()
        def jackBom20 = mavenHttpRepo.module('jack', 'bom', '2.0').asGradlePlatform().dependencyConstraint(depJackDb20).dependencyConstraint(depJackDx20).publish()
        def jackBom201 = mavenHttpRepo.module('jack', 'bom', '2.0.1').asGradlePlatform().dependencyConstraint(depJackDb201).dependencyConstraint(depJackDx20).publish()

        def springBom = mavenHttpRepo.module('spring', 'bom', '2.0').asGradlePlatform().dependencyConstraint(depJackDx20).dependencyConstraint(depJackDb201).publish()

        def depSwagCore = mavenHttpRepo.module('org.test', 'swag-core', '1.0').dependsOn(depJackDb202).publish()
        def depSwagInt = mavenHttpRepo.module('org.test', 'swag-int', '1.0').dependsOn(depSwagCore).publish()
        def depSwag = mavenHttpRepo.module('org.test', 'swag', '1.0').dependsOn(depSwagInt).publish()

        depJackDb20.allowAll()
        depJackDb201.allowAll()
        depJackDb202.allowAll()
        depJackDx20.allowAll()
        jackBom20.allowAll()
        jackBom201.allowAll()
        springBom.allowAll()
        depSwagCore.allowAll()
        depSwagInt.allowAll()
        depSwag.allowAll()

        buildFile << """
            configurations {
                conf.dependencies.clear()
            }

            dependencies {
                conf(platform('spring:bom:2.0'))
                conf 'org.test:swag:1.0'
                conf 'jack:dx'
            }

            tasks.register('resolve') {
                def conf = configurations.conf
                doLast {
                    // Need a specific path for restoring serialized version, other paths work
                    println conf.resolvedConfiguration.lenientConfiguration.allModuleDependencies
                }
            }
"""

        expect:
        succeeds 'resolve'
        //Shape of the graph is not checked as bug was failing resolution altogether
    }

    private void checkConfiguration(String configuration) {
        resolve = new ResolveTestFixture(buildFile, configuration)
        resolve.expectDefaultConfiguration("compile")
        resolve.prepare()
    }

    private void platformModule(String dependencies) {
        settingsFile << """
            include "platform"
        """
        file("platform/build.gradle") << """
            plugins {
                id 'java-platform'
            }

            dependencies {
                $dependencies
            }
        """
    }
}

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


import org.gradle.api.attributes.Category
import org.gradle.integtests.fixtures.AbstractHttpDependencyResolutionTest
import org.gradle.integtests.fixtures.resolve.ResolveTestFixture
import org.hamcrest.Matchers

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

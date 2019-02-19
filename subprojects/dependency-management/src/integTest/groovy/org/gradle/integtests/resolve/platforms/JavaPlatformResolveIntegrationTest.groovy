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

import org.gradle.api.attributes.Usage
import org.gradle.api.internal.artifacts.dsl.dependencies.PlatformSupport
import org.gradle.integtests.fixtures.AbstractHttpDependencyResolutionTest
import org.gradle.integtests.fixtures.FeaturePreviewsFixture
import org.gradle.integtests.fixtures.resolve.ResolveTestFixture
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
                    variant("apiElements", ['org.gradle.usage': 'java-api', 'org.gradle.component.category': 'platform'])
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
                    variant("runtimeElements", ['org.gradle.usage': 'java-runtime', 'org.gradle.component.category': 'platform'])
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

    def "reasonable behavior when using a regular project dependency instead of a platform dependency"() {
        def module = mavenHttpRepo.module("org", "foo", "1.1").publish()

        given:
        platformModule("""
            constraints {
                api "org:foo:1.1"
            }
        """)

        buildFile << """
            dependencies {
                api project(":platform")
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
                    variant("apiElements", ['org.gradle.usage': 'java-api', 'org.gradle.component.category': 'platform'])
                    constraint("org:foo:1.1")
                    noArtifacts()
                }
                edge('org:foo', 'org:foo:1.1') {
                    byConstraint()
                }
            }
        }
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
                    variant("enforcedApiElements", ['org.gradle.usage': 'java-api', 'org.gradle.component.category': 'enforced-platform'])
                    constraint("org:foo:1.1")
                    noArtifacts()
                }
                edge('org:foo:1.2', 'org:foo:1.1') {
                    byConstraint()
                    forced()
                }
            }
        }
    }

    // When publishing a platform, the Gradle metadata will _not_ contain enforced platforms
    // as those are synthetic platforms generated at runtime. This test is here to make sure
    // this is the case
    def "can enforce a published platform"() {
        def platform = mavenHttpRepo.module("org", "platform", "1.0")
                .withModuleMetadata()
                .adhocVariants()
                .variant("apiElements", [(Usage.USAGE_ATTRIBUTE.name): Usage.JAVA_API, (PlatformSupport.COMPONENT_CATEGORY.name): PlatformSupport.REGULAR_PLATFORM]) { useDefaultArtifacts = false }
                .dependsOn("org", "foo", "1.0")
                .variant("runtimeElements", [(Usage.USAGE_ATTRIBUTE.name): Usage.JAVA_RUNTIME, (PlatformSupport.COMPONENT_CATEGORY.name): PlatformSupport.REGULAR_PLATFORM]) {
                    useDefaultArtifacts = false
                }
                .dependsOn("org", "foo", "1.0")
                .publish()
        def foo10 = mavenHttpRepo.module("org", "foo", "1.0").withModuleMetadata().publish()
        def foo11 = mavenHttpRepo.module("org", "foo", "1.1").withModuleMetadata().publish()

        FeaturePreviewsFixture.enableGradleMetadata(settingsFile)

        buildFile << """
            dependencies {
                api enforcedPlatform("org:platform:1.0")
                api "org:foo:1.1"
            }
        """
        checkConfiguration("compileClasspath")

        when:
        platform.moduleMetadata.expectGet()
        foo11.moduleMetadata.expectGet()
        foo10.moduleMetadata.expectGet()
        foo10.artifact.expectGet()

        run ":checkDeps"

        then:
        resolve.expectGraph {
            root(":", "org.test:test:1.9") {
                module("org:platform:1.0") {
                    configuration = "enforcedApiElements"
                    variant("enforcedApiElements", [
                            'org.gradle.usage': 'java-api',
                            'org.gradle.component.category': 'enforced-platform',
                            'org.gradle.status': 'release',
                    ])
                    module("org:foo:1.0")
                    noArtifacts()
                }
                edge('org:foo:1.1', 'org:foo:1.0') {
                    configuration = 'api'
                    forced()
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
                api platform("org:platform") // no version, will select the "platform" component
                constraints {
                   api "org:platform:1.0"
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
                            'org.gradle.component.category': 'platform',
                            'org.gradle.status': 'release',
                    ])
                    byConstraint()
                    noArtifacts()
                }
                constraint("org:platform:1.0", "org:platform:1.0") {
                    variant("platform-compile", [
                            'org.gradle.usage': 'java-api',
                            'org.gradle.component.category': 'platform',
                            'org.gradle.status': 'release',
                    ])
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
                module("org:top:1.0") {
                    variant("enforced-platform-compile", [
                            'org.gradle.component.category': 'enforced-platform',
                            'org.gradle.status': 'release',
                            'org.gradle.usage': 'java-api'])
                    noArtifacts()
                }
            }
        }
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

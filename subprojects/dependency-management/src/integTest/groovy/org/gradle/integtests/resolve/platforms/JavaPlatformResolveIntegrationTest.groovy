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
import org.gradle.api.attributes.Usage
import org.gradle.integtests.fixtures.AbstractHttpDependencyResolutionTest
import org.gradle.integtests.fixtures.resolve.ResolveTestFixture
import spock.lang.Issue
import spock.lang.Unroll

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
        failure.assertHasCause('''The consumer was configured to find an API of a library compatible with Java 8, preferably in the form of class files, and its dependencies declared externally but no matching variant of project :platform was found.
  - Variant 'apiElements' capability org.test:platform:1.9:
      - Incompatible attribute:
          - Required a library and found a platform
      - Other compatible attributes:
          - Doesn't say anything about how its dependencies are found (required its dependencies declared externally)
          - Doesn't say anything about its target Java version (required compatibility with Java 8)
          - Doesn't say anything about its elements (required them preferably in the form of class files)
          - Provides an API
  - Variant 'enforcedApiElements' capability org.test:platform-derived-enforced-platform:1.9:
      - Incompatible attribute:
          - Required a library and found an enforced platform
      - Other compatible attributes:
          - Doesn't say anything about how its dependencies are found (required its dependencies declared externally)
          - Doesn't say anything about its target Java version (required compatibility with Java 8)
          - Doesn't say anything about its elements (required them preferably in the form of class files)
          - Provides an API
  - Variant 'enforcedRuntimeElements' capability org.test:platform-derived-enforced-platform:1.9:
      - Incompatible attribute:
          - Required a library and found an enforced platform
      - Other compatible attributes:
          - Doesn't say anything about how its dependencies are found (required its dependencies declared externally)
          - Doesn't say anything about its target Java version (required compatibility with Java 8)
          - Doesn't say anything about its elements (required them preferably in the form of class files)
          - Required an API and found a runtime
  - Variant 'runtimeElements' capability org.test:platform:1.9:
      - Incompatible attribute:
          - Required a library and found a platform
      - Other compatible attributes:
          - Doesn't say anything about how its dependencies are found (required its dependencies declared externally)
          - Doesn't say anything about its target Java version (required compatibility with Java 8)
          - Doesn't say anything about its elements (required them preferably in the form of class files)
          - Required an API and found a runtime''')
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
                .variant("apiElements", [(Usage.USAGE_ATTRIBUTE.name): Usage.JAVA_API, (Category.CATEGORY_ATTRIBUTE.name): Category.REGULAR_PLATFORM]) { useDefaultArtifacts = false }
                .dependsOn("org", "foo", "1.0")
                .variant("runtimeElements", [(Usage.USAGE_ATTRIBUTE.name): Usage.JAVA_RUNTIME, (Category.CATEGORY_ATTRIBUTE.name): Category.REGULAR_PLATFORM]) {
                    useDefaultArtifacts = false
                }
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
                module("org:platform:1.0") {
                    configuration = "enforcedApiElements"
                    variant("enforcedApiElements", [
                            'org.gradle.usage': 'java-api',
                            'org.gradle.category': 'enforced-platform',
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
                    variant("apiElements", ['org.gradle.category':'library',
                                            'org.gradle.dependency.bundling':'external',
                                            'org.gradle.jvm.version': JavaVersion.current().majorVersion,
                                            'org.gradle.usage':'java-api',
                                            'org.gradle.libraryelements': 'jar'])
                    constraint("org:platform:1.0", "org:platform:1.0") {
                        variant("platform-compile", [
                            'org.gradle.usage': 'java-api',
                            'org.gradle.category': 'platform',
                            'org.gradle.status': 'release',
                        ])
                    }
                    artifact(name: 'main', noType: true)
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
        def platform = mavenHttpRepo.module("org", "platform", "1.0").withModuleMetadata().withoutDefaultVariants()
            .withVariant('api') {
                useDefaultArtifacts = false
                attribute(Usage.USAGE_ATTRIBUTE.name, Usage.JAVA_API)
                attribute(Category.CATEGORY_ATTRIBUTE.name, Category.REGULAR_PLATFORM)
            }
            .withVariant('runtime') {
                useDefaultArtifacts = false
                attribute(Usage.USAGE_ATTRIBUTE.name, Usage.JAVA_RUNTIME)
                attribute(Category.CATEGORY_ATTRIBUTE.name, Category.REGULAR_PLATFORM)
            }.publish()

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
    @Unroll
    def "can enforce a platform that is already on the dependency graph on the #classpath classpath"() {
        def platform = mavenHttpRepo.module("org", "platform", "1.0").withModuleMetadata().withoutDefaultVariants()
            .withVariant('apiElements') {
                useDefaultArtifacts = false
                attribute(Usage.USAGE_ATTRIBUTE.name, Usage.JAVA_API)
                attribute(Category.CATEGORY_ATTRIBUTE.name, Category.REGULAR_PLATFORM)
            }
            .withVariant('runtimeElements') {
                useDefaultArtifacts = false
                attribute(Usage.USAGE_ATTRIBUTE.name, Usage.JAVA_RUNTIME)
                attribute(Category.CATEGORY_ATTRIBUTE.name, Category.REGULAR_PLATFORM)
            }.publish()

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
        def regularVariant = "${usage}Elements"
        def enforcedVariant = "enforced${usage.capitalize()}Elements"
        resolve.expectGraph {
            root(":", "org.test:test:1.9") {
                module("org:platform:1.0") {
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
        mavenHttpRepo.module("org.test", "platform", "1.9").withModuleMetadata().withoutDefaultVariants()
            .withVariant('apiElements') {
                useDefaultArtifacts = false
                attribute(Usage.USAGE_ATTRIBUTE.name, Usage.JAVA_API)
                attribute(Category.CATEGORY_ATTRIBUTE.name, Category.REGULAR_PLATFORM)
            }
            .withVariant('runtimeElements') {
                useDefaultArtifacts = false
                attribute(Usage.USAGE_ATTRIBUTE.name, Usage.JAVA_RUNTIME)
                attribute(Category.CATEGORY_ATTRIBUTE.name, Category.REGULAR_PLATFORM)
            }.publish()
        def moduleA = mavenHttpRepo.module("org.test", "b", "1.9").withModuleMetadata().withVariant("runtime") {
            dependsOn("org.test", "platform", "1.9", null, [(Category.CATEGORY_ATTRIBUTE.name): Category.REGULAR_PLATFORM])
        }.withVariant("api") {
            dependsOn("org.test", "platform", "1.9", null, [(Category.CATEGORY_ATTRIBUTE.name): Category.REGULAR_PLATFORM])
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

    @Unroll
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
            .withModuleMetadata()
            .adhocVariants()
            .variant("apiElements", [(Usage.USAGE_ATTRIBUTE.name): Usage.JAVA_API, (Category.CATEGORY_ATTRIBUTE.name): Category.REGULAR_PLATFORM]) { useDefaultArtifacts = false }
            .dependencyConstraint(foo)
            .variant("runtimeElements", [(Usage.USAGE_ATTRIBUTE.name): Usage.JAVA_RUNTIME, (Category.CATEGORY_ATTRIBUTE.name): Category.REGULAR_PLATFORM]) {
                useDefaultArtifacts = false
            }
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
                        variant("runtimeElements", ['org.gradle.usage': 'java-runtime', 'org.gradle.category': 'platform', 'org.gradle.status': 'release'])
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

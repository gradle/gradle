/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.java.fixtures

import org.gradle.integtests.fixtures.ToBeFixedForIsolatedProjects
import org.gradle.integtests.fixtures.resolve.ResolveTestFixture
import org.gradle.test.fixtures.GradleModuleMetadata
import org.gradle.test.fixtures.maven.MavenPom

/**
 * Base class for integration tests for the `java-test-fixtures` plugin that involve `java` or `java-library` projects.
 */
abstract class AbstractJavaProjectTestFixturesIntegrationTest extends AbstractTestFixturesIntegrationTest {
    abstract String getPluginName()

    abstract List getSkippedJars(boolean compileClasspathPackaging)

    def setup() {
        settingsFile << """
            rootProject.name = 'root'
        """

        buildFile << """
            allprojects {
                apply plugin: '${pluginName}'

                ${mavenCentralRepository()}

                dependencies { testImplementation 'junit:junit:4.13' }
            }
        """
    }

    private toggleCompileClasspathPackaging(boolean activate) {
        if (activate) {
            propertiesFile << """
                systemProp.org.gradle.java.compile-classpath-packaging=true
            """.trim()
        }
    }

    def "can compile test fixtures [compileClasspathPackaging=#compileClasspathPackaging]"() {
        toggleCompileClasspathPackaging(compileClasspathPackaging)
        buildFile << """
            apply plugin: 'java-test-fixtures'

            version = '1.0'
        """
        dumpCompileAndRuntimeTestClasspath()
        addPersonDomainClass()
        addPersonTestFixture()
        addPersonTestUsingTestFixtures()

        when:
        succeeds 'compileTestJava'

        then:
        def skippedJars = getSkippedJars(compileClasspathPackaging)
        def producedJars = [':jar', ':testFixturesJar'] - skippedJars
        executedAndNotSkipped(
                ":compileJava",
                ":compileTestFixturesJava",
                ":compileTestJava",
                *producedJars
        )
        notExecuted(*skippedJars)
        outputContains """Test compile classpath
---
${compileClasspathPackaging ? 'libs/root-1.0-test-fixtures.jar' : 'classes/java/testFixtures'}
${pluginName == 'java' || compileClasspathPackaging ? 'libs/root-1.0.jar' : 'classes/java/main'}
junit-4.13.jar
hamcrest-core-1.3.jar
---
"""

        when:
        succeeds "test"

        then:
        def expectedJars = [':jar', ':testFixturesJar'] - producedJars
        executedAndNotSkipped(*expectedJars)
        outputContains """Test runtime classpath
---
classes/java/test
resources/test
libs/root-1.0-test-fixtures.jar
libs/root-1.0.jar
junit-4.13.jar
hamcrest-core-1.3.jar
---
"""
        where:
        compileClasspathPackaging | _
        false                     | _
        true                      | _
    }

    def "test fixtures can use their own dependencies [compileClasspathPackaging=#compileClasspathPackaging]"() {
        toggleCompileClasspathPackaging(compileClasspathPackaging)
        buildFile << """
            apply plugin: 'java-test-fixtures'

            dependencies {
                testFixturesImplementation 'org.apache.commons:commons-lang3:3.9'
            }
        """
        addPersonDomainClass()
        addPersonTestFixtureUsingApacheCommons()
        addPersonTestUsingTestFixtures()

        when:
        succeeds 'compileTestJava'

        then:
        def skippedJars = getSkippedJars(compileClasspathPackaging)
        def producedJars = [':jar', ':testFixturesJar'] - skippedJars
        executedAndNotSkipped(
                ":compileJava",
                ":compileTestFixturesJava",
                ":compileTestJava",
                *producedJars
        )
        notExecuted(*skippedJars)

        when:
        succeeds "test"

        then:
        def expectedJars = [':jar', ':testFixturesJar'] - producedJars
        executedAndNotSkipped(*expectedJars)

        where:
        compileClasspathPackaging | _
        false                     | _
        true                      | _
    }

    def "test fixtures implementation dependencies do not leak into the test compile classpath"() {
        buildFile << """
            apply plugin: 'java-test-fixtures'

            dependencies {
                testFixturesImplementation 'org.apache.commons:commons-lang3:3.9'
            }
        """
        addPersonDomainClass()
        addPersonTestFixtureUsingApacheCommons()
        addPersonTestUsingTestFixtures()
        file("src/test/java/org/Leaking.java") << """
            package org;
            import org.apache.commons.lang3.StringUtils;

            public class Leaking {
            }
        """
        when:
        fails 'compileTestJava'

        then:
        failure.assertHasCause("Compilation failed")
        errorOutput.contains("package org.apache.commons.lang3 does not exist")
    }

    def "test fixtures api dependencies are visible on the test compile classpath"() {
        buildFile << """
            apply plugin: 'java-test-fixtures'

            dependencies {
                testFixturesApi 'org.apache.commons:commons-lang3:3.9'
            }
        """
        addPersonDomainClass()
        addPersonTestFixtureUsingApacheCommons()
        addPersonTestUsingTestFixtures()
        file("src/test/java/org/Leaking.java") << """
            package org;
            import org.apache.commons.lang3.StringUtils;

            public class Leaking {
            }
        """

        expect:
        succeeds 'compileTestJava'
    }

    @ToBeFixedForIsolatedProjects(because = "allprojects, capabilities are not IP safe")
    def "can consume test fixtures of subproject"() {
        settingsFile << """
            include 'sub'
        """
        file("sub/build.gradle") << """
            apply plugin: 'java-test-fixtures'
        """
        buildFile << """
            dependencies {
                testImplementation(testFixtures(project(":sub")))
            }
        """
        addPersonDomainClass("sub")
        addPersonTestFixture("sub")
        // the test will live in the current project, instead of "sub"
        // which demonstrates that the test fixtures are exposed
        addPersonTestUsingTestFixtures()

        when:
        succeeds ':compileTestJava'

        then:
        executedAndNotSkipped(
                ":sub:compileTestFixturesJava"
        )
    }

    @ToBeFixedForIsolatedProjects(because = "allprojects, capabilities are not IP safe")
    def "changing coordinates of subproject doesn't break consumption of fixtures"() {
        settingsFile << """
            include 'sub'
        """
        file("sub/build.gradle") << """
            apply plugin: 'java-test-fixtures'

            group = 'other' // this is applied _after_ the dependency is created
        """
        buildFile << """
            dependencies {
                testImplementation(testFixtures(project(":sub")))
            }
        """
        addPersonDomainClass("sub")
        addPersonTestFixture("sub")
        // the test will live in the current project, instead of "sub"
        // which demonstrates that the test fixtures are exposed
        addPersonTestUsingTestFixtures()

        when:
        succeeds ':compileTestJava'

        then:
        executedAndNotSkipped(
                ":sub:compileTestFixturesJava"
        )
    }

    def "can publish test fixtures"() {
        buildFile << """
            apply plugin: 'maven-publish'
            apply plugin: 'java-test-fixtures'

            dependencies {
                testFixturesImplementation 'org.apache.commons:commons-lang3:3.9'
            }

            publishing {
                repositories {
                    maven {
                        url "\${buildDir}/repo"
                    }
                    publications {
                        maven(MavenPublication) {
                            from components.java
                        }
                    }
                }
            }

            group = 'com.acme'
            version = '1.3'
        """
        addPersonDomainClass()
        addPersonTestFixtureUsingApacheCommons()
        addPersonTestUsingTestFixtures()

        when:
        succeeds 'publish'

        then: "a test fixtures jar is published"
        file("build/repo/com/acme/root/1.3/root-1.3-test-fixtures.jar").exists()

        and: "appears as optional dependency in Maven POM"
        MavenPom pom = new MavenPom(file("build/repo/com/acme/root/1.3/root-1.3.pom"))
        pom.scope("runtime") {
            assertOptionalDependencies(
                    "org.apache.commons:commons-lang3:3.9"
            )
        }

        and: "appears as a variant in Gradle Module metadata"
        GradleModuleMetadata gmm = new GradleModuleMetadata(file("build/repo/com/acme/root/1.3/root-1.3.module"))
        gmm.variant("testFixturesApiElements") {
            dependency("com.acme:root:1.3")
            noMoreDependencies()
        }
        gmm.variant("testFixturesRuntimeElements") {
            dependency("com.acme:root:1.3")
            dependency("org.apache.commons:commons-lang3:3.9")
            noMoreDependencies()
        }
    }

    def "can deactivate test fixture publishing"() {
        buildFile << """
            apply plugin: 'maven-publish'
            apply plugin: 'java-test-fixtures'

            dependencies {
                testFixturesImplementation 'org.apache.commons:commons-lang3:3.9'
            }

            components.java.withVariantsFromConfiguration(configurations.testFixturesApiElements) { skip() }
            components.java.withVariantsFromConfiguration(configurations.testFixturesRuntimeElements) { skip() }

            publishing {
                repositories {
                    maven {
                        url "\${buildDir}/repo"
                    }
                    publications {
                        maven(MavenPublication) {
                            from components.java
                        }
                    }
                }
            }

            group = 'com.acme'
            version = '1.3'
        """
        addPersonDomainClass()
        addPersonTestFixtureUsingApacheCommons()
        addPersonTestUsingTestFixtures()

        when:
        succeeds 'publish'

        then: "a test fixtures jar is not published"
        !file("build/repo/com/acme/root/1.3/root-1.3-test-fixtures.jar").exists()

        and: "doe not appear as optional dependency in Maven POM"
        MavenPom pom = new MavenPom(file("build/repo/com/acme/root/1.3/root-1.3.pom"))
        pom.scopes.isEmpty()

        and: "does not appear as a variant in Gradle Module metadata"
        GradleModuleMetadata gmm = new GradleModuleMetadata(file("build/repo/com/acme/root/1.3/root-1.3.module"))
        !gmm.variants.any { it.name == "testFixturesApiElements" }
        !gmm.variants.any { it.name == "testFixturesRuntimeElements" }
        gmm.variants.size() == 2
    }

    def "can consume test fixtures of an external module"() {
        mavenRepo.module("com.acme", "external-module", "1.3")
                .variant("testFixturesApiElements", ['org.gradle.usage': 'java-api', 'org.gradle.libraryelements': 'jar']) {
                    capability('com.acme', 'external-module-test-fixtures', '1.3')
                    dependsOn("com.acme:external-module:1.3")
                    artifact("external-module-1.3-test-fixtures.jar")
                }
                .variant("testFixturesRuntimeElements", ['org.gradle.usage': 'java-runtime', 'org.gradle.libraryelements': 'jar']) {
                    capability('com.acme', 'external-module-test-fixtures', '1.3')
                    dependsOn("com.acme:external-module:1.3")
                    dependsOn("org.apache.commons:commons-lang3:3.9")
                    artifact("external-module-1.3-test-fixtures.jar")
                }
                .withModuleMetadata()
                .publish()
        buildFile << """
            dependencies {
                testImplementation(testFixtures('com.acme:external-module:1.3'))
            }
            repositories {
                maven {
                    url "${mavenRepo.uri}"
                }
            }
        """
        when:
        def resolve = new ResolveTestFixture(buildFile, "testCompileClasspath")
        resolve.prepare()
        succeeds ':checkdeps'

        then:
        resolve.expectGraph {
            root(":", ":root:unspecified") {
                module('junit:junit:4.13') {
                    configuration = 'compile' // external POM
                    module("org.hamcrest:hamcrest-core:1.3")
                }
                module('com.acme:external-module:1.3') {
                    variant("testFixturesApiElements", [
                            'org.gradle.status': 'release', 'org.gradle.usage': 'java-api', 'org.gradle.libraryelements': 'jar'
                    ])
                    firstLevelConfigurations = ['testFixturesApiElements']
                    module('com.acme:external-module:1.3') {
                        variant("api", ['org.gradle.status': 'release', 'org.gradle.usage': 'java-api', 'org.gradle.libraryelements': 'jar', 'org.gradle.category': 'library'])
                        artifact(name: 'external-module')
                    }
                    artifact(name: 'external-module', classifier: 'test-fixtures')
                }
            }
        }

        when:
        resolve = new ResolveTestFixture(buildFile, "testRuntimeClasspath")
        resolve.prepare()
        succeeds ':checkdeps'

        then:
        resolve.expectGraph {
            root(":", ":root:unspecified") {
                module('junit:junit:4.13') {
                    configuration = 'runtime' // external POM
                    module("org.hamcrest:hamcrest-core:1.3")
                }
                module('com.acme:external-module:1.3') {
                    variant("testFixturesRuntimeElements", [
                            'org.gradle.status': 'release', 'org.gradle.usage': 'java-runtime', 'org.gradle.libraryelements': 'jar'
                    ])
                    firstLevelConfigurations = ['testFixturesRuntimeElements']
                    module('com.acme:external-module:1.3') {
                        variant("runtime", ['org.gradle.status': 'release', 'org.gradle.usage': 'java-runtime', 'org.gradle.libraryelements': 'jar', 'org.gradle.category': 'library'])
                        artifact(name: 'external-module')
                    }
                    module("org.apache.commons:commons-lang3:3.9") {
                        configuration = 'runtime' // external POM
                    }
                    artifact(name: 'external-module', classifier: 'test-fixtures')
                }
            }
        }
    }

    def "test fixtures feature is added to java component"() {
        given:
        buildFile << """
            apply plugin: 'java-test-fixtures'

            task verify {
                components.java.features {
                    assert size() == 2
                    assert main.sourceSet == sourceSets.main
                    assert testFixtures.sourceSet == sourceSets.testFixtures
                }
            }
        """

        expect:
        succeeds("verify")
    }
}

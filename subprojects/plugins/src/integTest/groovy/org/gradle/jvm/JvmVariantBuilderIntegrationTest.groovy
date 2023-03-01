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

package org.gradle.jvm

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.test.fixtures.maven.AbstractMavenModule
import org.gradle.test.fixtures.maven.MavenFileRepository
import spock.lang.Unroll

class JvmVariantBuilderIntegrationTest extends AbstractIntegrationSpec {
    MavenFileRepository publishRepo
    AbstractMavenModule module

    def setup() {
        settingsFile << """
            rootProject.name = 'mylib'
        """
        buildFile << """
            plugins {
                id 'java-library'
                id 'java-test-fixtures'
                id 'maven-publish'
            }
            def jvm = project.services.get(org.gradle.api.plugins.jvm.internal.JvmModelingServices)

            group = 'com.acme'
            version = '1.4'

            ${mavenCentralRepository()}

            jvm.createJvmVariant("integTest", sourceSets.create("integTest")) {}

            configurations {
                integTestImplementation.extendsFrom(testImplementation)
                integTestRuntimeOnly.extendsFrom(testRuntimeOnly)
            }

            dependencies {
                testImplementation 'junit:junit:4.13'
            }

            tasks.register("integTest", Test) {
                description = "Runs the integration tests."
                group = "verification"
                testClassesDirs = sourceSets.integTest.output.classesDirs
                classpath = sourceSets.integTest.runtimeClasspath
            }

            publishing {
                repositories {
                    maven { url "\${buildDir}/repo" }
                }
                publications {
                    maven(MavenPublication) {
                        from components.java
                    }
                }
            }
        """

        publishRepo = new MavenFileRepository(
            testDirectory.file("build/repo")
        )
        module = publishRepo.module('com.acme', 'mylib', '1.4')
    }

    /**
     * This test exercises the creation of a secondary component
     * similar to "test fixtures" but where the fixtures are only
     * available to integration tests
     */
    @Unroll("can register integration test fixtures (published=#published, javadocs=#javadocs, sources=#sources)")
    def "can register integration test fixtures"() {
        buildFile << """
            sourceSets {
                integTestFixtures
            }
            jvm.createJvmVariant("integTestFixtures", sourceSets.integTestFixtures) {
                distinctCapability()
                withDisplayName("Integration test fixtures")
                if ($published) { published() }
                if ($javadocs) { withJavadocJar() }
                if ($sources) { withSourcesJar() }
            }

            dependencies {
                integTestImplementation(project(":")) {
                    capabilities {
                        requireCapability 'com.acme:mylib-integ-test-fixtures'
                    }
                }
            }
        """


        file("src/integTest/java/MyTest.java") << """
            import org.junit.Test;
            import static org.junit.Assert.*;

            public class MyTest {
                @Test
                public void testSomething() {
                    int answer = MyFixture.answer();
                    assertEquals(answer, 42);
                }
            }
        """

        file("src/integTestFixtures/java/MyFixture.java") << """
            public class MyFixture {
                public static int answer() { return 42; }
            }
        """

        when:
        succeeds ':compileIntegTestJava'

        then:
        executedAndNotSkipped ":compileIntegTestFixturesJava", ":compileIntegTestJava"
        notExecuted ":integTestFixturesJar"

        when:
        succeeds ":integTest"

        then:
        executedAndNotSkipped ":integTestFixturesJar"

        when:
        succeeds ':publishMavenPublicationToMavenRepository'

        then:
        module.assertPublished()
        def expectedArtifacts = [
            'mylib-1.4.pom',
            'mylib-1.4.module',
            'mylib-1.4.jar',
            'mylib-1.4-test-fixtures.jar'
        ]
        if (published) {
            expectedArtifacts.add('mylib-1.4-integ-test-fixtures.jar')
        }
        if (javadocs) {
            expectedArtifacts.add('mylib-1.4-integ-test-fixtures-javadoc.jar')
        }
        if (sources) {
            expectedArtifacts.add('mylib-1.4-integ-test-fixtures-sources.jar')
        }
        module.assertArtifactsPublished(expectedArtifacts)

        and:
        def md = module.parsedModuleMetadata
        if (published) {
            md.variant('integTestFixturesRuntimeElements') {
                capability('com.acme', 'mylib-integ-test-fixtures', '1.4')
                file("mylib-1.4-integ-test-fixtures.jar")
                noMoreDependencies()
            }
            md.variant('integTestFixturesApiElements') {
                capability('com.acme', 'mylib-integ-test-fixtures', '1.4')
                file("mylib-1.4-integ-test-fixtures.jar")
                noMoreDependencies()
            }
            if (javadocs) {
                md.variant('integTestFixturesJavadocElements') {
                    capability('com.acme', 'mylib-integ-test-fixtures', '1.4')
                    file("mylib-1.4-integ-test-fixtures-javadoc.jar")
                    noMoreDependencies()
                }
            }
            if (sources) {
                md.variant('integTestFixturesSourcesElements') {
                    capability('com.acme', 'mylib-integ-test-fixtures', '1.4')
                    file("mylib-1.4-integ-test-fixtures-sources.jar")
                    noMoreDependencies()
                }
            }
        }

        where:
        published | javadocs | sources
        false     | false    | false
        true      | false    | false
        true      | true     | false
        true      | false    | true
        true      | true     | true
    }
}

/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.buildinit.plugins

import org.gradle.api.JavaVersion
import org.gradle.buildinit.plugins.internal.modifiers.BuildInitDsl
import org.gradle.internal.jvm.Jvm
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.UnitTestPreconditions

/**
 * MavenConversionIntegrationTest tests that use a dynamically-generated POM to ensure cross-version compatibility.
 */
abstract class MavenConversionDynamicPomIntegrationTest extends AbstractInitIntegrationSpec {

    @Override
    String subprojectName() { null }

    abstract BuildInitDsl getScriptDsl()

    def setup() {
        /**
         * We need to configure the local maven repository explicitly as
         * RepositorySystem.defaultUserLocalRepository is statically initialised and used when
         * creating multiple ProjectBuildingRequest.
         * */
        m2.generateUserSettingsFile(m2.mavenRepo())
        using m2

        targetDir.file("src/main/java/Foo.java") << """
            import org.apache.commons.lang.StringUtils;

            public class Foo {
              public String toString() {
                return StringUtils.normalizeSpace("hi  there!");
              }
            }
        """
        targetDir.file("src/test/java/FooTest.java") << """
            import org.junit.Test;

            public class FooTest {
              @Test public void test() {
                assert false: "test failure";
              }
            }
        """
    }

    def "singleModule with same source and target"() {
        def source = Jvm.current().javaVersion
        def target = Jvm.current().javaVersion
        writePom(source, target)

        def dsl = dslFixtureFor(scriptDsl)

        when:
        run 'init', '--dsl', scriptDsl.id as String

        then:
        dsl.assertGradleFilesGenerated()
        dsl.getSettingsFile().text.contains("rootProject.name = 'util'") || dsl.getSettingsFile().text.contains('rootProject.name = "util"')
        MavenConversionIntegrationTest.assertContainsPublishingConfig(dsl.getBuildFile(), scriptDsl)
        dsl.getBuildFile(targetDir).text.contains("java.sourceCompatibility = JavaVersion.${source.name()}")
        !dsl.getBuildFile(targetDir).text.contains("java.targetCompatibility = ")

        when:
        fails 'clean', 'build'

        then:
        // when tests fail, jar may not exist
        failure.assertHasDescription("Execution failed for task ':test'.")
        failure.assertHasCause("There were failing tests.")
    }

    def "singleModule with different source and target"() {
        def source = Jvm.current().javaVersion.previous() as JavaVersion
        def target = Jvm.current().javaVersion
        writePom(source, target)

        def dsl = dslFixtureFor(scriptDsl)

        when:
        run 'init', '--dsl', scriptDsl.id as String

        then:
        dsl.assertGradleFilesGenerated()
        dsl.getSettingsFile().text.contains("rootProject.name = 'util'") || dsl.getSettingsFile().text.contains('rootProject.name = "util"')
        MavenConversionIntegrationTest.assertContainsPublishingConfig(dsl.getBuildFile(), scriptDsl)
        dsl.getBuildFile(targetDir).text.contains("java.sourceCompatibility = JavaVersion.${source.name()}")
        dsl.getBuildFile(targetDir).text.contains("java.targetCompatibility = JavaVersion.${target.name()}")

        when:
        fails 'clean', 'build'

        then:
        // when tests fail, jar may not exist
        failure.assertHasDescription("Execution failed for task ':test'.")
        failure.assertHasCause("There were failing tests.")
    }

    @Requires(UnitTestPreconditions.Jdk9OrLater)
    def "singleModule with just source"() {
        def source = Jvm.current().javaVersion
        writePom(source, null)

        def dsl = dslFixtureFor(scriptDsl)

        when:
        run 'init', '--dsl', scriptDsl.id as String

        then:
        dsl.assertGradleFilesGenerated()
        dsl.getSettingsFile().text.contains("rootProject.name = 'util'") || dsl.getSettingsFile().text.contains('rootProject.name = "util"')
        MavenConversionIntegrationTest.assertContainsPublishingConfig(dsl.getBuildFile(), scriptDsl)
        dsl.getBuildFile(targetDir).text.contains("java.sourceCompatibility = JavaVersion.${source.name()}")
        // target defaults to 1.8
        dsl.getBuildFile(targetDir).text.contains("java.targetCompatibility = JavaVersion.VERSION_1_8")

        when:
        fails 'clean', 'build'

        then:
        // if the source and target are different, we can't actually compile because javac requires them to be the same
        failure.assertHasDescription("Execution failed for task ':compileJava'.")
        // May or may not be part of the failure cause (varies by javac version)
        errorOutput.contains("source release ${source} requires target release ${source}")
    }

    @Requires(UnitTestPreconditions.Jdk9OrLater)
    def "singleModule with just target"() {
        def target = Jvm.current().javaVersion
        writePom(null, target)

        def dsl = dslFixtureFor(scriptDsl)

        when:
        run 'init', '--dsl', scriptDsl.id as String

        then:
        dsl.assertGradleFilesGenerated()
        dsl.getSettingsFile().text.contains("rootProject.name = 'util'") || dsl.getSettingsFile().text.contains('rootProject.name = "util"')
        MavenConversionIntegrationTest.assertContainsPublishingConfig(dsl.getBuildFile(), scriptDsl)
        // source defaults to 1.8
        dsl.getBuildFile(targetDir).text.contains("java.sourceCompatibility = JavaVersion.VERSION_1_8")
        dsl.getBuildFile(targetDir).text.contains("java.targetCompatibility = JavaVersion.${target.name()}")

        when:
        fails 'clean', 'build'

        then:
        // when tests fail, jar may not exist
        failure.assertHasDescription("Execution failed for task ':test'.")
        failure.assertHasCause("There were failing tests.")
    }

    private writePom(source, target) {
        // Dynamically generated POM file, based on MavenConversionIntegrationTest#singleModule pom.xml
        targetDir.file("pom.xml") << """
            <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
                <modelVersion>4.0.0</modelVersion>

                <groupId>util</groupId>
                <artifactId>util</artifactId>
                <version>2.5</version>
                <packaging>jar</packaging>

                <dependencies>
                    <dependency>
                        <groupId>commons-lang</groupId>
                        <artifactId>commons-lang</artifactId>
                        <version>2.6</version>
                    </dependency>
                    <dependency>
                        <groupId>junit</groupId>
                        <artifactId>junit</artifactId>
                        <version>4.13.1</version>
                        <scope>test</scope>
                    </dependency>
                </dependencies>
                <build>
                    <plugins>
                        <plugin>
                            <groupId>org.apache.maven.plugins</groupId>
                            <artifactId>maven-compiler-plugin</artifactId>
                            <configuration>
                                ${source == null ? "" : "<source>${source}</source>"}
                                ${target == null ? "" : "<target>${target}</target>"}
                            </configuration>
                        </plugin>
                    </plugins>
                </build>
            </project>
        """
    }
}

class KotlinDslMavenConversionDynamicPomIntegrationTest extends MavenConversionDynamicPomIntegrationTest {
    BuildInitDsl scriptDsl = BuildInitDsl.KOTLIN
}

class GroovyDslMavenConversionDynamicPomIntegrationTest extends MavenConversionDynamicPomIntegrationTest {
    BuildInitDsl scriptDsl = BuildInitDsl.GROOVY
}

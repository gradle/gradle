/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.api.tasks.compile

import org.gradle.api.JavaVersion
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.AvailableJavaHomes
import org.gradle.integtests.fixtures.DefaultTestExecutionResult
import org.gradle.test.fixtures.archive.JarTestFixture
import org.gradle.util.GradleVersion
import org.junit.Assume

import static org.gradle.internal.classanalysis.JavaClassUtil.getClassMajorVersion

class CrossCompilationIntegrationTest extends AbstractIntegrationSpec {
    def "can configure the Java plugin to compile and run tests against Java #version JDK"() {
        def jvm = AvailableJavaHomes.getJdk(version)
        Assume.assumeTrue(jvm != null)

        settingsFile << "rootProject.name = 'oldjava'"
        buildFile << """
            plugins {
                id "java"
            }
            ${mavenCentralRepository()}
            dependencies {
                testImplementation("junit:junit:4.13")
            }
            java {
                toolchain {
                    languageVersion = JavaLanguageVersion.of(${version.majorVersion})
                }
            }
        """

        def sourceFile = file("src/main/java/Thing.java")
        sourceFile << """
            import java.util.List;
            import java.util.ArrayList;

            public class Thing {
                public Thing() {
                    // Uses Java 8 features
                    Runnable r = () -> { };
                    r.run();
                }
            }
        """

        def testFile = file("src/test/java/ThingTest.java")
        testFile << """
            import org.junit.Test;
            import org.junit.Assert;
            import java.io.File;

            public class ThingTest {
                @Test
                public void check() {
                    Assert.assertTrue(new File(System.getProperty("java.home")).toURI().toString().startsWith("${jvm.javaHome.toURI()}"));
                    Assert.assertTrue(System.getProperty("java.version").startsWith("${version}."));
                    new Thing();
                }
            }
        """

        when:
        executer.beforeExecute({
            withArgument("-Porg.gradle.java.installations.paths=" + jvm.getJavaHome().getAbsolutePath())
        })
        fails("build")

        then:
        failure.assertHasDescription("Execution failed for task ':compileJava'.")
        failure.assertHasCause("Compilation failed with exit code")

        when:
        sourceFile.replace("() -> { }", "new Runnable() { public void run() { } }")
        executer.expectDeprecationWarning("Running tests on Java versions earlier than 8 has been deprecated. This will fail with an error in Gradle 9.0. Consult the upgrading guide for further information: https://docs.gradle.org/${GradleVersion.current().version}/userguide/upgrading_version_8.html#minimum_test_jvm_version")
        run("build")

        then:
        new JarTestFixture(file("build/libs/oldjava.jar")).javaVersion == version
        getClassMajorVersion(file("build/classes/java/main/Thing.class")) == getClassMajorVersion(version)
        getClassMajorVersion(file("build/classes/java/test/ThingTest.class")) == getClassMajorVersion(version)
        new DefaultTestExecutionResult(testDirectory).assertTestClassesExecuted("ThingTest")

        where:
        version << [JavaVersion.VERSION_1_6, JavaVersion.VERSION_1_7]
    }
}

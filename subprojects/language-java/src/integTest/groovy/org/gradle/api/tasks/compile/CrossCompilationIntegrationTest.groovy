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
import org.gradle.test.fixtures.file.ClassFile
import org.junit.Assume
import spock.lang.Unroll

class CrossCompilationIntegrationTest extends AbstractIntegrationSpec {
    @Unroll
    def "can configure the Java plugin to compile and run tests against Java #version JDK"() {
        def jvm = AvailableJavaHomes.getJdk(version)
        Assume.assumeTrue(jvm != null)

        settingsFile << "rootProject.name = 'oldjava'"
        buildFile << """
            plugins {
                id "java"
            }
            repositories {
                jcenter()
            }
            dependencies {
                testImplementation("junit:junit:4.12")
            }
            java {
                sourceCompatibility = JavaVersion.${version.name()}
            }

            def javaInstallation = project.javaInstalls.installationForDirectory(file("${jvm.javaHome.toURI()}"))

            tasks.named("compileJava") {
                options.fork = true
                options.forkOptions.javaHome = javaInstallation.get().installationDirectory.asFile
            }
            tasks.named("compileTestJava") {
                options.fork = true
                options.forkOptions.javaHome = javaInstallation.get().installationDirectory.asFile
            }
            tasks.named("test") {
                executable = javaInstallation.get().javaExecutable.asFile
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
        fails("build")

        then:
        failure.assertHasDescription("Execution failed for task ':compileJava'.")
        failure.assertHasCause("Compilation failed with exit code")

        when:
        sourceFile.replace("() -> { }", "new Runnable() { public void run() { } }")
        run("build")

        then:
        new JarTestFixture(file("build/libs/oldjava.jar")).javaVersion == version
        new ClassFile(file("build/classes/java/main/Thing.class")).javaVersion == version
        new ClassFile(file("build/classes/java/test/ThingTest.class")).javaVersion == version
        new DefaultTestExecutionResult(testDirectory).assertTestClassesExecuted("ThingTest")

        where:
        version << [JavaVersion.VERSION_1_6, JavaVersion.VERSION_1_7]
    }
}

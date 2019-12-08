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
import org.gradle.integtests.fixtures.ToBeFixedForInstantExecution
import org.gradle.test.fixtures.file.TestFile
import org.junit.Assume
import org.objectweb.asm.Opcodes
import spock.lang.Unroll

class CrossCompilationIntegrationTest extends AbstractIntegrationSpec {
    @Unroll
    @ToBeFixedForInstantExecution
    def "can configure the Java plugin to compile and run tests against Java #version JDK"() {
        def jvm = AvailableJavaHomes.getJdk(version)
        Assume.assumeTrue(jvm != null)

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
                options.forkOptions.javaHome = javaInstallation.get().installationDirectory
            }
            tasks.named("compileTestJava") {
                options.fork = true
                options.forkOptions.javaHome = javaInstallation.get().installationDirectory
            }
            tasks.named("test") {
                executable = javaInstallation.get().javaExecutable
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
        run("build", "-i")

        then:
        versionFromByteCode(file("build/classes/java/main/Thing.class")) == version
        versionFromByteCode(file("build/classes/java/test/ThingTest.class")) == version
        def results = new DefaultTestExecutionResult(testDirectory)
        results.assertTestClassesExecuted("ThingTest")

        where:
        version << [JavaVersion.VERSION_1_6, JavaVersion.VERSION_1_7]
    }

    JavaVersion versionFromByteCode(TestFile file) {
        def bytes = file.bytes
        def majorVersion = (((bytes[6] & 0xFF) << 8) | (bytes[7] & 0xFF))
        switch (majorVersion) {
            case Opcodes.V1_6:
                return JavaVersion.VERSION_1_6
            case Opcodes.V1_7:
                return JavaVersion.VERSION_1_7
            default:
                throw new UnsupportedOperationException("Unexpected major version ${majorVersion}")
        }
    }
}

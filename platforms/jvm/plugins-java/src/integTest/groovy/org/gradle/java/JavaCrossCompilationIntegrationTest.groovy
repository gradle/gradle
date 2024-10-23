/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.java

import org.gradle.api.JavaVersion
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.AvailableJavaHomes
import org.gradle.integtests.fixtures.jvm.TestJavaClassUtil
import org.gradle.internal.FileUtils
import org.gradle.internal.jvm.Jvm
import org.gradle.test.fixtures.Flaky
import org.gradle.test.fixtures.file.DoesNotSupportNonAsciiPaths
import org.gradle.util.GradleVersion
import org.junit.Assume

import static org.gradle.internal.serialize.JavaClassUtil.getClassMajorVersion

@DoesNotSupportNonAsciiPaths(reason = "Java 6 seems to have issues with non-ascii paths")
@Flaky(because = "https://github.com/gradle/gradle-private/issues/3901")
class JavaCrossCompilationIntegrationTest extends AbstractIntegrationSpec {

    static List<String> javaVersionsToCrossCompileAgainst() {
        return ["1.6", "1.7", "1.8", "11", "15", "16", "17", "18", "19", "20", "21", "22", "23"]
    }

    static JavaVersion toJavaVersion(String version) {
        return JavaVersion.toVersion(version)
    }

    def withJavaProjectUsingToolchainsForJavaVersion(String version) {
        def javaVersion = toJavaVersion(version)
        def target = AvailableJavaHomes.getJdk(javaVersion)
        Assume.assumeNotNull(target)
        withJavaProjectUsingToolchainsForJavaVersion(target)
    }

    def withJavaProjectUsingToolchainsForJavaVersion(Jvm jvm) {
        buildFile << """
            apply plugin: 'java'
            ${mavenCentralRepository()}
            java {
                toolchain {
                    languageVersion = JavaLanguageVersion.of(${jvm.javaVersion.majorVersion})
                }
            }
            tasks.withType(Javadoc) {
                options.noTimestamp = false
            }
        """

        file("src/main/java/Thing.java") << """
            /** Some thing. */
            public class Thing { }
        """
        executer.withArgument("-Porg.gradle.java.installations.paths=" + jvm.javaHome.absolutePath)
    }

    def "can compile source and run JUnit tests using target Java version"() {
        given:
        withJavaProjectUsingToolchainsForJavaVersion(version)
        buildFile << """
            dependencies { testImplementation 'junit:junit:4.13' }
        """

        file("src/test/java/ThingTest.java") << """
            import org.junit.Test;
            import static org.junit.Assert.*;

            public class ThingTest {
                @Test
                public void verify() {
                    assertTrue(System.getProperty("java.version").startsWith("${version}"));
                }
            }
        """

        expect:
        if (["1.6", "1.7"].contains(version)) {
            executer.expectDeprecationWarning("Running tests on Java versions earlier than 8 has been deprecated. This will fail with an error in Gradle 9.0. Consult the upgrading guide for further information: https://docs.gradle.org/${GradleVersion.current().version}/userguide/upgrading_version_8.html#minimum_test_jvm_version")
        }
        succeeds 'test'
        getClassMajorVersion(javaClassFile("Thing.class")) == TestJavaClassUtil.getClassVersion(toJavaVersion(version))
        getClassMajorVersion(classFile ( "java", "test", "ThingTest.class")) == TestJavaClassUtil.getClassVersion(toJavaVersion(version))

        where:
        version << javaVersionsToCrossCompileAgainst()
    }

    def "can compile source and run TestNG tests using target Java version"() {
        given:
        withJavaProjectUsingToolchainsForJavaVersion(version)
        buildFile << """
            testing.suites.test {
                useTestNG('6.8.8')
            }
        """

        file("src/test/java/ThingTest.java") << """
            import org.testng.annotations.Test;

            public class ThingTest {
                @Test
                public void verify() {
                    assert System.getProperty("java.version").startsWith("${version}");
                }
            }
        """

        expect:
        if (["1.6", "1.7"].contains(version)) {
            executer.expectDeprecationWarning("Running tests on Java versions earlier than 8 has been deprecated. This will fail with an error in Gradle 9.0. Consult the upgrading guide for further information: https://docs.gradle.org/${GradleVersion.current().version}/userguide/upgrading_version_8.html#minimum_test_jvm_version")
        }
        succeeds 'test'

        where:
        version << javaVersionsToCrossCompileAgainst()
    }

    def "can build and run application using target Java version"() {
        given:
        JavaVersion javaVersion = toJavaVersion(version)
        Jvm target = javaVersion.majorVersion == JavaVersion.current().majorVersion ? Jvm.current() : AvailableJavaHomes.getJdk(javaVersion)
        Assume.assumeNotNull(target)
        withJavaProjectUsingToolchainsForJavaVersion(target)
        buildFile << """
            apply plugin: 'application'

            application {
                mainClass = 'Main'
            }
        """

        file("src/main/java/Main.java") << """
            public class Main {
                public static void main(String[] args) {
                    System.out.println("java home: " + System.getProperty("java.home"));
                    System.out.println("java version: " + System.getProperty("java.version"));
                }
            }
        """

        expect:
        succeeds 'run'
        output.contains("java home: ${FileUtils.canonicalize(target.javaHome)}")
        output.contains("java version: ${version}")

        where:
        version << javaVersionsToCrossCompileAgainst()
    }

    def "can generate Javadocs using target Java version"() {
        given:
        withJavaProjectUsingToolchainsForJavaVersion(version)

        expect:
        succeeds 'javadoc'
        file('build/docs/javadoc/Thing.html').text.matches("(?s).*Generated by javadoc \\(.*?\\Q${version}\\E.*")

        where:
        version << javaVersionsToCrossCompileAgainst()
    }
}

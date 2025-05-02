/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.testing.junit

import org.gradle.integtests.fixtures.DefaultTestExecutionResult
import org.gradle.testing.fixture.AbstractTestingMultiVersionIntegrationTest
import spock.lang.Issue

import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

@Issue("https://github.com/gradle/gradle/issues/18486")
abstract class AbstractJUnitClassDetectionIntegrationTest extends AbstractTestingMultiVersionIntegrationTest {

    def setup() {
        buildFile << """
            apply plugin: 'java'
            ${mavenCentralRepository()}
            dependencies {
                ${testFrameworkDependencies}
            }
            test.${configureTestFramework}
        """.stripIndent()
    }

    def jarName = "testFramework.jar"
    def jarNameNew = "anotherTestFramework.jar"
    def jar = new File(testDirectory, "build/libs/$jarName")
    def jarNew = new File(testDirectory, "build/libs/$jarNameNew")

    def 'support detecting classes whose package is not a zip entry'() {
        given:
        buildFile << """
            dependencies {
                ${getTestFrameworkDependencies('main')}
                testImplementation files('build/libs/testFramework.jar')
                ${testFrameworkDependencies}
            }
            jar {
                manifest {
                    attributes 'Manifest-Version': '1.0'
                }
                archiveBaseName = 'testFramework'
            }
        """

        file("src/main/java/org/gradle/BasePlatformTestCase.java") << """
            package org.gradle;
            import junit.framework.TestCase;
            public class BasePlatformTestCase extends TestCase { }
        """

        when:
        succeeds('build')

        then:
        Set<String> entries = ["org/", "org/gradle/", "org/gradle/BasePlatformTestCase.class"]
        assertJarContainsAllEntries(jar, entries)
        file('src/test/java/SomeTest.java') << """
            package com.example;
            import org.gradle.BasePlatformTestCase;
            public class SomeTest extends BasePlatformTestCase {
                public void testPass() { }
            }
        """

        when:
        succeeds("test", "--tests", "SomeTest")
        then:
        new DefaultTestExecutionResult(testDirectory).testClass('com.example.SomeTest').assertTestCount(1, 0, 0)
        new DefaultTestExecutionResult(testDirectory).assertTestClassesExecuted('com.example.SomeTest')

        when:
        createNewJarWithoutPackageEntries(jar, jarNew)
        String entry = "org/gradle/BasePlatformTestCase.class"
        assertJarContainsOnlyOneEntry(jarNew, entry)
        testDirectory.file("build.gradle").replace(jarName, jarNameNew)
        succeeds("test", "--tests", "SomeTest")
        then:
        new DefaultTestExecutionResult(testDirectory).testClass('com.example.SomeTest').assertTestCount(1, 0, 0)
        new DefaultTestExecutionResult(testDirectory).assertTestClassesExecuted('com.example.SomeTest')
    }

    @Issue("https://github.com/gradle/gradle/issues/18465")
    def 'does not try to execute non-test class as test class'() {
        given:
        file('src/test/java/com/example/MyTest.java') << """
            package com.example;

            ${testFrameworkImports}

            public class MyTest {
                @Test public void someTest() {
                    assertTrue(true);
                }
            }
        """.stripIndent()
        file('src/test/java/com/example/CacheSpec.java') << """
            package com.example;

            import static java.lang.annotation.ElementType.METHOD;
            import static java.lang.annotation.RetentionPolicy.RUNTIME;

            import java.lang.annotation.Retention;
            import java.lang.annotation.Target;
            import java.util.concurrent.Executor;
            import java.util.concurrent.ForkJoinPool;

            @SuppressWarnings("ImmutableEnumChecker")
            @Target(METHOD) @Retention(RUNTIME)
            public @interface CacheSpec {

              enum CacheExecutor {
                DEFAULT {
                  @Override public Executor create() {
                    return ForkJoinPool.commonPool();
                  }
                },
                DIRECT {
                  @Override public Executor create() {
                    return Runnable::run;
                  }
                };

                public abstract Executor create();
              }
            }
        """.stripIndent()

        when:
        succeeds('test')

        then:
        new DefaultTestExecutionResult(testDirectory).testClass('com.example.MyTest').assertTestCount(1, 0, 0)
    }

    private static void assertJarContainsAllEntries(final File jar, final Set<String> entries) {
        ZipFile originalJar = new ZipFile(jar)

        originalJar.withCloseable {
            Enumeration<? extends ZipEntry> enumeration = originalJar.entries()
            while (enumeration.hasMoreElements()) {
                ZipEntry entry = enumeration.nextElement()
                String name = entry.getName()
                if (entries.contains(name)) {
                    entries.remove(name)
                }
            }
        }

        assert entries.isEmpty()
    }

    private static void assertJarContainsOnlyOneEntry(final File jar, final String entry) {
        ZipFile originalJar = new ZipFile(jar)

        originalJar.withCloseable {
            Enumeration<? extends ZipEntry> enumeration = originalJar.entries()
            assert enumeration.hasMoreElements()
            String name = enumeration.nextElement().name
            assert name == entry
            assert !enumeration.hasMoreElements()
        }
    }

    private static void createNewJarWithoutPackageEntries(final File jar, final File jarNew) {
        ZipFile originalJar = new ZipFile(jar)
        originalJar.withCloseable {
            Enumeration<? extends ZipEntry> entries = originalJar.entries()
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement()
                if (entry.getName().contains("BasePlatformTestCase")) {
                    ZipEntry newEntry = new ZipEntry(entry.getName())
                    ZipOutputStream outputStream = new ZipOutputStream(new FileOutputStream(jarNew))
                    outputStream.withCloseable { output ->
                        output.putNextEntry(newEntry)
                        InputStream inputStream = originalJar.getInputStream(entry)
                        inputStream.withCloseable { input ->
                            byte[] buffer = new byte[512]
                            while (input.available() > 0) {
                                int read = input.read(buffer)
                                if (read > 0) {
                                    output.write(buffer, 0, read)
                                }
                            }
                        }
                    }
                    return
                }
            }
        }
    }
}

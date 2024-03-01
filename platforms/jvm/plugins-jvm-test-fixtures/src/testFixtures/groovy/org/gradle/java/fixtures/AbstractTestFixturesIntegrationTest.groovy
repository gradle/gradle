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

package org.gradle.java.fixtures

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.test.fixtures.file.TestFile

/**
 * Base class for integration tests of the Java test fixtures plugin that supplies some utility methods
 * for creating classes to test, tests for the them, and fixtures using them.
 */
abstract class AbstractTestFixturesIntegrationTest extends AbstractIntegrationSpec {
    protected TestFile addPersonTestUsingTestFixtures(String subproject = "") {
        file("${subproject ? "${subproject}/" : ""}src/test/java/org/PersonTest.java") << """
            import org.PersonFixture;
            import org.Person;
            import org.junit.Test;
            import static org.junit.Assert.*;

            public class PersonTest {
                @Test
                public void testAny() {
                    Person anyone = PersonFixture.anyone();
                    assertEquals("John", anyone.getFirstName());
                    assertEquals("Doe", anyone.getLastName());
                }
            }
        """
    }

    protected TestFile addPersonDomainClass(String subproject = "", String lang = 'java') {
        file("${subproject ? "${subproject}/" : ""}src/main/$lang/org/Person.$lang") << """
            package org;

            public class Person {
                private final String firstName;
                private final String lastName;

                public Person(String first, String last) {
                    this.firstName = first;
                    this.lastName = last;
                }

                public String getFirstName() {
                    return firstName;
                }

                public String getLastName() {
                    return lastName;
                }
            }
        """
    }

    protected TestFile addPersonTestFixture(String subproject = "", String lang = "java") {
        file("${subproject ? "${subproject}/" : ""}src/testFixtures/$lang/org/PersonFixture.$lang") << """
            package org;

            public class PersonFixture {
                public static Person anyone() {
                    return new Person("John", "Doe");
                }
            }
        """
    }

    protected TestFile addPersonTestFixtureUsingApacheCommons(String subproject = "") {
        file("${subproject ? "${subproject}/" : ""}src/testFixtures/java/org/PersonFixture.java") << """
            package org;
            import org.apache.commons.lang3.StringUtils;

            public class PersonFixture {
                public static Person anyone() {
                    return new Person(StringUtils.capitalize("john"), StringUtils.capitalize("doe"));
                }
            }
        """
    }

    protected void dumpCompileAndRuntimeTestClasspath() {
        buildFile << """
            class Utils {
                static void printClasspathFile(File it) {
                    if (it.absolutePath.contains('intTestHomeDir')) {
                        println it.name
                    } else {
                        println it.absolutePath.substring(it.absolutePath.lastIndexOf('build') + 6).replace(File.separatorChar, (char) '/')
                    }
                }
            }

            compileTestJava {
               doFirst {
                   println "Test compile classpath"
                   println "---"
                   classpath.each { Utils.printClasspathFile(it) }
                   println "---"
               }
            }

            test {
               doFirst {
                  println "Test runtime classpath"
                  println "---"
                  classpath.each { Utils.printClasspathFile(it) }
                  println "---"
               }
            }
"""
    }
}

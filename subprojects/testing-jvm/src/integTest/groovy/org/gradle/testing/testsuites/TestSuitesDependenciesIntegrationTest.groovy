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

package org.gradle.testing.testsuites

import org.gradle.api.internal.tasks.testing.junit.JUnitTestFramework
import org.gradle.integtests.fixtures.AbstractIntegrationSpec



class TestSuitesDependenciesIntegrationTest extends AbstractIntegrationSpec {

  def 'suites do not share dependencies by default'() {
      given:
      buildFile << """
        plugins {
          id 'java'
        }

        ${mavenCentralRepository()}

        testing {
            suites {
                test {
                    dependencies {
                        implementation 'org.apache.commons:commons-lang3:3.11'
                    }
                }
                integTest(JvmTestSuite) {
                    useJUnit()
                }
            }
        }

        tasks.named('check') {
            dependsOn testing.suites.integTest
        }
        """

      file('src/test/java/example/UnitTest.java') << '''
            package example;

            import org.apache.commons.lang3.StringUtils;
            import org.junit.Assert;
            import org.junit.Test;

            public class UnitTest {
                @Test
                public void unitTest() {
                    Assert.assertTrue(StringUtils.isEmpty(""));
                }
            }
        '''

      file('src/integTest/java/it/IntegrationTest.java') << '''
            package it;

            import org.apache.commons.lang3.StringUtils; // compilation fails here; commons-lang3 is not automatically "inherited" by integTests
            import org.junit.Assert;
            import org.junit.Test;

            public class IntegrationTest {
                @Test
                public void integrationTest() {
                    Assert.assertTrue(StringUtils.isEmpty("")); // compilation also fails here; commons-lang3 is not automatically "inherited" by integTests
                }
            }
        '''

      when:
      fails 'check'

      then:
      failureCauseContains('Compilation failed; see the compiler error output for details.')
  }

  def 'default test suite has project dependency by default; others do not'() {
      given:
      buildFile << """
        plugins {
          id 'java'
        }

        ${mavenCentralRepository()}

        dependencies {
            // production code requires commons-lang3 at runtime, which will leak into tests' runtime classpaths
            implementation 'org.apache.commons:commons-lang3:3.11'
        }

        testing {
            suites {
                integTest(JvmTestSuite)
            }
        }

        tasks.named('check') {
            dependsOn testing.suites.integTest
        }

        tasks.register('checkConfiguration') {
                dependsOn test, integTest
                doLast {
                    assert configurations.testRuntimeClasspath.files*.name == ['commons-lang3-3.11.jar'] : 'commons-lang3 leaks from the production project dependencies'
                    assert !configurations.integTestRuntimeClasspath.files*.name.contains('commons-lang3-3.11.jar') : 'integTest does not implicitly depend on the production project'
                }
            }
        """

      expect:
      succeeds 'checkConfiguration'
  }

}

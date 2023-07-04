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

package org.gradle.testing.junit.junit4

import org.gradle.testing.AbstractTestListenerBuildOperationAdapterIntegrationTest

/**
 * Base class for JUnit 4 build operation adapter integration tests.  Provides JUnit4-specific tests and test sources for both JUnit4 and JUnit Vintage.
 */
abstract class AbstractJUnit4TestListenerBuildOperationAdapterIntegrationTest extends AbstractTestListenerBuildOperationAdapterIntegrationTest {
    @Override
    void writeTestSources() {
        file('src/test/java/org/gradle/ASuite.java') << """
            package org.gradle;
            import org.junit.runner.RunWith;
            import org.junit.runners.Suite;
            @RunWith(Suite.class)
            @Suite.SuiteClasses({OkTest.class, OtherTest.class })
            public class ASuite {
                static {
                    System.out.println("suite class loaded");
                }
            }
        """
        file('src/test/java/org/gradle/OkTest.java') << """
            package org.gradle;
            import org.junit.Test;
            public class OkTest {
                @Test
                public void ok() throws Exception {
                    System.err.println("This is test stderr");
                }

                @Test
                public void anotherOk() {
                    System.out.println("sys out from another test method");
                    System.err.println("sys err from another test method");
                }
            }
        """
        file('src/test/java/org/gradle/OtherTest.java') << """
            package org.gradle;
            import org.junit.Test;
            public class OtherTest {
                @Test
                public void ok() throws Exception {
                    System.out.println("This is other stdout");
                }
            }
        """
    }
}

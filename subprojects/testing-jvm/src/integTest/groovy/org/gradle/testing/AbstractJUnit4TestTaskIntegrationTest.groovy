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

package org.gradle.testing

abstract class AbstractJUnit4TestTaskIntegrationTest extends AbstractTestTaskIntegrationTest {
    @Override
    String getStandaloneTestClass() {
        return testClass('MyTest')
    }

    @Override
    String testClass(String className) {
        return """
            import org.junit.*;
            import org.junit.experimental.categories.Category;

            public class $className {
               @Test
               @Category(Fast.class)
               public void fastTest() {
                  System.out.println(System.getProperty("java.version"));
                  Assert.assertEquals(1,1);
               }

               @Test
               @Category(Slow.class)
               public void slowTest() {
                  System.out.println(System.getProperty("java.version"));
                  Assert.assertEquals(1,1);
               }

               interface Fast {}
               interface Slow {}
            }
        """.stripIndent()
    }

    @Override
    String getImportAll() {
        return "org.junit.*"
    }

    @Override
    String getAssertOrAssertions() {
        return "Assert"
    }
}

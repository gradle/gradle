/*
 * Copyright 2025 the original author or authors.
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

import org.gradle.test.fixtures.file.TestFile

/**
 * Helper class for generating large test cases with nested classes and parameterized tests.
 */
class LargeTestCaseGenerator {

    static void generateTests(String prefix, TestFile testDirectory, boolean withFailures = true) {
        generateNestedClassTests(prefix, testDirectory, withFailures)
        generateParameterizedTests(prefix, testDirectory, withFailures)
    }

    private static void generateNestedClassTests(String prefix, TestFile testDirectory, boolean withFailures) {
        // Generate 10 outer classes (A-J)
        def outerClasses = ['A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J']
        def innerClasses = ['A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J']

        outerClasses.each { outerClass ->
            def classContent = new StringBuilder()
            classContent.append("""
                import org.junit.Test;
                import static org.junit.Assert.*;
                import java.util.Random;

                public class ${prefix}_Outer_${outerClass} {
            """)

            innerClasses.each { innerClass ->
                classContent.append("""
                    public static class ${prefix}_Inner_${outerClass}_${innerClass} {
                        private static Random random = new Random(${prefix}_Inner_${outerClass}_${innerClass}.class.getName().hashCode());
                """)

                // Generate 10 tests for each inner class
                (1..10).each { testNum ->
                    classContent.append("""
                        @Test
                        public void test_${prefix}_Inner_${outerClass}_${innerClass}_${testNum}() {
                    """)
                    if (withFailures) {
                        classContent.append("""
                            int value = random.nextInt();
                            if (value % 13 == 0) {
                                fail("Test failed with value: " + value);
                            }
                        """)
                    } else {
                        classContent.append("""
                            // Test passes
                        """)
                    }
                    classContent.append("""
                        }
                    """)
                }

                classContent.append("""
                    }
                """)
            }

            classContent.append("""
                }
            """)

            testDirectory.file("src/test/java/${prefix}_Outer_${outerClass}.java") << classContent.toString()
        }
    }

    private static void generateParameterizedTests(String prefix, TestFile testDirectory, boolean withFailures) {
        // Add 10 parameterized test classes using loops
        (1..10).each { paramTestNum ->
            def paramClassContent = new StringBuilder()
            paramClassContent.append("""
                import org.junit.Test;
                import org.junit.runner.RunWith;
                import org.junit.runners.Parameterized;
                import org.junit.runners.Parameterized.Parameters;
                import static org.junit.Assert.*;
                import java.util.Arrays;
                import java.util.Collection;
                import java.util.Random;

                @RunWith(Parameterized.class)
                public class ${prefix}_ParameterizedTest_${paramTestNum} {
                    private int iteration;
                    private Random random;

                    public ${prefix}_ParameterizedTest_${paramTestNum}(int iteration) {
                        this.iteration = iteration;
                        String className = "${prefix}_ParameterizedTest_${paramTestNum}";
                        random = new Random(className.hashCode() + iteration);
                    }

                    @Parameters
                    public static Collection<Object[]> data() {
                        Object[][] data = new Object[100][1];
                        for (int i = 0; i < 100; i++) {
                            data[i][0] = i;
                        }
                        return Arrays.asList(data);
                    }
            """)

            // Add 10 test methods using a loop
            (1..10).each { testMethodNum ->
                paramClassContent.append("""
                    @Test
                    public void test_${prefix}_ParameterizedTest_${paramTestNum}_${testMethodNum}() {
                    """)
                    if (withFailures) {
                        paramClassContent.append("""
                            int value = random.nextInt();
                            if (value % 13 == 0) {
                                fail("Test failed at iteration " + iteration + " with value: " + value);
                            }
                        """)
                    } else {
                        paramClassContent.append("""
                            // Test passes
                        """)
                    }
                paramClassContent.append("""
                    }
                """)
            }

            paramClassContent.append("""
                }
            """)

            testDirectory.file("src/test/java/${prefix}_ParameterizedTest_${paramTestNum}.java") << paramClassContent.toString()
        }
    }
}

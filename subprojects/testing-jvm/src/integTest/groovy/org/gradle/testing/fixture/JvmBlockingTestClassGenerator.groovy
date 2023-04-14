/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.testing.fixture

import org.gradle.integtests.fixtures.RepoScriptBlockUtil
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.server.http.BlockingHttpServer

class JvmBlockingTestClassGenerator {
    static final String FAILED_RESOURCE = "fail"
    static final String OTHER_RESOURCE = "other"
    static final int DEFAULT_MAX_WORKERS = 2

    private final TestFile root
    private final BlockingHttpServer server
    private final String testAnnotationClass
    private final String testDependencies
    private final String testFrameworkConfiguration

    JvmBlockingTestClassGenerator(TestFile root, BlockingHttpServer server, String testAnnotationClass, String testDependencies, String testFrameworkConfiguration) {
        this.root = root
        this.server = server
        this.testAnnotationClass = testAnnotationClass
        this.testDependencies = testDependencies
        this.testFrameworkConfiguration = testFrameworkConfiguration
    }

    String initBuildFile(int maxWorkers = DEFAULT_MAX_WORKERS, int forkEvery = 0) {
        return """
            apply plugin: 'java'

            ${RepoScriptBlockUtil.mavenCentralRepository()}

            dependencies {
                $testDependencies
            }

            tasks.withType(Test) {
                maxParallelForks = $maxWorkers
                forkEvery = $forkEvery
            }

            $testFrameworkConfiguration
        """
    }

    void withFailingTest() {
        root.file('src/test/java/pkg/FailedTest.java') << """
            package pkg;
            import $testAnnotationClass;
            public class FailedTest {
                @Test
                public void failTest() {
                    ${server.callFromBuild("$FAILED_RESOURCE")}
                    throw new RuntimeException();
                }
            }
        """.stripIndent()
    }

    void withNonfailingTest() {
        root.file('src/test/java/pkg/OtherTest.java') << """
            package pkg;
            import $testAnnotationClass;
            public class OtherTest {
                @Test
                public void passingTest() {
                    ${server.callFromBuild("$OTHER_RESOURCE")}
                }
            }
        """.stripIndent()
    }

    List<String> withNonfailingTests(int num) {
        (1..num).collect {
            final resource = "test_${it}" as String
            root.file("src/test/java/pkg/OtherTest_${it}.java") << """
                package pkg;
                import $testAnnotationClass;
                public class OtherTest_${it} {
                    @Test
                    public void passingTest() {
                        ${server.callFromBuild("$resource")}
                    }
                }
            """.stripIndent()
            resource
        }
    }

    Map<String, String> withFailingTests(int num) {
        (1..num).collectEntries() {
            final testName = "OtherTest_${it}" as String
            final resource = "test_${it}" as String
            root.file("src/test/java/OtherTest_${it}.java") << """
                import $testAnnotationClass;
                public class ${testName} {
                    @Test
                    public void failedTest() {
                        ${server.callFromBuild("$resource")}
                        throw new RuntimeException();
                    }
                }
            """.stripIndent()
            [(testName): resource]
        }
    }
}

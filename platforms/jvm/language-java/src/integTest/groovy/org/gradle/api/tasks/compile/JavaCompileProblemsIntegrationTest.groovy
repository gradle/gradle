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

package org.gradle.api.tasks.compile

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.problems.ReceivedProblem
import org.gradle.test.fixtures.file.TestFile
/**
 * Test class verifying the integration between the {@code JavaCompile} and the {@code Problems} service.
 */
class JavaCompileProblemsIntegrationTest extends AbstractIntegrationSpec {

    // The `assertProblem` method will store visited files here, and `assertAllFilesVisited` will check it
    List<String> assertedFileLocations = []

    def setup() {
        enableProblemsApiCheck()

        propertiesFile << """
            # Feature flag as of 8.6 to enable the Problems API
            systemProp.org.gradle.internal.emit-compiler-problems=true
        """

        buildFile << """
            plugins {
                id 'java'
            }

            tasks {
                compileJava {
                    options.compilerArgs += ["-Xlint:all"]
                }
            }
        """
    }

    def "problem is received when a single-file compilation failure happens"() {
        def files = [
            writeJavaCausingTwoCompilationErrors("Foo")
        ]
        // Duplicate the entries, as we have two problems per file
        files.addAll(files)
        when:
        fails("compileJava")

        then:
        collectedProblems.size() == 2
        for (def problem in collectedProblems) {
            assertProblem(problem, files, "ERROR")
        }
        assertedFileLocations == files
    }

    def "problems are received when a multi-file compilation failure happens"() {
        def files = [
            writeJavaCausingTwoCompilationErrors("Foo"),
            writeJavaCausingTwoCompilationErrors("Bar")
        ]
        // Duplicate the entries, as we have two problems per file
        files.addAll(files)

        when:
        fails("compileJava")

        then:
        collectedProblems.size() == 4
        for (def problem in collectedProblems) {
            assertProblem(problem, files, "ERROR")
        }
    }

    def "problem is received when a single-file warning happens"() {
        def files = [
            writeJavaCausingTwoCompilationWarnings("Foo")
        ]
        // Duplicate the entries, as we have two problems per file
        files.addAll(files)

        when:
        def result = run("compileJava")

        then:
        collectedProblems.size() == 2
        for (def problem in collectedProblems) {
            assertProblem(problem, files, "WARNING")
        }
    }

    def "problems are received when a multi-file warning happens"() {
        def files = [
            writeJavaCausingTwoCompilationWarnings("Foo"),
            writeJavaCausingTwoCompilationWarnings("Bar")
        ]
        // Duplicate the entries, as we have two problems per file
        files.addAll(files)

        when:
        def result = run("compileJava")

        then:
        collectedProblems.size() == 4
        for (def problem in collectedProblems) {
            assertProblem(problem, files, "WARNING")
        }
    }

    def "only failures are received when a multi-file compilation failure and warning happens"() {
        def files = [
            writeJavaCausingTwoCompilationErrorsAndTwoWarnings("Foo"),
            writeJavaCausingTwoCompilationErrorsAndTwoWarnings("Bar")
        ]
        // Duplicate the entries, as we have two problems per file
        files.addAll(files)

        when:
        def result = fails("compileJava")

        then:
        collectedProblems.size() == 4
        for (def problem in collectedProblems) {
            assertProblem(problem, files, "ERROR")
        }
    }

    def "problems are received when two separate compilation task is executed"() {
        def files = [
            writeJavaCausingTwoCompilationErrors("Foo"),
            writeJavaCausingTwoCompilationErrors("FooTest", "test")
        ]
        // Duplicate the entries, as we have two problems per file
        files.addAll(files)

        when:
        // Special flag to fork the compiler, see the setup()
        fails("compileTestJava")

        then:
        collectedProblems.size() == 2
        for (def problem in collectedProblems) {
            assertProblem(problem, files, "ERROR")
        }
    }

    /**
     * Assert if a compilation problems looks like how we expect it to look like.
     * <p>
     * In addition, the method will update the {@code assertedFileLocations} with the location found in the problem.
     * This could be used to assert that all expected files have been visited.
     *
     * @param problem the problem to assert
     * @param possibleFiles the list of possible files that the problem could be in
     * @param severity the expected severity of the problem
     *
     * @throws AssertionError if the problem does not look like how we expect it to look like
     */
    def assertProblem(ReceivedProblem problem, List<String> possibleFiles, String severity, boolean checkTaskLocation = true) {
        assert problem["severity"] == severity: "Expected severity to be ${severity}, but was ${problem["severity"]}"

        def locations = problem["locations"] as List<Map<String, Object>>
        if (checkTaskLocation) {
            assert locations.size() == 2: "Expected two locations, but received ${locations.size()}"
        } else {
            assert locations.size() == 1: "Expected a single location, but received ${locations.size()}"
        }

        if (checkTaskLocation) {
            def taskLocation = locations.find {
                it.containsKey("buildTreePath")
            }
            assert taskLocation != null: "Expected a task location, but it was null"
            assert taskLocation["buildTreePath"] == ":compileJava": "Expected task location to be ':compileJava', but it was ${taskLocation["buildTreePath"]}"
        }

        def fileLocation = locations.find {
            it.containsKey("path") && it.containsKey("line") && it.containsKey("column") && it.containsKey("length")
        }
        assert fileLocation != null: "Expected a file location, but it was null"
        assert fileLocation["line"] != null: "Expected a line number, but it was null"
        assert fileLocation["column"] != null: "Expected a column number, but it was null"
        assert fileLocation["length"] != null: "Expected a length, but it was null"

        def fileLocationPath = fileLocation["path"] as String
        assert possibleFiles.remove(fileLocationPath): "Not found file location '${fileLocationPath}' in the expected file locations: ${possibleFiles}"

        return true
    }

    String writeJavaCausingTwoCompilationErrors(String className, String sourceSet = "main") {
        def file = file("src/${sourceSet}/java/${className}.java")
        file << """
            public class ${className} {
                public static void problemOne(String[] args) {
                    // Missing semicolon will trigger an error
                    String s = "Hello, World!"
                }

                public static void problemTwo(String[] args) {
                    // Missing semicolon will trigger an error
                    String s = "Hello, World!"
                }
            }
        """

        return formatFilePath(file)
    }

    String writeJavaCausingTwoCompilationWarnings(String className) {
        def file = file("src/main/java/${className}.java")
        file << """
            public class ${className} {
                public static void warningOne(String[] args) {
                    // Unnecessary cast will trigger a warning
                    String s = (String)"Hello World";
                }

                public static void warningTwo(String[] args) {
                    // Unnecessary cast will trigger a warning
                    String s = (String)"Hello World";
                }
            }
        """

        return formatFilePath(file)
    }

    String writeJavaCausingTwoCompilationErrorsAndTwoWarnings(String className) {
        def file = file("src/main/java/${className}.java")
        file << """
            public class ${className} {
                public static void problemOne(String[] args) {
                    // Missing semicolon will trigger an error
                    String s = "Hello, World!"
                }

                public static void problemTwo(String[] args) {
                    // Missing semicolon will trigger an error
                    String s = "Hello, World!"
                }

                public static void warningOne(String[] args) {
                    // Unnecessary cast will trigger a warning
                    String s = (String)"Hello World";
                }

                public static void warningTwo(String[] args) {
                    // Unnecessary cast will trigger a warning
                    String s = (String)"Hello World";
                }
            }
        """

        return formatFilePath(file)
    }

    def formatFilePath(TestFile file) {
        return file.absolutePath.toString()
    }

}

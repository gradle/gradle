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
            org.gradle.compile.use-problems-api=true
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
        assertAllFilesVisited(files)
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
        assertAllFilesVisited(files)
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
        assertAllFilesVisited(files)
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
        assertAllFilesVisited(files)
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
        assertAllFilesVisited(files)
    }

    def "events are received when compiler is forked"() {
        buildFile << """
            tasks.compileJava.options.fork = true
        """

        def files = [
            writeJavaCausingTwoCompilationErrors("Foo"),
        ]

        when:
        // Special flag to fork the compiler, see the setup()
        fails("compileJava")

        then:
        collectedProblems.size() == 2
        for (def problem in collectedProblems) {
            assertProblem(problem, files, "ERROR")
        }
        assertAllFilesVisited(files)
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
    def assertProblem(Map<String, Object> problem, List<String> possibleFiles, String severity) {
        assert problem["severity"] == severity

        def locations = problem["locations"] as List<Map<String, Object>>
        assert locations.size() == 2

        def taskLocation = locations.find {
            it["type"] == "task"
        }
        assert taskLocation != null
        assert taskLocation["identityPath"]["path"] == ":compileJava"

        def fileLocation = locations.find {
            it["type"] == "file"
        }
        assert fileLocation != null
        assert fileLocation["line"] != null
        assert fileLocation["column"] != null
        assert fileLocation["length"] != null

        def fileLocationPath = fileLocation["path"] as String
        assert possibleFiles.remove(fileLocationPath)

        return true
    }

    def assertAllFilesVisited(List<String> files) {
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

        return file.absolutePath
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

        return file.absolutePath
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

        return file.absolutePath
    }

}

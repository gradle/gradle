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

import org.gradle.api.JavaVersion
import org.gradle.api.internal.tasks.compile.DiagnosticToProblemListener
import org.gradle.api.problems.FileLocation
import org.gradle.api.problems.LineInFileLocation
import org.gradle.api.problems.OffsetInFileLocation
import org.gradle.api.problems.Severity
import org.gradle.api.tasks.compile.fixtures.ProblematicClassGenerator
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.AvailableJavaHomes
import org.gradle.integtests.fixtures.jvm.JavaToolchainFixture
import org.gradle.integtests.fixtures.problems.ReceivedProblem
import org.gradle.internal.jvm.Jvm
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.IntegTestPreconditions
import spock.lang.Issue

/**
 * Test class verifying the integration between the {@code JavaCompile} and the {@code Problems} service.
 */
class JavaCompileProblemsIntegrationTest extends AbstractIntegrationSpec implements JavaToolchainFixture {

    /**
     * A map of all possible file locations, and the number of occurrences we expect to find in the problems.
     */
    private final Map<String, Integer> possibleFileLocations = [:]

    /**
     * A map of all visited file locations, and the number of occurrences we have found in the problems.
     * <p>
     * This field will be updated by {@link #assertProblem(ReceivedProblem, String, Boolean)} as it asserts a problem.
     */
    private final Map<String, Integer> visitedFileLocations = [:]

    def setup() {
        enableProblemsApiCheck()

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

    def cleanup() {
        if (isProblemsApiCheckEnabled()) {
            assert possibleFileLocations == visitedFileLocations: "Not all expected files were visited, unchecked files were: ${possibleFileLocations.keySet() - visitedFileLocations.keySet()}"
        }
    }

    /**
     * Verifies common properties of an error problem.
     *
     * @param problem The problem to verify
     * @param expectLineLocation Whether to expect a line location (defaults to true)
     * @param checkSolutions Whether to check for non-empty solutions (defaults to true)
     */
    void verifyErrorProblem(ReceivedProblem problem, boolean expectLineLocation = true, boolean checkSolutions = true) {
        assertLocations(problem, expectLineLocation)
        assert problem.severity == Severity.ERROR
        assert problem.fqid == 'compilation:java:compiler.err.expected'
        assert problem.definition.id.displayName == "';' expected"
        assert problem.contextualLabel == '\';\' expected'
        if (checkSolutions) {
            assert !problem.solutions.empty
        }
    }

    /**
     * Verifies common properties of a warning problem.
     *
     * @param problem The problem to verify
     * @param expectLineLocation Whether to expect a line location (defaults to true)
     * @param fileLocation Optional file location for additional verification
     */
    void verifyWarningProblem(ReceivedProblem problem, boolean expectLineLocation = true, String fileLocation = null) {
        assertLocations(problem, expectLineLocation)
        assert problem.severity == Severity.WARNING
        assert problem.fqid == 'compilation:java:compiler.warn.redundant.cast'
        assert problem.definition.id.displayName == 'redundant cast to java.lang.String'
        assertRedundantCastInContextualLabel(problem.contextualLabel)

        // Optional verification for details if file location is provided
        if (fileLocation) {
            assertRedundantCastInDetails(problem.details, fileLocation)
            assert problem.solutions.empty
        }
    }

    /**
     * Verifies common properties of a -Werror specific error problem.
     *
     * @param problem The problem to verify
     */
    void verifyWerrorProblem(ReceivedProblem problem) {
        assertLocations(problem, false, false)
        assert problem.severity == Severity.ERROR
        assert problem.fqid == 'compilation:java:compiler.err.warnings.and.werror'
        assert problem.definition.id.displayName == 'warnings found and -Werror specified'
        assert problem.contextualLabel == 'warnings found and -Werror specified'
        assert !problem.solutions.empty
        assert problem.details == "error: warnings found and -Werror specified"
    }

    /**
     * Verifies a JDK-specific warning problem with the appropriate message format.
     *
     * @param problem The problem to verify
     * @param isJava9Compatible Whether the JDK is Java 9 compatible
     */
    void verifyJdkSpecificWarningProblem(ReceivedProblem problem, boolean isJava9Compatible) {
        assertLocations(problem, true)
        assert problem.severity == Severity.WARNING
        assert problem.fqid == 'compilation:java:compiler.warn.redundant.cast'
        def message = getRedundantMessage(isJava9Compatible)
        assert problem.definition.id.displayName == 'redundant cast to java.lang.String'
        assert problem.contextualLabel == message
        assert problem.details.contains(message)
    }

    def "problem is received when a single-file compilation failure happens"() {
        given:
        possibleFileLocations.put(writeJavaCausingTwoCompilationErrors("Foo").absolutePath, 2)

        when:
        fails("compileJava")

        then:
        verifyErrorProblem(receivedProblem(0))
        verifyErrorProblem(receivedProblem(1))
        result.error.contains("2 errors\n")
    }

    def "problems are received when a multi-file compilation failure happens"() {
        given:
        possibleFileLocations.put(writeJavaCausingTwoCompilationErrors("Foo").absolutePath, 2)
        possibleFileLocations.put(writeJavaCausingTwoCompilationErrors("Bar").absolutePath, 2)

        when:
        fails("compileJava")

        then:
        (0..3).each { verifyErrorProblem(receivedProblem(it)) }
        result.error.contains("4 errors\n")
    }

    def "problem is received when a single-file warning happens"() {
        given:
        possibleFileLocations.put(writeJavaCausingTwoCompilationWarnings("Foo").absolutePath, 2)

        when:
        def result = run("compileJava")

        then:
        (0..1).each { verifyWarningProblem(receivedProblem(it)) }
        result.error.contains("2 warnings\n")
    }

    void assertRedundantCastInContextualLabel(String label) {
        assert label == getRedundantMessage()
    }

    def "problem is received when a single-file note happens"() {
        given:
        possibleFileLocations.put(writeJavaCausingOneNoteCompilationWarnings("Foo").absolutePath, 2)
        buildFile.text = buildFile.text.replaceAll(/"-Xlint:all"/, "")

        when:
        run("compileJava")

        then:
        verifyAll(receivedProblem(0)) {
            assertLocations(it, false, false)
            severity == Severity.ADVICE
            fqid == 'compilation:java:compiler.note.unchecked.filename'
            contextualLabel == "${buildFile.parentFile.path}/src/main/java/Foo.java uses unchecked or unsafe operations.".replace('/', File.separator)
        }
        verifyAll(receivedProblem(1)) {
            assertLocations(it, false, false)
            severity == Severity.ADVICE
            fqid == 'compilation:java:compiler.note.unchecked.recompile'
            contextualLabel == "Recompile with -Xlint:unchecked for details."
        }
    }

    def "problems are received when a multi-file warning happens"() {
        given:
        possibleFileLocations.put(writeJavaCausingTwoCompilationWarnings("Foo").absolutePath, 2)
        possibleFileLocations.put(writeJavaCausingTwoCompilationWarnings("Bar").absolutePath, 2)

        when:
        def result = run("compileJava")

        then:
        (0..3).each { verifyWarningProblem(receivedProblem(it)) }
        result.error.contains("4 warnings\n")
    }

    def "only failures are received when a multi-file compilation failure and warning happens"() {
        given:
        possibleFileLocations.put(writeJavaCausingTwoCompilationErrorsAndTwoWarnings("Foo").absolutePath, 2)
        possibleFileLocations.put(writeJavaCausingTwoCompilationErrorsAndTwoWarnings("Bar").absolutePath, 2)

        when:
        def result = fails("compileJava")

        then:
        (0..3).each { verifyErrorProblem(receivedProblem(it)) }
        result.error.contains("4 errors\n")
    }

    def "problems are received when two separate compilation task is executed"() {
        given:
        // The main source set will only cause warnings, as otherwise compilation will fail with the `compileJava` task
        possibleFileLocations.put(writeJavaCausingTwoCompilationWarnings("Foo").absolutePath, 2)
        // The test source set will cause errors
        possibleFileLocations.put(writeJavaCausingTwoCompilationErrors("FooTest", "test").absolutePath, 2)

        when:
        // Special flag to fork the compiler, see the setup()
        fails("compileTestJava")

        then:
        // Errors from test source set
        verifyErrorProblem(receivedProblem(0))
        verifyErrorProblem(receivedProblem(1))

        // Warnings from main source set
        verifyWarningProblem(receivedProblem(2))
        verifyWarningProblem(receivedProblem(3))

        result.error.contains("2 errors\n")
        result.error.contains("2 warnings\n")
    }

    def "the compiler flag -Werror correctly reports problems"() {
        given:
        buildFile << "tasks.compileJava.options.compilerArgs += ['-Werror']"

        def fooFileLocation = writeJavaCausingTwoCompilationWarnings("Foo")
        possibleFileLocations.put(fooFileLocation.absolutePath, 3)

        when:
        fails("compileJava")

        then:
        // Special -Werror error
        verifyWerrorProblem(receivedProblem(0))

        // The two expected warnings
        verifyWarningProblem(receivedProblem(1), true, "$fooFileLocation:11")
        verifyWarningProblem(receivedProblem(2), true, "$fooFileLocation:7")

        result.error.contains("1 error\n")
        result.error.contains("2 warnings\n")
    }

    def "warning counts are not reported when there are no warnings"() {
        disableProblemsApiCheck()

        given:
        def generator = new ProblematicClassGenerator(testDirectory, "Foo")
        generator.save()

        when:
        succeeds("compileJava")

        then:
        !result.error.contains("0 warnings")
    }

    def "warning counts are reported correctly"(int warningCount, String warningMessage) {
        disableProblemsApiCheck()

        given:
        def generator = new ProblematicClassGenerator(testDirectory)
        for (int i = 1; i <= warningCount; i++) {
            generator.addWarning()
        }
        generator.save()

        when:
        succeeds("compileJava")

        then:
        result.error.contains(warningMessage)

        where:
        warningCount | warningMessage
        1            | "1 warning"
        2            | "2 warnings"
    }

    def "error counts are not reported when there are no errors"() {
        disableProblemsApiCheck()

        given:
        def generator = new ProblematicClassGenerator(testDirectory)
        generator.save()

        when:
        succeeds("compileJava")

        then:
        !result.error.contains("0 errors")
    }

    def "error counts are reported correctly"(int errorCount, String errorMessage) {
        disableProblemsApiCheck()

        given:
        def generator = new ProblematicClassGenerator(testDirectory)
        for (int i = 1; i <= errorCount; i++) {
            generator.addError()
        }
        generator.save()

        when:
        fails("compileJava")

        then:
        result.error.contains(errorMessage)

        where:
        errorCount | errorMessage
        1          | "1 error"
        2          | "2 errors"
    }

    @Issue("https://github.com/gradle/gradle/pull/29141")
    @Requires(IntegTestPreconditions.Java8HomeAvailable)
    def "compiler warnings causes failure in problem mapping under JDK8"() {
        given:
        setupAnnotationProcessors(JavaVersion.VERSION_1_8)

        def generator = new ProblematicClassGenerator(testDirectory, "Foo")
        generator.addWarning()
        generator.save()
        possibleFileLocations.put(generator.sourceFile.absolutePath, 1)

        when:
        executer.withArguments("--info")
        withInstallations(AvailableJavaHomes.getJdk(JavaVersion.VERSION_1_8))
        succeeds(":compileJava")

        then:
        outputContains(DiagnosticToProblemListener.FORMATTER_FALLBACK_MESSAGE)
        verifyJdkSpecificWarningProblem(receivedProblem(0), false) // JDK8 is not Java 9 compatible
    }

    @Issue("https://github.com/gradle/gradle/pull/29141")
    def "compiler warnings does not cause failure in problem mapping under JDK#jdk.javaVersionMajor"(Jvm jdk) {
        given:
        setupAnnotationProcessors(jdk.javaVersion)

        def generator = new ProblematicClassGenerator(testDirectory, "Foo")
        generator.addWarning()
        generator.save()
        possibleFileLocations.put(generator.sourceFile.absolutePath, 1)

        when:
        executer.withArguments("--info")
        withInstallations(jdk)
        succeeds(":compileJava")

        then:
        !result.error.contains(DiagnosticToProblemListener.FORMATTER_FALLBACK_MESSAGE)
        verifyJdkSpecificWarningProblem(receivedProblem(0), true) // These JDKs are Java 9 compatible

        where:
        jdk << AvailableJavaHomes.getAvailableJdks {
            it.languageVersion.isJava9Compatible()
        }
    }

    def "invalid flags should be reported as problems"() {
        given:
        writeJavaCausingTwoCompilationWarnings("Foo")
        buildFile << "tasks.compileJava.options.compilerArgs += ['-invalid-flag']"

        when:
        fails("compileJava")

        then:
        verifyAll(receivedProblem) {
            severity == Severity.ERROR
            fqid == 'compilation:java:initialization-failed'
            // Message can change between JDK versions:
            //  - JDK1.8: error: invalid flag: -invalid-flag
            //  - JDK11:         invalid flag: -invalid-flag
            contextualLabel.endsWith('invalid flag: -invalid-flag')
            exception.message.endsWith('invalid flag: -invalid-flag')
        }
    }

    /**
     * Assert if a compilation problems looks like how we expect it to look like.
     * <p>
     * In addition, the method will update the {@link #possibleFileLocations} with the location found in the problem.
     * This could be used to assert that all expected files have been visited.
     *
     * @param problem the problem to assert
     * @param severity the expected severity of the problem
     * @param extraChecks an optional closure to perform any custom checks on the problem, like checking the precise message of the problem
     *
     * @throws AssertionError if the problem does not look like how we expect it to look like
     */
    void assertLocations(
        ReceivedProblem problem,
        boolean expectLineLocation = true,
        boolean expectOffsetLocation = !expectLineLocation
    ) {
        assert problem.contextualLabel != null, "Expected contextual label to be non-null, but was null"

        def locations = problem.originLocations + problem.contextualLocations
        // We use this counter to assert that we have visited all locations
        def assertedLocationCount = 1

        if (expectLineLocation) {
            LineInFileLocation positionLocation = locations.find {
                it instanceof LineInFileLocation
            }
            assert positionLocation != null: "Expected a precise file location, but it was null"
            // Register that we've asserted this location
            assertedLocationCount += 1
            assertOccurence(positionLocation)
        } else if (expectOffsetLocation) {
            OffsetInFileLocation offsetLocation = locations.find {
                it instanceof OffsetInFileLocation
            }
            assert offsetLocation != null: "Expected a precise file location, but it was null"
            // Register that we've asserted this location
            assertedLocationCount += 1
            assertOccurence(offsetLocation)
        } else {
            FileLocation fileLocation = locations.find { it instanceof FileLocation }
            assert fileLocation != null: "Expected a file location, but it was null"
            def fileLocationPath = fileLocation.path
            // Register that we've asserted this location
            assertedLocationCount += 1
            // Check if we expect this file location
            assertOccurence(fileLocation)
        }

        assert assertedLocationCount == locations.size(): "Expected to assert all locations, but only visited ${assertedLocationCount} out of ${locations.size()}"
    }

    void assertOccurence(FileLocation fileLocationPath) {
        def path = fileLocationPath.path
        def occurrences = possibleFileLocations.get(path)
        assert occurrences, "Not found file location '${path}' in the expected file locations: ${possibleFileLocations.keySet()}"
        visitedFileLocations.putIfAbsent(path, 0)
        visitedFileLocations[path] += 1
    }

    TestFile writeJavaCausingTwoCompilationErrors(String className, String sourceSet = "main") {
        def generator = new ProblematicClassGenerator(testDirectory, className, sourceSet)
        generator.addError()
        generator.addError()
        return generator.save()
    }

    TestFile writeJavaCausingTwoCompilationWarnings(String className) {
        def generator = new ProblematicClassGenerator(testDirectory, className)
        generator.addWarning()
        generator.addWarning()
        return generator.save()
    }

    TestFile writeJavaCausingOneNoteCompilationWarnings(String className) {
        def generator = new ProblematicClassGenerator(testDirectory, className)
        generator.addNotable()
        return generator.save()
    }

    TestFile writeJavaCausingTwoCompilationErrorsAndTwoWarnings(String className) {
        def generator = new ProblematicClassGenerator(testDirectory, className)
        generator.addError()
        generator.addError()
        generator.addWarning()
        generator.addWarning()
        return generator.save()
    }

    def setupAnnotationProcessors(JavaVersion testedJdkVersion) {
        //
        // 1. step: Create a simple annotation processor
        //
        file("processor/build.gradle") << """
            plugins {
                id 'java'
            }

            java {
                toolchain {
                    languageVersion = JavaLanguageVersion.of(${Integer.parseInt(testedJdkVersion.getMajorVersion())})
                }
            }
        """
        file("processor/src/main/java/DummyAnnotation.java") << """
            package com.example;

            import java.lang.annotation.ElementType;
            import java.lang.annotation.Retention;
            import java.lang.annotation.RetentionPolicy;
            import java.lang.annotation.Target;

            @Retention(RetentionPolicy.RUNTIME)
            @Target(ElementType.TYPE)
            public @interface DummyAnnotation {
            }
        """
        // A simple annotation processor
        file("processor/src/main/java/DummyProcessor.java") << """
            package com.example;

            import javax.annotation.processing.AbstractProcessor;
            import javax.annotation.processing.RoundEnvironment;
            import javax.annotation.processing.Processor;
            import javax.annotation.processing.ProcessingEnvironment;
            import javax.annotation.processing.SupportedAnnotationTypes;
            import javax.annotation.processing.SupportedSourceVersion;
            import javax.lang.model.SourceVersion;
            import javax.lang.model.element.TypeElement;
            import javax.lang.model.element.Element;
            import javax.tools.Diagnostic;

            import java.util.Set;

            @SupportedAnnotationTypes("com.example.DummyAnnotation")
            @SupportedSourceVersion(SourceVersion.RELEASE_${Integer.parseInt(testedJdkVersion.majorVersion)})
            public class DummyProcessor extends AbstractProcessor {
                @Override
                public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
                    return true; // No further processing of this annotation type
                }
            }
        """
        // META-INF/services file for registering the annotation processor
        file("processor/src/main/resources/META-INF/services/javax.annotation.processing.Processor") << "com.example.DummyProcessor"

        //
        // 2. step: Create a simple project that uses the annotation processor
        //
        settingsFile << """\
            include 'processor'
        """
        buildFile << """\
            java {
                toolchain {
                    languageVersion = JavaLanguageVersion.of(${Integer.parseInt(testedJdkVersion.majorVersion)})
                }
            }

            dependencies {
                annotationProcessor project(':processor')
            }
        """
    }

    void assertRedundantCastInDetails(String details, String fooFileLocation) {
        // Determine which message format to use based on Java version
        // Define the expected details format
        assert details == """\
${fooFileLocation}: warning: [cast] ${getRedundantMessage()}
        String s = (String)"Hello World";
                   ^"""
    }

    String getRedundantMessage(boolean isJava9Compatible = JavaVersion.current().java9Compatible) {
        "redundant cast to ${isJava9Compatible ? "" : "java.lang."}String"
    }
}

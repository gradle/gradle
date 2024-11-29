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
import org.gradle.api.problems.Severity
import org.gradle.api.problems.internal.FileLocation
import org.gradle.api.problems.internal.LineInFileLocation
import org.gradle.api.problems.internal.OffsetInFileLocation
import org.gradle.api.tasks.compile.fixtures.ProblematicClassGenerator
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.AvailableJavaHomes
import org.gradle.integtests.fixtures.jvm.JavaToolchainFixture
import org.gradle.integtests.fixtures.problems.ReceivedProblem
import org.gradle.internal.jvm.Jvm
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.IntegTestPreconditions
import org.opentest4j.AssertionFailedError
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

    def "problem is received when a single-file compilation failure happens"() {
        given:
        possibleFileLocations.put(writeJavaCausingTwoCompilationErrors("Foo").absolutePath, 2)

        when:
        fails("compileJava")

        then:
        verifyAll(receivedProblem(0)) {
            assertLabel(it)
            assertLocations(it, true)
            severity == Severity.ERROR
            contextualLabel == '\';\' expected'
            fqid == 'compilation:java:compiler-err-expected'
            contextualLabel == '\';\' expected'
        }
        verifyAll(receivedProblem(1)) {
            assertLocations(it, true)
            severity == Severity.ERROR
            contextualLabel == '\';\' expected'
            fqid == 'compilation:java:compiler-err-expected'
            contextualLabel == '\';\' expected'
        }

        result.error.contains("2 errors\n")
    }

    def "problems are received when a multi-file compilation failure happens"() {
        given:
        possibleFileLocations.put(writeJavaCausingTwoCompilationErrors("Foo").absolutePath, 2)
        possibleFileLocations.put(writeJavaCausingTwoCompilationErrors("Bar").absolutePath, 2)

        when:
        fails("compileJava")

        then:
        verifyAll(receivedProblem(0)) {
            assertLocations(it, true)
            severity == Severity.ERROR
            fqid == 'compilation:java:compiler-err-expected'
            contextualLabel == '\';\' expected'
            !solutions.empty
        }
        verifyAll(receivedProblem(1)) {
            assertLocations(it, true)
            severity == Severity.ERROR
            fqid == 'compilation:java:compiler-err-expected'
            contextualLabel == '\';\' expected'
            !solutions.empty
        }
        verifyAll(receivedProblem(2)) {
            assertLocations(it, true)
            severity == Severity.ERROR
            fqid == 'compilation:java:compiler-err-expected'
            contextualLabel == '\';\' expected'
            !solutions.empty
        }
        verifyAll(receivedProblem(3)) {
            assertLocations(it, true)
            severity == Severity.ERROR
            fqid == 'compilation:java:compiler-err-expected'
            contextualLabel == '\';\' expected'
            !solutions.empty
        }

        result.error.contains("4 errors\n")
    }

    def "problem is received when a single-file warning happens"() {
        given:
        possibleFileLocations.put(writeJavaCausingTwoCompilationWarnings("Foo").absolutePath, 2)

        when:
        def result = run("compileJava")

        then:
        verifyAll(receivedProblem(0)) {
            assertLocations(it, true)
            severity == Severity.WARNING
            fqid == 'compilation:java:compiler-warn-redundant-cast'
            contextualLabel == 'redundant cast to java.lang.String'
        }
        verifyAll(receivedProblem(1)) {
            assertLocations(it, true)
            severity == Severity.WARNING
            fqid == 'compilation:java:compiler-warn-redundant-cast'
            contextualLabel == 'redundant cast to java.lang.String'
        }

        result.error.contains("2 warnings\n")
    }

    def "problems are received when a multi-file warning happens"() {
        given:
        possibleFileLocations.put(writeJavaCausingTwoCompilationWarnings("Foo").absolutePath, 2)
        possibleFileLocations.put(writeJavaCausingTwoCompilationWarnings("Bar").absolutePath, 2)

        when:
        def result = run("compileJava")

        then:
        verifyAll(receivedProblem(0)) {
            assertLocations(it, true)
            severity == Severity.WARNING
            fqid == 'compilation:java:compiler-warn-redundant-cast'
            contextualLabel == 'redundant cast to java.lang.String'
        }
        verifyAll(receivedProblem(1)) {
            assertLocations(it, true)
            severity == Severity.WARNING
            fqid == 'compilation:java:compiler-warn-redundant-cast'
            contextualLabel == 'redundant cast to java.lang.String'
        }
        verifyAll(receivedProblem(2)) {
            assertLocations(it, true)
            severity == Severity.WARNING
            fqid == 'compilation:java:compiler-warn-redundant-cast'
            contextualLabel == 'redundant cast to java.lang.String'
        }
        verifyAll(receivedProblem(3)) {
            assertLocations(it, true)
            severity == Severity.WARNING
            fqid == 'compilation:java:compiler-warn-redundant-cast'
            contextualLabel == 'redundant cast to java.lang.String'
        }

        result.error.contains("4 warnings\n")
    }

    def "only failures are received when a multi-file compilation failure and warning happens"() {
        given:
        possibleFileLocations.put(writeJavaCausingTwoCompilationErrorsAndTwoWarnings("Foo").absolutePath, 2)
        possibleFileLocations.put(writeJavaCausingTwoCompilationErrorsAndTwoWarnings("Bar").absolutePath, 2)

        when:
        def result = fails("compileJava")

        then:
        verifyAll(receivedProblem(0)) {
            assertLocations(it, true)
            severity == Severity.ERROR
            fqid == 'compilation:java:compiler-err-expected'
            contextualLabel == '\';\' expected'
        }
        verifyAll(receivedProblem(1)) {
            assertLocations(it, true)
            severity == Severity.ERROR
            fqid == 'compilation:java:compiler-err-expected'
            contextualLabel == '\';\' expected'
        }
        verifyAll(receivedProblem(2)) {
            assertLocations(it, true)
            severity == Severity.ERROR
            fqid == 'compilation:java:compiler-err-expected'
            contextualLabel == '\';\' expected'
        }
        verifyAll(receivedProblem(3)) {
            assertLocations(it, true)
            severity == Severity.ERROR
            fqid == 'compilation:java:compiler-err-expected'
            contextualLabel == '\';\' expected'
        }

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
        verifyAll(receivedProblem(0)) {
            assertLocations(it, true)
            severity == Severity.ERROR
            fqid == 'compilation:java:compiler-err-expected'
            contextualLabel == '\';\' expected'
        }
        verifyAll(receivedProblem(1)) {
            assertLocations(it, true)
            severity == Severity.ERROR
            fqid == 'compilation:java:compiler-err-expected'
            contextualLabel == '\';\' expected'
        }
        verifyAll(receivedProblem(2)) {
            assertLocations(it, true)
            severity == Severity.WARNING
            fqid == 'compilation:java:compiler-warn-redundant-cast'
            contextualLabel == 'redundant cast to java.lang.String'
        }
        verifyAll(receivedProblem(3)) {
            assertLocations(it, true)
            severity == Severity.WARNING
            fqid == 'compilation:java:compiler-warn-redundant-cast'
            contextualLabel == 'redundant cast to java.lang.String'
        }

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
        // 2 warnings + 1 special error
        // The compiler will report a single error, implying that the warnings were treated as errors
        verifyAll(receivedProblem(0)) {
            assertLocations(it, false, false)
            severity == Severity.ERROR
            fqid == 'compilation:java:compiler-err-warnings-and-werror'
            contextualLabel == 'warnings found and -Werror specified'
            !solutions.empty
            details == "error: warnings found and -Werror specified"
        }

        // Based on the Java version, the types in the lint message will differ...
        String expectedType
        if (JavaVersion.current().isJava9Compatible()) {
            expectedType = "String"
        } else {
            expectedType = "java.lang.String"
        }

        // The two expected warnings are still reported as warnings
        verifyAll(receivedProblem(1)) {
            assertLocations(it, true)
            severity == Severity.WARNING
            fqid == 'compilation:java:compiler-warn-redundant-cast'
            contextualLabel == 'redundant cast to java.lang.String'
            solutions.empty
            verifyAll(getSingleLocation(ReceivedProblem.ReceivedFileLocation)) {
                it.path == fooFileLocation.absolutePath
            }
            details == """\
$fooFileLocation:5: warning: [cast] redundant cast to $expectedType
        String s = (String)"Hello World";
                   ^"""
        }
        verifyAll(receivedProblem(2)) {
            assertLocations(it, true)
            severity == Severity.WARNING
            fqid == 'compilation:java:compiler-warn-redundant-cast'
            contextualLabel == 'redundant cast to java.lang.String'
            solutions.empty
            details == """\
${fooFileLocation}:9: warning: [cast] redundant cast to $expectedType
        String s = (String)"Hello World";
                   ^"""
        }

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
        verifyAll(receivedProblem(0)) {
            assertLocations(it, true)
            severity == Severity.WARNING
            fqid == 'compilation:java:compiler-warn-redundant-cast'
            contextualLabel == 'redundant cast to java.lang.String'
            // In JDK8, the compiler will not simplify the type to just "String"
            details.contains("redundant cast to java.lang.String")
        }
    }

    @Issue("https://github.com/gradle/gradle/pull/29141")
    @Requires(IntegTestPreconditions.Java11HomeAvailable)
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
        verifyAll(receivedProblem(0)) {
            assertLocations(it, true)
            severity == Severity.WARNING
            fqid == 'compilation:java:compiler-warn-redundant-cast'
            contextualLabel == 'redundant cast to java.lang.String'
            // In JDK11, the compiler will not simplify the type to just "String"
            details.contains("redundant cast to String")
        }

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

    void assertLabel(ReceivedProblem receivedProblem) {
        switch (receivedProblem.severity) {
            case Severity.ERROR:
                assert receivedProblem.definition.id.displayName == "Java compilation error"
                break
            case Severity.WARNING:
                assert receivedProblem.definition.id.displayName == "Java compilation warning"
                break
            default:
                throw new AssertionFailedError("Unexpected severity: ${receivedProblem.severity}")
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

        def locations = problem.originLocations
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

}

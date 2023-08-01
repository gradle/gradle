/*
 * Copyright 2012 the original author or authors.
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
package org.gradle.groovy.compile

import com.google.common.collect.Ordering
import org.gradle.api.Action
import org.gradle.integtests.fixtures.FeaturePreviewsFixture
import org.gradle.integtests.fixtures.MultiVersionIntegrationSpec
import org.gradle.integtests.fixtures.TargetCoverage
import org.gradle.integtests.fixtures.TestResources
import org.gradle.integtests.fixtures.executer.ExecutionFailure
import org.gradle.integtests.fixtures.executer.ExecutionResult
import org.gradle.internal.reflect.problems.ValidationProblemId
import org.gradle.internal.reflect.validation.ValidationMessageChecker
import org.gradle.internal.reflect.validation.ValidationTestFor
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.UnitTestPreconditions
import org.gradle.testing.fixture.GroovyCoverage
import org.junit.Assume
import org.junit.Rule
import spock.lang.Ignore
import spock.lang.Issue

import static org.gradle.util.internal.GroovyDependencyUtil.groovyModuleDependency

@TargetCoverage({ GroovyCoverage.SUPPORTED_BY_JDK })
abstract class AbstractBasicGroovyCompilerIntegrationSpec extends MultiVersionIntegrationSpec implements ValidationMessageChecker {
    @Rule
    TestResources resources = new TestResources(temporaryFolder)

    String groovyDependency

    String getGroovyVersionNumber() {
        version.split(":", 2)[0]
    }

    def setup() {
        // necessary for picking up some of the output/errorOutput when forked executer is used
        executer.withArgument("-i")
        executer.withRepositoryMirrors()
        groovyDependency = groovyModuleDependency("groovy", versionNumber)
    }

    def "compileGoodCode"() {
        if (module == "groovy-all") {
            // No groovy-all for indy variant
            Assume.assumeTrue(versionClassifier != "indy")
        }
        groovyDependency = groovyModuleDependency(module, versionNumber)

        expect:
        succeeds("compileGroovy")
        groovyClassFile("Person.class").exists()
        groovyClassFile("Address.class").exists()

        where:
        module << ["groovy", "groovy-all"]
    }

    def "compileWithAnnotationProcessor"() {
        Assume.assumeFalse(versionLowerThan("1.7"))

        when:
        writeAnnotationProcessingBuild(
            "", // no Java
            "$annotationText class Groovy {}"
        )
        setupAnnotationProcessor()
        enableAnnotationProcessingOfJavaStubs()

        then:
        succeeds("compileGroovy")
        groovyClassFile('Groovy.class').exists()
        groovyGeneratedSourceFile('Groovy$$Generated.java').exists()
        groovyClassFile('Groovy$$Generated.class').exists()
    }

    def "can compile with annotation processor that takes arguments"() {
        Assume.assumeFalse(versionLowerThan("1.7"))

        when:
        writeAnnotationProcessingBuild(
            "", // no Java
            "$annotationText class Groovy {}"
        )
        setupAnnotationProcessor()
        enableAnnotationProcessingOfJavaStubs()
        buildFile << """
            compileGroovy.options.compilerArgumentProviders.add(new SuffixArgumentProvider("Gen"))
            class SuffixArgumentProvider implements CommandLineArgumentProvider {
                @Input
                String suffix

                SuffixArgumentProvider(String suffix) {
                    this.suffix = suffix
                }

                @Override
                List<String> asArguments() {
                    ["-Asuffix=\${suffix}".toString()]
                }
            }
        """

        then:
        succeeds("compileGroovy")
        groovyClassFile('Groovy.class').exists()
        groovyGeneratedSourceFile('Groovy$$Gen.java').exists()
        groovyClassFile('Groovy$$Gen.class').exists()
    }

    def "disableIncrementalCompilationWithAnnotationProcessor"() {
        Assume.assumeFalse(versionLowerThan("1.7"))

        when:
        writeAnnotationProcessingBuild(
            "", // no Java
            "$annotationText class Groovy {}"
        )
        setupAnnotationProcessor()
        enableAnnotationProcessingOfJavaStubs()
        enableIncrementalCompilation()

        then:
        fails("compileGroovy")
        failure.assertHasCause(
            'Enabling incremental compilation and configuring Java annotation processors for Groovy compilation is not allowed. ' +
                'Disable incremental Groovy compilation or remove the Java annotation processor configuration.')
    }

    def "compileBadCodeWithAnnotationProcessor"() {
        Assume.assumeFalse(versionLowerThan("1.7"))

        when:
        writeAnnotationProcessingBuild(
            "", // no Java
            "$annotationText class Groovy { def m() { $nonCompilableImperativeGroovy } }"
        )
        setupAnnotationProcessor()
        enableAnnotationProcessingOfJavaStubs()

        then:
        fails("compileGroovy")
        checkCompileOutput('unable to resolve class')
        failure.assertHasCause(compilationFailureMessage)

        file('build/classes/stub/Groovy.java').exists()
        groovyClassFile('Groovy.class').exists()
        groovyGeneratedSourceFile('Groovy$$Generated.java').exists()
        groovyClassFile('Groovy$$Generated.class').exists()
    }

    def "compileBadCodeWithoutAnnotationProcessor"() {
        when:
        writeAnnotationProcessingBuild(
            "", // no Java
            "$annotationText class Groovy { def m() { $nonCompilableImperativeGroovy } }"
        )
        enableAnnotationProcessingOfJavaStubs()

        then:
        fails("compileGroovy")
        checkCompileOutput('unable to resolve class')
        failure.assertHasCause(compilationFailureMessage)

        // No Groovy stubs will be created if there are no java files
        // and an annotation processor is not on the classpath
        !file('build/classes/stub/Groovy.java').exists()
        !groovyGeneratedSourceFile('Groovy$$Generated.java').exists()
        !groovyClassFile('Groovy.class').exists()
        !groovyClassFile('Groovy$$Generated.class').exists()
    }

    def "compileBadCodeWithAnnotationProcessorDisabled"() {
        when:
        writeAnnotationProcessingBuild(
            "", // no Java
            "$annotationText class Groovy { void m() { $nonCompilableImperativeGroovy } }")
        setupAnnotationProcessor()
        enableAnnotationProcessingOfJavaStubs()

        buildFile << """
            compileGroovy {
                options.compilerArgs << '-proc:none'
            }
        """

        then:
        fails("compileGroovy")
        checkCompileOutput('unable to resolve class')
        failure.assertHasCause(compilationFailureMessage)

        // Because annotation processing is disabled
        // No Groovy stubs will be created
        !file('build/classes/stub/Groovy.java').exists()
        !groovyGeneratedSourceFile('Groovy$$Generated.java').exists()
        !groovyClassFile('Groovy.class').exists()
        !groovyClassFile('Groovy$$Generated.class').exists()
    }

    def "jointCompileBadCodeWithoutAnnotationProcessor"() {
        when:
        writeAnnotationProcessingBuild(
            "public class Java {}",
            "class Groovy { def m() { $nonCompilableImperativeGroovy } }"
        )
        enableAnnotationProcessingOfJavaStubs()

        then:
        fails("compileGroovy")
        checkCompileOutput('unable to resolve class')
        failure.assertHasCause(compilationFailureMessage)

        // If there is no annotation processor on the classpath,
        // the Groovy stub class won't be compiled, because it is not
        // referenced by any java code in the joint compile
        file('build/classes/stub/Groovy.java').exists()
        !groovyClassFile('Groovy.class').exists()
        groovyClassFile('Java.class').exists()
    }

    def "jointCompileWithAnnotationProcessor"() {
        Assume.assumeFalse(versionLowerThan("1.7"))

        when:
        writeAnnotationProcessingBuild(
            "$annotationText public class Java {}",
            "$annotationText class Groovy {}"
        )
        setupAnnotationProcessor()
        enableAnnotationProcessingOfJavaStubs()

        then:
        succeeds("compileGroovy")
        groovyClassFile('Groovy.class').exists()
        groovyClassFile('Java.class').exists()
        groovyGeneratedSourceFile('Groovy$$Generated.java').exists()
        groovyGeneratedSourceFile('Java$$Generated.java').exists()
        groovyClassFile('Groovy$$Generated.class').exists()
        groovyClassFile('Java$$Generated.class').exists()
    }

    def "jointCompileWithJavaAnnotationProcessorOnly"() {
        when:
        writeAnnotationProcessingBuild(
            "$annotationText public class Java {}",
            "$annotationText class Groovy {}"
        )
        setupAnnotationProcessor()

        then:
        succeeds("compileGroovy")
        groovyClassFile('Java.class').exists()
        groovyClassFile('Groovy.class').exists()
        !groovyGeneratedSourceFile('Groovy$$Generated.java').exists()
        groovyGeneratedSourceFile('Java$$Generated.java').exists()
        !groovyClassFile('Groovy$$Generated.class').exists()
        groovyClassFile('Java$$Generated.class').exists()
    }

    def "jointCompileBadCodeWithAnnotationProcessor"() {
        Assume.assumeFalse(versionLowerThan("1.7"))

        when:
        writeAnnotationProcessingBuild(
            "$annotationText public class Java {}",
            "$annotationText class Groovy { void m() { $nonCompilableImperativeGroovy } }"
        )
        setupAnnotationProcessor()
        enableAnnotationProcessingOfJavaStubs()

        then:
        fails("compileGroovy")
        checkCompileOutput('unable to resolve class')
        failure.assertHasCause(compilationFailureMessage)

        // Because there is an annotation processor on the classpath,
        // the Java stub of Groovy.groovy will be compiled even if
        // it's not referenced by any other java code, even if the
        // Groovy compiler fails to compile the same class.
        file('build/classes/stub/Groovy.java').exists()
        groovyClassFile('Groovy.class').exists()
        groovyClassFile('Java.class').exists()
        groovyGeneratedSourceFile('Groovy$$Generated.java').exists()
        groovyGeneratedSourceFile('Java$$Generated.java').exists()
        groovyClassFile('Groovy$$Generated.class').exists()
        groovyClassFile('Java$$Generated.class').exists()
    }

    def "jointCompileWithAnnotationProcessorDisabled"() {
        when:
        writeAnnotationProcessingBuild(
            "$annotationText public class Java {}",
            "$annotationText class Groovy { }"
        )
        setupAnnotationProcessor()
        enableAnnotationProcessingOfJavaStubs()

        buildFile << """
            compileGroovy {
                options.compilerArgs << '-proc:none'
            }
        """

        then:
        succeeds("compileGroovy")
        groovyClassFile('Groovy.class').exists()
        groovyClassFile('Java.class').exists()
        !groovyGeneratedSourceFile('Groovy$$Generated.java').exists()
        !groovyGeneratedSourceFile('Java$$Generated.java').exists()
        !groovyClassFile('Groovy$$Generated.class').exists()
        !groovyClassFile('Java$$Generated.class').exists()
    }

    def "jointCompileBadCodeWithAnnotationProcessorDisabled"() {
        when:
        writeAnnotationProcessingBuild(
            "$annotationText public class Java {}",
            "$annotationText class Groovy { void m() { $nonCompilableImperativeGroovy } }"
        )
        setupAnnotationProcessor()
        enableAnnotationProcessingOfJavaStubs()

        buildFile << """
            compileGroovy {
                options.annotationProcessorPath = files()
            }
        """

        then:
        fails("compileGroovy")
        checkCompileOutput('unable to resolve class')
        failure.assertHasCause(compilationFailureMessage)

        // Because annotation processing is disabled
        // the Groovy class won't be compiled, because it is not
        // referenced by any java code in the joint compile
        file('build/classes/stub/Groovy.java').exists()
        !groovyClassFile('Groovy.class').exists()
        groovyClassFile('Java.class').exists()
        !groovyGeneratedSourceFile('Groovy$$Generated.java').exists()
        !groovyGeneratedSourceFile('Java$$Generated.java').exists()
        !groovyClassFile('Groovy$$Generated.class').exists()
        !groovyClassFile('Java$$Generated.class').exists()
    }

    def "groovyToolClassesAreNotVisible"() {
        Assume.assumeFalse(versionLowerThan("3.0"))

        expect:
        fails("compileGroovy")
        failure.assertHasErrorOutput('unable to resolve class groovy.ant.AntBuilder')

        when:
        buildFile << "dependencies { implementation '${groovyModuleDependency("groovy-ant", versionNumber)}' }"

        then:
        succeeds("compileGroovy")
        groovyClassFile("Thing.class").exists()
    }

    def "compileBadCode"() {
        expect:
        fails("compileGroovy")
        failure.assertHasErrorOutput 'unable to resolve class Unknown1'
        failure.assertHasErrorOutput 'unable to resolve class Unknown2'
        failure.assertHasCause(compilationFailureMessage)
    }

    def "compileBadJavaCode"() {
        expect:
        fails("compileGroovy")
        failure.assertHasErrorOutput 'illegal start of type'
        failure.assertHasCause(compilationFailureMessage)
    }

    def "canCompileAgainstGroovyClassThatDependsOnExternalClass"() {
        Assume.assumeFalse(versionLowerThan("3.0"))

        buildFile << "dependencies { implementation '${groovyModuleDependency("groovy-test", versionNumber)}' }"
        expect:
        succeeds("test")
    }

    def "canListSourceFiles"() {
        expect:
        succeeds("compileGroovy")
        output.contains(new File("src/main/groovy/compile/test/Person.groovy").toString())
        output.contains(new File("src/main/groovy/compile/test/Person2.groovy").toString())
    }

    def "configurationScriptNotSupported"() {
        Assume.assumeTrue(versionLowerThan("2.1"))

        expect:
        fails("compileGroovy")
        failure.assertHasCause("Using a Groovy compiler configuration script requires Groovy 2.1+ but found Groovy $groovyVersionNumber")
    }

    def "useConfigurationScript"() {
        Assume.assumeFalse(versionLowerThan("2.1"))
        Assume.assumeFalse('Test must run with 9+, cannot guarantee that with a lower toolchain', getClass().name.contains("LowerToolchain"))

        expect:
        fails("compileGroovy")
        checkCompileOutput('Cannot find matching method java.lang.String#bar()')
    }

    @ValidationTestFor(
        ValidationProblemId.INPUT_FILE_DOES_NOT_EXIST
    )
    def "failsBecauseOfMissingConfigFile"() {
        Assume.assumeFalse(versionLowerThan("2.1"))
        expectReindentedValidationMessage()

        expect:
        def configFile = file('groovycompilerconfig.groovy')
        fails("compileGroovy")
        failureDescriptionContains(inputDoesNotExist {
            type('org.gradle.api.tasks.compile.GroovyCompile')
                .property('groovyOptions.configurationScript')
                .file(configFile)
                .includeLink()
        })
    }

    def "failsBecauseOfInvalidConfigFile"() {
        Assume.assumeFalse(versionLowerThan("2.1"))
        expect:
        fails("compileGroovy")
        failure.assertHasCause("Could not execute Groovy compiler configuration script: ${file('groovycompilerconfig.groovy')}")
    }

    // JavaFx was removed in JDK 10
    // We don't have Oracle Java 8 on Windows any more
    @Requires([
        UnitTestPreconditions.Jdk9OrEarlier,
        UnitTestPreconditions.NotWindows
    ])
    def "compileJavaFx8Code"() {
        Assume.assumeFalse("Setup invalid with toolchains", getClass().name.contains('Toolchain') && !getClass().name.contains('SameToolchain'))

        expect:
        succeeds("compileGroovy")
    }

    def "cant compile against gradle base services"() {
        def gradleBaseServicesClass = Action
        buildScript """
            apply plugin: 'groovy'
            ${mavenCentralRepository()}
        """

        when:
        file("src/main/groovy/Groovy.groovy") << """
            import ${gradleBaseServicesClass.name}
            class Groovy {}
        """

        then:
        fails("compileGroovy")
        checkCompileOutput("unable to resolve class ${gradleBaseServicesClass.name}")
    }

    @Ignore
    @Issue("https://issues.gradle.org/browse/GRADLE-3377")
    @Requires(UnitTestPreconditions.Online)
    def "can compile with Groovy library resolved by classifier"() {
        def gradleBaseServicesClass = Action
        buildScript """
            apply plugin: 'groovy'
            ${mavenCentralRepository()}
            dependencies {
                implementation 'org.codehaus.groovy:groovy:2.4.3:grooid'
            }
        """

        when:
        file("src/main/groovy/Groovy.groovy") << """
            import ${gradleBaseServicesClass.name}
            class Groovy {}
        """

        then:
        succeeds("compileGroovy")
    }

    def "compile bad groovy code do not fail the build when options.failOnError is false"() {
        given:
        buildFile << """
            apply plugin: "groovy"
            ${mavenCentralRepository()}
            compileGroovy.options.failOnError = false
        """.stripIndent()

        and:
        badCode()

        expect:
        succeeds 'compileGroovy'
    }

    def "compile bad groovy code do not fail the build when groovyOptions.failOnError is false"() {
        given:
        buildFile << """
            apply plugin: "groovy"
            ${mavenCentralRepository()}
            compileGroovy.groovyOptions.failOnError = false
        """.stripIndent()

        and:
        badCode()

        expect:
        succeeds 'compileGroovy'
    }

    def "joint compile bad java code do not fail the build when options.failOnError is false"() {
        given:
        buildFile << """
            apply plugin: "groovy"
            ${mavenCentralRepository()}
            compileGroovy.options.failOnError = false
        """.stripIndent()

        and:
        goodCode()
        badJavaCode()

        expect:
        succeeds 'compileGroovy'
    }

    def "joint compile bad java code do not fail the build when groovyOptions.failOnError is false"() {
        given:
        buildFile << """
            apply plugin: "groovy"
            ${mavenCentralRepository()}
            compileGroovy.groovyOptions.failOnError = false
        """.stripIndent()

        and:
        goodCode()
        badJavaCode()

        expect:
        succeeds 'compileGroovy'
    }

    def goodCode() {
        file("src/main/groovy/compile/test/Person.groovy") << """
            package compile.test
            class Person {}
        """.stripIndent()
    }

    def badCode() {
        file("src/main/groovy/compile/test/Person.groovy") << """
            package compile.test
            class Person extends {}
        """.stripIndent()
    }

    def badJavaCode() {
        file("src/main/groovy/compile/test/Something.java") << """
            package compile.test;
            class Something extends {}
        """.stripIndent()
    }


    protected ExecutionResult run(String... tasks) {
        configureGroovy()
        super.run(tasks)
    }

    protected ExecutionFailure runAndFail(String... tasks) {
        configureGroovy()
        super.runAndFail(tasks)
    }

    protected ExecutionResult succeeds(String... tasks) {
        configureGroovy()
        super.succeeds(tasks)
    }

    protected ExecutionFailure fails(String... tasks) {
        configureGroovy()
        super.fails(tasks)
    }

    private void configureGroovy() {
        buildFile << """
dependencies {
    implementation '${groovyDependency.toString()}'
}

${compilerConfiguration()}
        """
    }

    abstract String compilerConfiguration()

    String getCompilationFailureMessage() {
        return "Compilation failed; see the compiler error output for details."
    }

    boolean versionLowerThan(String other) {
        compareToVersion(other) < 0
    }

    int compareToVersion(String other) {
        def versionParts = groovyVersionNumber.split("\\.") as List
        def otherParts = other.split("\\.") as List
        def ordering = Ordering.<Integer> natural().lexicographical()
        ordering.compare(versionParts, otherParts)
    }

    String getAnnotationText() {
        "@com.test.SimpleAnnotation"
    }

    String getNonCompilableImperativeGroovy() {
        "Bad code = new thatDoesntAffectStubGeneration()"
    }

    def writeAnnotationProcessorProject() {
        file("processor").create {
            file("build.gradle") << """apply plugin: 'java'

${annotationProcessorExtraSetup()}
"""
            "src/main" {
                file("resources/META-INF/services/javax.annotation.processing.Processor") << "com.test.SimpleAnnotationProcessor"
                "java/com/test/" {
                    file("SimpleAnnotation.java") << """
                        package com.test;

                        import java.lang.annotation.ElementType;
                        import java.lang.annotation.Retention;
                        import java.lang.annotation.RetentionPolicy;
                        import java.lang.annotation.Target;

                        @Retention(RetentionPolicy.SOURCE)
                        @Target(ElementType.TYPE)
                        public @interface SimpleAnnotation {}
                    """

                    file("SimpleAnnotationProcessor.java") << """
                        package com.test;

                        import java.io.BufferedWriter;
                        import java.io.IOException;
                        import java.io.Writer;
                        import java.util.Set;
                        import java.util.Map;

                        import javax.annotation.processing.*;
                        import javax.lang.model.element.Element;
                        import javax.lang.model.element.TypeElement;
                        import javax.lang.model.SourceVersion;
                        import javax.tools.JavaFileObject;

                        @SupportedAnnotationTypes("com.test.SimpleAnnotation")
                        @SupportedOptions({ "suffix" })
                        public class SimpleAnnotationProcessor extends AbstractProcessor {
                            private Map<String, String> options;

                            @Override
                            public synchronized void init(ProcessingEnvironment processingEnv) {
                                super.init(processingEnv);
                                options = processingEnv.getOptions();
                            }

                            @Override
                            public boolean process(final Set<? extends TypeElement> annotations, final RoundEnvironment roundEnv) {
                                if (isClasspathContaminated()) {
                                    throw new RuntimeException("Annotation Processor Classpath is contaminated by Gradle ClassLoader");
                                }

                                final String suffix = options.getOrDefault("suffix", "Generated");
                                for (final Element classElement : roundEnv.getElementsAnnotatedWith(SimpleAnnotation.class)) {
                                    final String className = String.format("%s\$\$%s", classElement.getSimpleName().toString(), suffix);

                                    Writer writer = null;
                                    try {
                                        final JavaFileObject file = processingEnv.getFiler().createSourceFile(className);

                                        writer = new BufferedWriter(file.openWriter());
                                        writer.append(String.format("public class %s {\\n", className));
                                        writer.append("}");
                                    } catch (final IOException e) {
                                        throw new RuntimeException(e);
                                    } finally {
                                        if (writer != null) {
                                            try {
                                                writer.close();
                                            } catch (final IOException e) {
                                                throw new RuntimeException(e);
                                            }
                                        }
                                    }
                                }

                                return true;
                            }

                            @Override
                            public SourceVersion getSupportedSourceVersion() {
                                return SourceVersion.latestSupported();
                            }

                            private boolean isClasspathContaminated() {
                                try {
                                    Class.forName("$Action.name");
                                    return true;
                                } catch (final ClassNotFoundException e) {
                                    return false;
                                }
                            }
                        }
                    """
                }
            }
        }
    }

    String annotationProcessorExtraSetup() {
        ""
    }

    String checkCompileOutput(String errorMessage) {
        failure.assertHasErrorOutput(errorMessage)
    }

    def writeAnnotationProcessingBuild(String java, String groovy) {
        buildFile << """
            apply plugin: "groovy"
            ${mavenCentralRepository()}
            compileGroovy {
                groovyOptions.with {
                    stubDir = file("\$buildDir/classes/stub")
                    keepStubs = true
                }
            }
        """

        if (java) {
            file("src/main/groovy/Java.java") << java
        }
        if (groovy) {
            file("src/main/groovy/Groovy.groovy") << groovy
        }
    }

    private void setupAnnotationProcessor() {
        settingsFile << "include 'processor'"
        writeAnnotationProcessorProject()
        buildFile << """
                dependencies {
                    compileOnly project(":processor")
                    annotationProcessor project(":processor")
                }
            """
    }

    private TestFile enableAnnotationProcessingOfJavaStubs() {
        buildFile << """
                compileGroovy.groovyOptions.javaAnnotationProcessing = true
            """
    }

    private void enableIncrementalCompilation() {
        FeaturePreviewsFixture.enableGroovyCompilationAvoidance(settingsFile)
        buildFile << '''
tasks.withType(GroovyCompile) {
    options.incremental = true
}
'''
    }
}

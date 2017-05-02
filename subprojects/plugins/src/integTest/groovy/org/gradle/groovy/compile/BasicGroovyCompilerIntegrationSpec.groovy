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
import org.gradle.integtests.fixtures.MultiVersionIntegrationSpec
import org.gradle.integtests.fixtures.TargetVersions
import org.gradle.integtests.fixtures.TestResources
import org.gradle.integtests.fixtures.executer.ExecutionFailure
import org.gradle.integtests.fixtures.executer.ExecutionResult
import org.gradle.test.fixtures.file.TestFile
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition
import org.junit.Rule
import spock.lang.Ignore
import spock.lang.Issue

@TargetVersions(['1.5.8', '1.6.9', '1.7.11', '1.8.8', '2.0.5', '2.1.9', '2.2.2', '2.3.10', '2.4.10'])
abstract class BasicGroovyCompilerIntegrationSpec extends MultiVersionIntegrationSpec {
    @Rule
    TestResources resources = new TestResources(temporaryFolder)

    String groovyDependency = "org.codehaus.groovy:groovy-all:$version"

    String getGroovyVersionNumber() {
        version.split(":", 2)[0]
    }

    def setup() {
        // necessary for picking up some of the output/errorOutput when forked executer is used
        executer.withArgument("-i")
    }

    def "compileGoodCode"() {
        groovyDependency = "org.codehaus.groovy:$module:$version"

        expect:
        succeeds("compileGroovy")
        !errorOutput
        groovyClassFile("Person.class").exists()
        groovyClassFile("Address.class").exists()

        where:
        module << ["groovy-all", "groovy"]
    }

    def "compileWithAnnotationProcessor"() {
        if (versionLowerThan("1.7")) {
            return
        }

        when:
        writeAnnotationProcessingBuild(
            "", // no Java
            "$annotationText class Groovy {}"
        )
        setupAnnotationProcessor()
        enableAnnotationProcessingOfJavaStubs()

        then:
        succeeds("compileGroovy")
        !errorOutput
        groovyClassFile('Groovy.class').exists()
        groovyClassFile('Groovy$$Generated.java').exists()
        groovyClassFile('Groovy$$Generated.class').exists()
    }

    def "compileBadCodeWithAnnotationProcessor"() {
        if (versionLowerThan("1.7")) {
            return
        }

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
        groovyClassFile('Groovy$$Generated.java').exists()
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
        !groovyClassFile('Groovy$$Generated.java').exists()
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
        !groovyClassFile('Groovy$$Generated.java').exists()
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
        if (versionLowerThan("1.7")) {
            return
        }

        when:
        writeAnnotationProcessingBuild(
            "$annotationText public class Java {}",
            "$annotationText class Groovy {}"
        )
        setupAnnotationProcessor()
        enableAnnotationProcessingOfJavaStubs()

        then:
        succeeds("compileGroovy")
        !errorOutput
        groovyClassFile('Groovy.class').exists()
        groovyClassFile('Java.class').exists()
        groovyClassFile('Groovy$$Generated.java').exists()
        groovyClassFile('Java$$Generated.java').exists()
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
        !errorOutput
        groovyClassFile('Java.class').exists()
        groovyClassFile('Groovy.class').exists()
        !groovyClassFile('Groovy$$Generated.java').exists()
        groovyClassFile('Java$$Generated.java').exists()
        !groovyClassFile('Groovy$$Generated.class').exists()
        groovyClassFile('Java$$Generated.class').exists()
    }

    def "jointCompileBadCodeWithAnnotationProcessor"() {
        if (versionLowerThan("1.7")) {
            return
        }

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
        groovyClassFile('Groovy$$Generated.java').exists()
        groovyClassFile('Java$$Generated.java').exists()
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
        !errorOutput
        groovyClassFile('Groovy.class').exists()
        groovyClassFile('Java.class').exists()
        !groovyClassFile('Groovy$$Generated.java').exists()
        !groovyClassFile('Java$$Generated.java').exists()
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
                options.compilerArgs << '-proc:none'
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
        !groovyClassFile('Groovy$$Generated.java').exists()
        !groovyClassFile('Java$$Generated.java').exists()
        !groovyClassFile('Groovy$$Generated.class').exists()
        !groovyClassFile('Java$$Generated.class').exists()
    }

    def "groovyToolClassesAreNotVisible"() {
        if (versionLowerThan("2.0")) {
            return
        }

        groovyDependency = "org.codehaus.groovy:groovy:$version"

        expect:
        fails("compileGroovy")
        errorOutput.contains('unable to resolve class AntBuilder')

        when:
        buildFile << "dependencies { compile 'org.codehaus.groovy:groovy-ant:${version}' }"

        then:
        succeeds("compileGroovy")
        !errorOutput
        groovyClassFile("Thing.class").exists()
    }

    def "compileBadCode"() {
        expect:
        fails("compileGroovy")
        // for some reasons, line breaks occur in different places when running this
        // test in different environments; hence we only check for short snippets
        compileErrorOutput.contains 'unable'
        compileErrorOutput.contains 'resolve'
        compileErrorOutput.contains 'Unknown1'
        compileErrorOutput.contains 'Unknown2'
        failure.assertHasCause(compilationFailureMessage)
    }

    def "compileBadJavaCode"() {
        expect:
        fails("compileGroovy")
        compileErrorOutput.contains 'illegal start of type'
        failure.assertHasCause(compilationFailureMessage)
    }

    def "canCompileAgainstGroovyClassThatDependsOnExternalClass"() {
        expect:
        succeeds("test")
    }

    def "canListSourceFiles"() {
        expect:
        succeeds("compileGroovy")
        output.contains(new File("src/main/groovy/compile/test/Person.groovy").toString())
        output.contains(new File("src/main/groovy/compile/test/Person2.groovy").toString())
        !errorOutput
    }

    def "configurationScriptNotSupported"() {
        if (!versionLowerThan("2.1")) {
            return
        }

        expect:
        fails("compileGroovy")
        failure.assertHasCause("Using a Groovy compiler configuration script requires Groovy 2.1+ but found Groovy $groovyVersionNumber")
    }

    def "useConfigurationScript"() {
        if (versionLowerThan("2.1")) {
            return
        }

        expect:
        fails("compileGroovy")
        checkCompileOutput('Cannot find matching method java.lang.String#bar()')
    }

    def "failsBecauseOfMissingConfigFile"() {
        if (versionLowerThan("2.1")) {
            return
        }
        expect:
        fails("compileGroovy")
        failure.assertHasCause("File '${file('groovycompilerconfig.groovy')}' specified for property 'groovyOptions.configurationScript' does not exist.")
    }

    def "failsBecauseOfInvalidConfigFile"() {
        if (versionLowerThan("2.1")) {
            return
        }
        expect:
        fails("compileGroovy")
        failure.assertHasCause("Could not execute Groovy compiler configuration script: ${file('groovycompilerconfig.groovy')}")
    }

    @Requires(TestPrecondition.JDK8_OR_LATER)
    def "compileJavaFx8Code"() {
        expect:
        succeeds("compileGroovy")
    }

    def "cant compile against gradle base services"() {
        def gradleBaseServicesClass = Action
        buildScript """
            apply plugin: 'groovy'
            repositories { mavenCentral() }
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
    @Requires(TestPrecondition.ONLINE)
    def "can compile with Groovy library resolved by classifier"() {
        def gradleBaseServicesClass = Action
        buildScript """
            apply plugin: 'groovy'
            repositories { mavenCentral() }
            dependencies {
                compile 'org.codehaus.groovy:groovy:2.4.3:grooid'
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
            repositories { mavenCentral() }
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
            repositories { mavenCentral() }
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
            repositories { mavenCentral() }
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
            repositories { mavenCentral() }
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
    compile '${groovyDependency.toString()}'
}

${compilerConfiguration()}
        """
    }

    abstract String compilerConfiguration()

    String getCompilationFailureMessage() {
        return "Compilation failed; see the compiler error output for details."
    }

    String getCompileErrorOutput() {
        return errorOutput
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
            file("build.gradle") << "apply plugin: 'java'"
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

                        import javax.annotation.processing.AbstractProcessor;
                        import javax.annotation.processing.RoundEnvironment;
                        import javax.annotation.processing.SupportedAnnotationTypes;
                        import javax.lang.model.element.Element;
                        import javax.lang.model.element.TypeElement;
                        import javax.lang.model.SourceVersion;
                        import javax.tools.JavaFileObject;

                        @SupportedAnnotationTypes("com.test.SimpleAnnotation")
                        public class SimpleAnnotationProcessor extends AbstractProcessor {
                            @Override
                            public boolean process(final Set<? extends TypeElement> annotations, final RoundEnvironment roundEnv) {
                                if (${gradleLeaksIntoAnnotationProcessor() ? '!' : ''}isClasspathContaminated()) {
                                    throw new RuntimeException("Annotation Processor Classpath is ${gradleLeaksIntoAnnotationProcessor() ? 'not ' : ''}}contaminated by Gradle ClassLoader");
                                }

                                for (final Element classElement : roundEnv.getElementsAnnotatedWith(SimpleAnnotation.class)) {
                                    final String className = String.format("%s\$\$Generated", classElement.getSimpleName().toString());

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

    String checkCompileOutput(String errorMessage) {
        compileErrorOutput.contains(errorMessage)
    }

    protected boolean gradleLeaksIntoAnnotationProcessor() {
        return false;
    }

    def writeAnnotationProcessingBuild(String java, String groovy) {
        buildFile << """
            apply plugin: "groovy"
            repositories { mavenCentral() }
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
                    compile project(":processor")
                }
            """
    }

    private TestFile enableAnnotationProcessingOfJavaStubs() {
        buildFile << """
                compileGroovy.groovyOptions.javaAnnotationProcessing = true
            """
    }
}

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


package org.gradle.java.compile

import org.gradle.api.Action
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.ToBeFixedForInstantExecution
import org.gradle.test.fixtures.file.ClassFile
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition

abstract class BasicJavaCompilerIntegrationSpec extends AbstractIntegrationSpec {
    def setup() {
        executer.withArguments("-i")
        buildFile << buildScript()
        buildFile << """
    ${compilerConfiguration()}
"""
    }

    def compileGoodCode() {
        given:
        goodCode()

        expect:
        succeeds("compileJava")
        output.contains(logStatement())
        javaClassFile("compile/test/Person.class").exists()
    }

    def compileBadCodeBreaksTheBuild() {
        given:
        badCode()

        expect:
        fails("compileJava")
        output.contains(logStatement())
        failure.assertHasErrorOutput("';' expected")
        javaClassFile("").assertHasDescendants()
    }

    def compileBadCodeWithoutFailing() {
        given:
        badCode()

        and:
        buildFile << 'compileJava.options.failOnError = false'

        expect:
        succeeds("compileJava")
        output.contains(logStatement())
        result.assertHasErrorOutput("';' expected")
        javaClassFile("").assertHasDescendants()
    }

    @ToBeFixedForInstantExecution
    def compileWithSpecifiedEncoding() {
        given:
        goodCodeEncodedWith('ISO8859_7')

        and:
        buildFile << '''
            apply plugin: 'application'
            mainClassName = 'Main'
            compileJava.options.encoding = \'ISO8859_7\'
'''

        expect:
        succeeds("run")
        output.contains(logStatement())
        file('encoded.out').getText("utf-8") == "\u03b1\u03b2\u03b3"
    }

    @ToBeFixedForInstantExecution
    def compilesWithSpecifiedDebugSettings() {
        given:
        goodCode()

        when:
        run("compileJava")

        then:
        def fullDebug = classFile("compile/test/Person.class")
        fullDebug.debugIncludesSourceFile
        fullDebug.debugIncludesLineNumbers
        fullDebug.debugIncludesLocalVariables

        when:
        buildFile << """
compileJava.options.debugOptions.debugLevel='lines'
"""
        run("compileJava")

        then:
        def linesOnly = classFile("compile/test/Person.class")
        !linesOnly.debugIncludesSourceFile
        linesOnly.debugIncludesLineNumbers
        !linesOnly.debugIncludesLocalVariables

        when:
        buildFile << """
compileJava.options.debug = false
"""
        run("compileJava")

        then:
        def noDebug = classFile("compile/test/Person.class")
        !noDebug.debugIncludesSourceFile
        !noDebug.debugIncludesLineNumbers
        !noDebug.debugIncludesLocalVariables
    }

    // JavaFx was removed in JDK 10
    @Requires(TestPrecondition.JDK9_OR_EARLIER)
    def "compileJavaFx8Code"() {
        given:
        file("src/main/java/compile/test/FxApp.java") << '''
import javafx.application.Application;
import javafx.stage.Stage;

public class FxApp extends Application {
    public void start(Stage stage) {
    }
}
'''

        expect:
        succeeds("compileJava")
    }

    @Requires(TestPrecondition.JDK9_OR_LATER)
    def "compile with release option"() {
        given:
        goodCode()
        buildFile << """
compileJava.options.compilerArgs.addAll(['--release', '8'])
"""

        expect:
        succeeds 'compileJava'
        bytecodeVersion() == 52
    }

    @Requires(TestPrecondition.JDK9_OR_LATER)
    def "compile with release property set"() {
        given:
        goodCode()
        buildFile << """
compileJava.release.set(8)
"""

        expect:
        succeeds 'compileJava'
        bytecodeVersion() == 52
    }

    @Requires(TestPrecondition.JDK9_OR_LATER)
    def "compile with release property set in plugin extension"() {
        given:
        goodCode()
        buildFile << """
java.release.set(8)
"""

        expect:
        succeeds 'compileJava'
        bytecodeVersion() == 52
    }

    @Requires(TestPrecondition.JDK9_OR_LATER)
    def "compile fails when using newer API with release option"() {
        given:
        file("src/main/java/compile/test/FailsOnJava8.java") << '''
package compile.test;

import java.util.stream.Stream;
import java.util.function.Predicate;

public class FailsOnJava8<T> {
    public Stream<T> takeFromStream(Stream<T> stream) {
        return stream.takeWhile(Predicate.isEqual("foo"));
    }
}
'''

        buildFile << """
compileJava.options.compilerArgs.addAll(['--release', '8'])
"""

        expect:
        fails 'compileJava'
        output.contains(logStatement())
        failure.assertHasErrorOutput("cannot find symbol")
        failure.assertHasErrorOutput("method takeWhile")
    }

    def buildScript() {
        """
apply plugin: "java"
${mavenCentralRepository()}

dependencies {
    implementation "org.codehaus.groovy:groovy:2.4.10"
}
"""
    }

    abstract compilerConfiguration()

    abstract logStatement()

    def goodCode() {
        file("src/main/java/compile/test/Person.java") << '''
package compile.test;

import org.codehaus.groovy.runtime.DefaultGroovyMethods;

import java.util.Arrays;

public class Person {
    String name;
    int age;

    void hello() {
        Iterable<Integer> vars = Arrays.asList(3, 1, 2);
        DefaultGroovyMethods.max(vars);
    }
}'''
        file("src/main/java/compile/test/Person2.java") << '''
package compile.test;

class Person2 extends Person {
}
'''
    }

    def goodCodeEncodedWith(String encoding) {
        def code = '''
import java.io.FileOutputStream;
import java.io.File;
import java.io.OutputStreamWriter;

class Main {
    public static void main(String[] args) throws Exception {
        // Some lowercase greek letters
        String content = "\u03b1\u03b2\u03b3";
        OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(new File("encoded.out")), "utf-8");
        writer.write(content);
        writer.close();
    }
}
'''
        def file = file("src/main/java/Main.java")
        file.parentFile.mkdirs()
        file.withWriter(encoding) { writer ->
            writer.write(code)
        }

        // Verify some assumptions: that we've got the correct characters in there, and that we're not using the system encoding
        assert code.contains(new String(Character.toChars(0x3b1)))
        assert !Arrays.equals(code.bytes, file.bytes)
    }

    def badCode() {
        file("src/main/java/compile/test/Person.java") << '''
        package compile.test;

        public class Person {
            String name;
            int age;

            void hello() {
                return nothing
            }
        } '''
    }

    def classFile(String path) {
        return new ClassFile(javaClassFile(path))
    }

    def "can use annotation processor"() {
        when:
        buildFile << """
            apply plugin: "java"
            dependencies {
                compileOnly project(":processor")
                annotationProcessor project(":processor")
            }
        """
        settingsFile << "include 'processor'"
        writeAnnotationProcessorProject()

        file("src/main/java/Java.java") << "@com.test.SimpleAnnotation public class Java {}"

        then:
        succeeds("compileJava")
        javaGeneratedSourceFile('Java$$Generated.java').exists()
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
                                if (isClasspathContaminated()) {
                                    throw new RuntimeException("Annotation Processor Classpath is contaminated by Gradle ClassLoader");
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

    def "cant compile against gradle base services"() {
        def gradleBaseServicesClass = Action

        when:
        file("src/main/java/Java.java") << """
            import ${gradleBaseServicesClass.name};
            public class Java {}
        """

        then:
        fails("compileJava")
        failure.assertHasErrorOutput("package ${gradleBaseServicesClass.package.name} does not exist")
    }

    def bytecodeVersion() {
        def classFile = javaClassFile('compile/test/Person.class').newDataInputStream()
        classFile.readInt()
        classFile.readUnsignedShort()
        def majorVersion = classFile.readUnsignedShort()
        classFile.close()
        return majorVersion
    }

}


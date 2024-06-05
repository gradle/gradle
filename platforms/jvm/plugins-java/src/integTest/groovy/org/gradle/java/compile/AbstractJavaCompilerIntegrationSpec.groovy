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


package org.gradle.java.compile

import org.gradle.api.Action
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.AvailableJavaHomes
import org.gradle.integtests.fixtures.jvm.TestJavaClassUtil
import org.gradle.internal.jvm.Jvm
import org.gradle.internal.serialize.JavaClassUtil
import org.gradle.test.fixtures.file.ClassFile
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.UnitTestPreconditions
import org.gradle.util.internal.TextUtil
import org.junit.Assume
import spock.lang.Issue

abstract class AbstractJavaCompilerIntegrationSpec extends AbstractIntegrationSpec {

    abstract String compilerConfiguration()

    abstract String logStatement()

    def setup() {
        executer.withArguments("-i")
        buildFile << """
            plugins {
                id("java-library")
            }
            tasks.withType(JavaCompile) {
                options.compilerArgs << '-Xlint:all,-options' << '-Werror'
            }
        """
        buildFile << compilerConfiguration()
    }

    def "can compile good code"() {
        given:
        goodCode()

        when:
        succeeds("compileJava")

        then:
        output.contains(logStatement())
        javaClassFile("compile/test/Person.class").exists()
    }

    def "compile bad code breaks the build and compilation error doesn't show link to help.gradle.org"() {
        given:
        badCode()

        when:
        fails("compileJava")

        then:
        output.contains(logStatement())
        failure.assertHasErrorOutput("';' expected")
        failure.assertNotOutput("https://help.gradle.org")
        javaClassFile("").assertHasDescendants()
    }

    def "can compile bad code without failing"() {
        given:
        badCode()

        and:
        buildFile << "compileJava.options.failOnError = false"

        when:
        succeeds("compileJava")

        then:
        output.contains(logStatement())
        result.assertHasErrorOutput("';' expected")
        javaClassFile("").assertHasDescendants()
    }

    def "can compile with specific encoding"() {
        given:
        goodCodeEncodedWith('ISO8859_7')

        and:
        buildFile << """
            apply plugin: 'application'

            application {
                mainClass = 'Main'
            }
            compileJava.options.encoding = \'ISO8859_7\'
        """

        when:
        succeeds("run")

        then:
        output.contains(logStatement())
        file('encoded.out').getText("utf-8") == "\u03b1\u03b2\u03b3"
    }

    def "can compile with specified debug settings"() {
        given:
        goodCode()

        when:
        succeeds("compileJava")

        then:
        def fullDebug = classFile("compile/test/Person.class")
        fullDebug.debugIncludesSourceFile
        fullDebug.debugIncludesLineNumbers
        fullDebug.debugIncludesLocalVariables

        when:
        buildFile << """
            compileJava.options.debugOptions.debugLevel='lines'
        """
        succeeds("compileJava")

        then:
        def linesOnly = classFile("compile/test/Person.class")
        !linesOnly.debugIncludesSourceFile
        linesOnly.debugIncludesLineNumbers
        !linesOnly.debugIncludesLocalVariables

        when:
        buildFile << """
            compileJava.options.debug = false
        """
        succeeds("compileJava")

        then:
        def noDebug = classFile("compile/test/Person.class")
        !noDebug.debugIncludesSourceFile
        !noDebug.debugIncludesLineNumbers
        !noDebug.debugIncludesLocalVariables
    }

    // JavaFx was removed in JDK 10
    // JavaFx comes packaged with Oracle JDKs
    @Requires([
        UnitTestPreconditions.Jdk9OrEarlier,
        UnitTestPreconditions.JdkOracle
    ])
    def "can compile JavaFx 8 code"() {
        given:
        file("src/main/java/compile/test/FxApp.java") << """
            import javafx.application.Application;
            import javafx.stage.Stage;

            public class FxApp extends Application {
                public void start(Stage stage) {
                }
            }
        """

        expect:
        succeeds("compileJava")
    }

    def "honors project level compatibility when using toolchain"() {
        given:
        def lower = getLowerJvm()
        def lowerVersion = lower.javaVersion.getMajorVersion()

        and:
        goodCode()
        buildFile << """
            java.sourceCompatibility = JavaVersion.toVersion(${lowerVersion})
            java.targetCompatibility = JavaVersion.toVersion(${lowerVersion})
            java.toolchain {
                languageVersion = JavaLanguageVersion.of(${Jvm.current().javaVersion.majorVersion})
            }

            compileJava {
                ${configureBoostrapClasspath(lower)}
            }

            assert configurations.apiElements.attributes.getAttribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE) == ${lowerVersion}
            assert configurations.runtimeElements.attributes.getAttribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE) == ${lowerVersion}
            assert configurations.compileClasspath.attributes.getAttribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE) == ${lowerVersion}
            assert configurations.runtimeClasspath.attributes.getAttribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE) == ${lowerVersion}
        """

        when:
        succeeds("compileJava")

        then:
        bytecodeVersion() == TestJavaClassUtil.getClassVersion(lower.javaVersion)
    }

    def 'honors task level compatibility when using toolchain'() {
        given:
        def lower = getLowerJvm()
        def lowerVersion = lower.javaVersion.getMajorVersion()

        and:
        goodCode()
        buildFile << """
            java.toolchain {
                languageVersion = JavaLanguageVersion.of(${Jvm.current().javaVersion.majorVersion})
            }

            compileJava {
                ${configureBoostrapClasspath(lower)}
                sourceCompatibility = JavaVersion.toVersion(${lowerVersion})
                targetCompatibility = JavaVersion.toVersion(${lowerVersion})
            }

            assert configurations.apiElements.attributes.getAttribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE) == ${lowerVersion}
            assert configurations.runtimeElements.attributes.getAttribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE) == ${lowerVersion}
            assert configurations.compileClasspath.attributes.getAttribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE) == ${lowerVersion}
            assert configurations.runtimeClasspath.attributes.getAttribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE) == ${lowerVersion}
        """

        when:
        succeeds("compileJava")

        then:
        bytecodeVersion() == TestJavaClassUtil.getClassVersion(lower.javaVersion)
    }

    @Requires(UnitTestPreconditions.Jdk11OrLater)
    def "compile with release flag using #notation notation"() {
        given:
        goodCode()
        buildFile << """
            java.targetCompatibility = JavaVersion.VERSION_1_7 // Ignored
            compileJava {
                options.compilerArgs.addAll(['--release', $notation])
            }

            assert configurations.apiElements.attributes.getAttribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE) == 11
            assert configurations.runtimeElements.attributes.getAttribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE) == 11
            assert configurations.compileClasspath.attributes.getAttribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE) == 11
            assert configurations.runtimeClasspath.attributes.getAttribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE) == 11
        """

        when:
        succeeds("compileJava")

        then:
        bytecodeVersion() == 55

        where:
        notation << [
            "'11'",
            '11', // Integer, see #13351
            '"${11}"' // GString, see #13351
        ]
    }

    @Requires(UnitTestPreconditions.Jdk11OrLater)
    def "compile with release property set"() {
        given:
        goodCode()
        buildFile << """
            java.targetCompatibility = JavaVersion.VERSION_1_7 // Ignored
            compileJava.targetCompatibility = '10' // Ignored
            compileJava {
                options.release.set(11)
            }

            assert configurations.apiElements.attributes.getAttribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE) == 11
            assert configurations.runtimeElements.attributes.getAttribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE) == 11
            assert configurations.compileClasspath.attributes.getAttribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE) == 11
            assert configurations.runtimeClasspath.attributes.getAttribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE) == 11
        """

        when:
        succeeds("compileJava")

        then:
        bytecodeVersion() == 55
    }

    @Requires(UnitTestPreconditions.Jdk9OrLater)
    def "fails to compile with release property and flag set"() {
        given:
        goodCode()
        buildFile << """
            compileJava.options.compilerArgs.addAll(['--release', '12'])
            compileJava.options.release.set(8)
        """

        when:
        fails("compileJava")

        then:
        failureHasCause('Cannot specify --release via `CompileOptions.compilerArgs` when using `JavaCompile.release`.')
    }

    @Requires(UnitTestPreconditions.Jdk11OrLater)
    def "compile with release property and autoTargetJvmDisabled"() {
        given:
        goodCode()
        buildFile << """
            java.disableAutoTargetJvm()
            java.targetCompatibility = JavaVersion.VERSION_1_7 // Ignored

            compileJava {
                targetCompatibility = '10' // Ignored
                options.release.set(11)
            }

            assert configurations.apiElements.attributes.getAttribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE) == 11
            assert configurations.runtimeElements.attributes.getAttribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE) == 11
            assert configurations.compileClasspath.attributes.getAttribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE) == Integer.MAX_VALUE
            assert configurations.runtimeClasspath.attributes.getAttribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE) == Integer.MAX_VALUE
        """

        when:
        succeeds("compileJava")

        then:
        bytecodeVersion() == 55
    }

    def "compile with target compatibility"() {
        given:
        def lower = getLowerJvm()
        def lowerVersion = lower.javaVersion.getMajorVersion()

        and:
        goodCode()
        buildFile << """
            java.targetCompatibility = JavaVersion.VERSION_1_9 // Ignored
            compileJava {
                targetCompatibility = JavaVersion.toVersion(${lowerVersion})
                sourceCompatibility = JavaVersion.toVersion(${lowerVersion})
                ${configureBoostrapClasspath(lower)}
            }

            assert configurations.apiElements.attributes.getAttribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE) == ${lowerVersion}
            assert configurations.runtimeElements.attributes.getAttribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE) == ${lowerVersion}
            assert configurations.compileClasspath.attributes.getAttribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE) == ${lowerVersion}
            assert configurations.runtimeClasspath.attributes.getAttribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE) == ${lowerVersion}
        """

        when:
        succeeds("compileJava")

        then:
        bytecodeVersion() == TestJavaClassUtil.getClassVersion(lower.javaVersion)
    }

    def "compile with target compatibility set in plugin extension"() {
        given:
        def lower = getLowerJvm()
        def lowerVersion = lower.javaVersion.getMajorVersion()

        and:
        goodCode()
        buildFile << """
            java.targetCompatibility = JavaVersion.toVersion(${lowerVersion})
            java.sourceCompatibility = JavaVersion.toVersion(${lowerVersion})

            compileJava {
                ${configureBoostrapClasspath(lower)}
            }

            assert configurations.apiElements.attributes.getAttribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE) == ${lowerVersion}
            assert configurations.runtimeElements.attributes.getAttribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE) == ${lowerVersion}
            assert configurations.compileClasspath.attributes.getAttribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE) == ${lowerVersion}
            assert configurations.runtimeClasspath.attributes.getAttribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE) == ${lowerVersion}
        """

        when:
        succeeds("compileJava")

        then:
        bytecodeVersion() == TestJavaClassUtil.getClassVersion(lower.javaVersion)
    }

    @Requires(UnitTestPreconditions.Jdk12OrLater)
    def "compile fails when using newer API with release option"() {
        given:
        file("src/main/java/compile/test/FailsOnJava11.java") << """
            package compile.test;

            import java.util.stream.Stream;
            import java.util.function.Predicate;

            public class FailsOnJava11<T> {
                static {
                    System.out.println("Hello, world!".describeConstable());
                }
            }
        """

        buildFile << """
            compileJava.options.compilerArgs.addAll(['--release', '11'])
        """

        when:
        fails("compileJava")

        then:
        output.contains(logStatement())
        failure.assertHasErrorOutput("cannot find symbol")
        failure.assertHasErrorOutput("method describeConstable")
    }

    @Requires(UnitTestPreconditions.Jdk12OrLater)
    def "compile fails when using newer API with release property"() {
        given:
        file("src/main/java/compile/test/FailsOnJava11.java") << """
            package compile.test;

            import java.util.stream.Stream;
            import java.util.function.Predicate;

            public class FailsOnJava11<T> {
                static {
                    System.out.println("Hello, world!".describeConstable());
                }
            }
        """

        buildFile << """
            compileJava.options.release.set(11)
        """

        when:
        fails("compileJava")

        then:
        output.contains(logStatement())
        failure.assertHasErrorOutput("cannot find symbol")
        failure.assertHasErrorOutput("method describeConstable")
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

    def "gradle classpath does not leak onto java compile classpath"() {
        given:
        file("src/main/java/Example.java") << """
            import org.gradle.api.Project;
            import org.gradle.api.Plugin;

            public class Example implements Plugin<Project> {
                public void apply(Project project) {
                    System.out.println("Hello from Example");
                }
            }
        """

        when:
        fails(":compileJava")

        then:
        !file("build/classes/java/main/Example.class").exists()
        failure.assertHasErrorOutput("package org.gradle.api does not exist")
    }

    def "can compile with long classpath"() {
        given:
        goodCode()

        and:
        buildFile << """
            dependencies {
                file("\$projectDir/lib/").mkdirs()
                implementation files((1..999).collect {
                    createJarFile("\$projectDir/lib/library\${it}.jar")
                })
            }

            def createJarFile(String libraryPath) {
                def fos
                try {
                    fos = new FileOutputStream(file(libraryPath))
                    new java.util.jar.JarOutputStream(fos, new java.util.jar.Manifest()).withStream {
                        libraryPath
                    }
                } finally {
                    fos?.close()
                }
            }
        """

        when:
        succeeds("compileJava")

        then:
        output.contains(logStatement())
        javaClassFile("compile/test/Person.class").exists()
    }

    def "can compile with custom heap settings"() {
        given:
        goodCode()

        and:
        buildFile << """
            compileJava.options.forkOptions.with {
                memoryInitialSize = '64m'
                memoryMaximumSize = '128m'
            }
        """

        when:
        succeeds("compileJava")

        then:
        output.contains(logStatement())
        javaClassFile("compile/test/Person.class").exists()
        // couldn't find a good way to verify that heap settings take effect
    }

    def "can list source files"() {
        given:
        goodCode()

        and:
        buildFile << 'compileJava.options.listFiles = true'

        when:
        succeeds("compileJava")

        then:
        output.contains(new File("src/main/java/compile/test/Person.java").toString())
        output.contains(new File("src/main/java/compile/test/Person2.java").toString())
        output.contains(logStatement())
        javaClassFile("compile/test/Person.class").exists()
        javaClassFile("compile/test/Person2.class").exists()
    }

    def "ignores non java source files by default"() {
        given:
        goodCode()

        and:
        file('src/main/java/resource.txt').createFile()
        buildFile << 'compileJava.source += files("src/main/java/resource.txt")'

        when:
        succeeds("compileJava")

        then:
        javaClassFile("compile/test/Person.class").exists()
        javaClassFile("compile/test/Person2.class").exists()
    }

    @Issue("https://github.com/gradle/gradle/issues/5750")
    def "include narrows down source files to compile"() {
        given:
        goodCode()

        and:
        file('src/main/java/Bar.java') << 'class Bar {}'
        buildFile << 'compileJava.include "**/Person*.java"'

        when:
        succeeds("compileJava")

        then:
        javaClassFile("compile/test/Person.class").exists()
        javaClassFile("compile/test/Person2.class").exists()
        !javaClassFile("Bar.class").exists()
    }

    def "can use annotation processor"() {
        given:
        buildFile << """
            dependencies {
                compileOnly project(":processor")
                annotationProcessor project(":processor")
            }
        """
        settingsFile << "include 'processor'"
        writeAnnotationProcessorProject()

        file("src/main/java/Java.java") << """
            @com.test.SimpleAnnotation
            public class Java {
                void foo() {
                    (new Runnable() { public void run() {} }).run();
                }
            }
        """

        when:
        succeeds("compileJava")

        then:
        javaGeneratedSourceFile('Java$$Generated.java').exists()
    }

    def writeAnnotationProcessorProject() {
        file("processor").create {
            file("build.gradle") << """
                apply plugin: 'java'
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

    def goodCode() {
        buildFile << """
            ${mavenCentralRepository()}
            dependencies {
                implementation "org.codehaus.groovy:groovy:2.4.10"
            }
        """

        file("src/main/java/compile/test/Person.java") << """
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
            }
        """

        file("src/main/java/compile/test/Person2.java") << """
            package compile.test;

            class Person2 extends Person {
            }
        """
    }

    def goodCodeEncodedWith(String encoding) {
        def code = """
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
        """

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
        file("src/main/java/compile/test/Person.java") << """
            package compile.test;

            public class Person {
                String name;
                int age;

                void hello() {
                    return nothing
                }
            }
        """
    }

    def classFile(String path) {
        return new ClassFile(javaClassFile(path))
    }

    def bytecodeVersion() {
        return JavaClassUtil.getClassMajorVersion(javaClassFile('compile/test/Person.class'))
    }

    def configureBoostrapClasspath(Jvm jvm) {
        if (jvm.javaVersion.majorVersionNumber < 9) {
            def rtJar = new File(jvm.javaHome, "jre/lib/rt.jar")
            def rtJarPath = TextUtil.escapeString(rtJar.absolutePath)
            return """
                options.bootstrapClasspath = files('${rtJarPath}')
            """
        } else {
            def javaHome = TextUtil.escapeString(jvm.javaHome.absolutePath)
            return """
                options.compilerArgs += [
                    '--system', '${javaHome}'
                ]
            """
        }
    }

    Jvm getLowerJvm() {
        def lower = AvailableJavaHomes.getAvailableJdk { element -> element.getLanguageVersion() < Jvm.current().javaVersion }
        Assume.assumeTrue(lower != null)
        return lower
    }
}

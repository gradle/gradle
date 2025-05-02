/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.internal.cc.impl.inputs.undeclared

import groovy.transform.CompileStatic
import groovy.transform.MapConstructor
import org.gradle.internal.cc.impl.AbstractConfigurationCacheIntegrationTest
import org.gradle.test.fixtures.dsl.GradleDsl
import org.gradle.util.internal.ToBeImplemented

class MethodReferenceInstrumentationIntegrationTest extends AbstractConfigurationCacheIntegrationTest {
    @Override
    def setup() {
        testDirectory.file(file().path).text = file().expectedValue
    }

    def "reference #reference is instrumented in java"() {
        given:
        def classTemplate = { ownerKind, ownerName -> """
                import java.io.*;
                import java.nio.file.*;
                import java.util.*;
                import java.util.function.*;

                public $ownerKind $ownerName {
                    @FunctionalInterface
                    interface ThrowingFunction<T, R> {
                        R apply(T value) throws Exception;
                    }

                    public static String readInputWithReference(String input) throws Exception {
                        $referenceType ref = $reference;
                        if (!(ref instanceof Serializable)) {
                            throw new AssertionError("The lambda should be serializable!");
                        }
                        $consumerStatement
                    }

                    static String readIS(InputStream in) throws Exception {
                        return new BufferedReader(new InputStreamReader(in, "UTF-8")).readLine();
                    }
                }
            """
        }

        testDirectory.create {
            createDir("buildSrc") {
                file("src/main/java/MethodRefInputsCls.java") << classTemplate("class", "MethodRefInputsCls")
                file("src/main/java/MethodRefInputsInterface.java") << classTemplate("interface", "MethodRefInputsInterface")
            }
        }

        buildFile """
            tasks.register("echo") {
                def value1 = MethodRefInputsCls.readInputWithReference(${input.expr})
                def value2 = MethodRefInputsInterface.readInputWithReference(${input.expr})
                doLast {
                    println("value1 = \$value1")
                    println("value2 = \$value2")
                }
            }
        """

        when:
        configurationCacheRun("echo", "-D${systemProperty().path}=${systemProperty().expectedValue}")

        then:
        outputContains("value1 = ${input.expectedValue}")
        outputContains("value2 = ${input.expectedValue}")

        problems.assertResultHasProblems(result) {
            withInput("Build file 'build.gradle': ${input.expectedInput}")
        }

        where:
        input            | referenceType                               | reference                  | consumerStatement
        file()           | "ThrowingFunction<String, FileInputStream>" | "FileInputStream::new"     | "try (InputStream in = ref.apply(input)) { return readIS(in); }"
        systemProperty() | "Function<String, String>"                  | "System::getProperty"      | "return ref.apply(input);"
        fileEntry()      | "Function<File, Boolean>"                   | "File::isFile"             | "return String.valueOf(ref.apply(new File(input)));"
        fileEntry()      | "Supplier<Boolean>"                         | "new File(input)::isFile"  | "return String.valueOf(ref.get());"
        file()           | "ThrowingFunction<Path, BufferedReader>"    | "Files::newBufferedReader" | "try (BufferedReader in = ref.apply(Paths.get(input))) { return in.readLine(); }"
    }

    def "instruments bound method reference when receiver is a subtype"() {
        given:
        def input = fileEntry()
        def classTemplate = { ownerKind, ownerName -> """
                import java.io.*;
                import java.nio.file.*;
                import java.util.*;
                import java.util.function.*;

                public $ownerKind $ownerName {
                    public static class MyFile extends File {
                        public MyFile(String path) { super(path); }
                    }

                    public static String readInputWithReference(String input) throws Exception {
                        return String.valueOf(checkExists(new MyFile(input)));
                    }

                    static boolean checkExists(MyFile myFile) {
                        Supplier<Boolean> supplier = myFile::isFile;
                        return supplier.get();
                    }
                }
            """
        }

        testDirectory.create {
            createDir("buildSrc") {
                file("src/main/java/MethodRefInputsCls.java") << classTemplate("class", "MethodRefInputsCls")
                file("src/main/java/MethodRefInputsInterface.java") << classTemplate("interface", "MethodRefInputsInterface")
            }
        }

        buildFile """
            tasks.register("echo") {
                def value1 = MethodRefInputsCls.readInputWithReference(${input.expr})
                def value2 = MethodRefInputsInterface.readInputWithReference(${input.expr})
                doLast {
                    println("value1 = \$value1")
                    println("value2 = \$value2")
                }
            }
        """

        when:
        configurationCacheRun("echo")

        then:
        outputContains("value1 = ${input.expectedValue}")
        outputContains("value2 = ${input.expectedValue}")

        problems.assertResultHasProblems(result) {
            withInput("Build file 'build.gradle': ${input.expectedInput}")
        }
    }

    def "does not instrument bound method reference when receiver is a subtype that overrides the method"() {
        given:
        def input = fileEntry()
        def classTemplate = { ownerKind, ownerName -> """
                import java.io.*;
                import java.nio.file.*;
                import java.util.*;
                import java.util.function.*;

                public $ownerKind $ownerName {
                    public static class MyFile extends File {
                        public MyFile(String path) { super(path); }

                        @Override public boolean isFile() { return false; }  // does not call super intentionally.
                    }

                    public static String readInputWithReference(String input) throws Exception {
                        return String.valueOf(checkExists(new MyFile(input)));
                    }

                    static boolean checkExists(MyFile myFile) {
                        Supplier<Boolean> supplier = myFile::isFile;
                        return supplier.get();
                    }
                }
            """
        }

        testDirectory.create {
            createDir("buildSrc") {
                file("src/main/java/MethodRefInputsCls.java") << classTemplate("class", "MethodRefInputsCls")
                file("src/main/java/MethodRefInputsInterface.java") << classTemplate("interface", "MethodRefInputsInterface")
            }
        }

        buildFile """
            tasks.register("echo") {
                def value1 = MethodRefInputsCls.readInputWithReference(${input.expr})
                def value2 = MethodRefInputsInterface.readInputWithReference(${input.expr})
                doLast {
                    println("value1 = \$value1")
                    println("value2 = \$value2")
                }
            }
        """

        when:
        configurationCacheRun("echo")

        then:
        outputContains("value1 = false")
        outputContains("value2 = false")

        problems.assertResultHasProblems(result) {
            withNoInputs()
        }
    }

    @ToBeImplemented
    def "does not instrument bound method reference when receiver overrides the method but is declared as base type"() {
        given:
        def input = fileEntry()
        def classTemplate = { ownerKind, ownerName -> """
                import java.io.*;
                import java.nio.file.*;
                import java.util.*;
                import java.util.function.*;

                public $ownerKind $ownerName {
                    public static class MyFile extends File {
                        public MyFile(String path) { super(path); }

                        @Override public boolean isFile() { return false; }  // does not call super intentionally.
                    }

                    public static String readInputWithReference(String input) throws Exception {
                        return String.valueOf(checkExists(new MyFile(input)));
                    }

                    static boolean checkExists(File myFile) {
                        Supplier<Boolean> supplier = myFile::isFile;
                        return supplier.get();
                    }
                }
            """
        }

        testDirectory.create {
            createDir("buildSrc") {
                file("src/main/java/MethodRefInputsCls.java") << classTemplate("class", "MethodRefInputsCls")
                file("src/main/java/MethodRefInputsInterface.java") << classTemplate("interface", "MethodRefInputsInterface")
            }
        }

        buildFile """
            tasks.register("echo") {
                def value1 = MethodRefInputsCls.readInputWithReference(${input.expr})
                def value2 = MethodRefInputsInterface.readInputWithReference(${input.expr})
                doLast {
                    println("value1 = \$value1")
                    println("value2 = \$value2")
                }
            }
        """

        when:
        configurationCacheRun("echo")

        then:
        outputContains("value1 = false")
        outputContains("value2 = false")

        problems.assertResultHasProblems(result) {
            // TODO(mlopatkin): shouldn't have inputs but we rewrite File::isFile without taking the receiver type into account.
            //   Same goes for `File foo = new MyFile(".."); foo.isFile();` that is also intercepted based on the static type of the receiver.
            withInput("Build file 'build.gradle': ${input.expectedInput}")
        }
    }

    def "reference #reference as #referenceType is instrumented in kotlin script"() {
        given:
        testDirectory.file(file().path).text = file().expectedValue

        buildKotlinFile """
            import java.io.*
            import java.nio.*
            import java.nio.charset.*
            import java.nio.file.*
            import java.util.function.Function
            import java.util.function.BiFunction
            import java.util.function.Supplier

            // work around Kotlin's type inference issues when the target is a SAM type
            fun makeRef(ref: $referenceType): $referenceType = ref

            fun readInputWithReference(): String {
                val input = ${input.expr}
                val ref = makeRef($reference)
                return $consumerExpression
            }

            tasks.register("echo") {
                var value = readInputWithReference()
                doLast {
                    println("value = \$value")
                }
            }
        """

        when:
        configurationCacheRun("echo", "-D${systemProperty().path}=${systemProperty().expectedValue}")

        then:
        outputContains("value = ${input.expectedValue}")

        problems.assertResultHasProblems(result) {
            withInput("Build file 'build.gradle.kts': ${input.expectedInput}")
            ignoringUnexpectedInputs()  // Kotlin Plugin brings its inputs
        }

        where:
        input            | referenceType                                  | reference                  | consumerExpression
        file()           | "(String) -> FileInputStream"                  | "::FileInputStream"        | "ref(input).bufferedReader().use { it.readText() }"
        file()           | "Function<String, FileInputStream>"            | "::FileInputStream"        | "ref.apply(input).bufferedReader().use { it.readText() }"
        systemProperty() | "(String) -> String"                           | "System::getProperty"      | "ref(input)"
        systemProperty() | "Function<String, String>"                     | "System::getProperty"      | "ref.apply(input)"
        fileEntry()      | "(File) -> Boolean"                            | "File::isFile"             | "ref(File(input)).toString()"
        fileEntry()      | "() -> Boolean"                                | "File(input)::isFile"      | "ref().toString()"
        fileEntry()      | "Function<File, Boolean>"                      | "File::isFile"             | "ref.apply(File(input)).toString()"
        fileEntry()      | "Supplier<Boolean>"                            | "File(input)::isFile"      | "ref.get().toString()"
        file()           | "(java.nio.file.Path) -> BufferedReader"       | "Files::newBufferedReader" | "ref(Paths.get(input)).use { it.readText() }"
        file()           | "Function<java.nio.file.Path, BufferedReader>" | "Files::newBufferedReader" | "ref.apply(Paths.get(input)).use { it.readText() }"
        file()           | "() -> String"                                 | "File(input)::readText"    | "ref()"
        file()           | "Supplier<String>"                             | "File(input)::readText"    | "ref.get()"
        file()           | "(File) -> String"                             | "File::readText"           | "ref(File(input))"
        file()           | "Function<File, String>"                       | "File::readText"           | "ref.apply(File(input))"
        file()           | "(File, Charset) -> String"                    | "File::readText"           | "ref(File(input), Charset.defaultCharset())"
        file()           | "BiFunction<File, Charset, String>"            | "File::readText"           | "ref.apply(File(input), Charset.defaultCharset())"
    }

    def "reference #reference as #referenceType is instrumented in kotlin indy code"() {
        given:
        testDirectory.file(file().path).text = file().expectedValue

        testDirectory.create {
            createDir("lib-kotlin") {
                file("build.gradle.kts") << """
                    import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

                    plugins {
                        kotlin("jvm") version embeddedKotlinVersion
                    }

                    group = "org.example.support"

                    ${mavenCentralRepository(GradleDsl.KOTLIN)}

                    dependencies {
                        implementation(kotlin("stdlib"))
                    }

                    tasks.withType<KotlinCompile>().configureEach {
                        // Work around JVM validation issue: https://youtrack.jetbrains.com/issue/KT-66919
                        jvmTargetValidationMode = org.jetbrains.kotlin.gradle.dsl.jvm.JvmTargetValidationMode.WARNING
                        compilerOptions {
                            freeCompilerArgs.apply {
                                add("-Xlambdas=indy")
                                add("-Xsam-conversions=indy")
                            }
                        }
                    }
                """

                file("settings.gradle.kts") << """ rootProject.name = "lib-kotlin" """

                file("src/main/kotlin/ReadInput.kt") << """
                    import java.io.*
                    import java.nio.*
                    import java.nio.charset.*
                    import java.nio.file.*
                    import java.util.function.Function
                    import java.util.function.BiFunction
                    import java.util.function.Supplier

                    // work around Kotlin's type inference issues when the target is a SAM type
                    fun makeRef(ref: $referenceType) = ref

                    fun readInputWithReference(input: String): String {
                        val ref = makeRef($reference)
                        return $consumerExpression
                    }
                """
            }

            createDir("buildSrc") {
                file("settings.gradle.kts") << """
                    includeBuild("../lib-kotlin")
                """

                file("build.gradle.kts") << """
                    ${mavenCentralRepository(GradleDsl.KOTLIN)}

                    dependencies {
                        implementation("org.example.support:lib-kotlin")
                    }
                """
            }
        }

        buildKotlinFile """
            tasks.register("echo") {
                var value = readInputWithReference(${input.expr})
                doLast {
                    println("value = \$value")
                }
            }
        """

        when:
        configurationCacheRun("echo", "-D${systemProperty().path}=${systemProperty().expectedValue}")

        then:
        outputContains("value = ${input.expectedValue}")

        problems.assertResultHasProblems(result) {
            withInput("Build file 'build.gradle.kts': ${input.expectedInput}")
            ignoringUnexpectedInputs() // Kotlin Plugin brings its inputs
        }

        where:
        input            | referenceType                                  | reference                  | consumerExpression
        file()           | "(String) -> FileInputStream"                  | "::FileInputStream"        | "ref(input).bufferedReader().use { it.readText() }"
        file()           | "Function<String, FileInputStream>"            | "::FileInputStream"        | "ref.apply(input).bufferedReader().use { it.readText() }"
        systemProperty() | "(String) -> String"                           | "System::getProperty"      | "ref(input)"
        systemProperty() | "Function<String, String>"                     | "System::getProperty"      | "ref.apply(input)"
        fileEntry()      | "(File) -> Boolean"                            | "File::isFile"             | "ref(File(input)).toString()"
        fileEntry()      | "() -> Boolean"                                | "File(input)::isFile"      | "ref().toString()"
        fileEntry()      | "Function<File, Boolean>"                      | "File::isFile"             | "ref.apply(File(input)).toString()"
        fileEntry()      | "Supplier<Boolean>"                            | "File(input)::isFile"      | "ref.get().toString()"
        file()           | "(java.nio.file.Path) -> BufferedReader"       | "Files::newBufferedReader" | "ref(Paths.get(input)).use { it.readText() }"
        file()           | "Function<java.nio.file.Path, BufferedReader>" | "Files::newBufferedReader" | "ref.apply(Paths.get(input)).use { it.readText() }"
        file()           | "() -> String"                                 | "File(input)::readText"    | "ref()"
        file()           | "Supplier<String>"                             | "File(input)::readText"    | "ref.get()"
        file()           | "(File) -> String"                             | "File::readText"           | "ref(File(input))"
        file()           | "Function<File, String>"                       | "File::readText"           | "ref.apply(File(input))"
        file()           | "(File, Charset) -> String"                    | "File::readText"           | "ref(File(input), Charset.defaultCharset())"
        file()           | "BiFunction<File, Charset, String>"            | "File::readText"           | "ref.apply(File(input), Charset.defaultCharset())"
    }

    def "reference #reference as #referenceType is instrumented in static groovy"() {
        given:
        buildFile """
            import java.nio.file.*
            import java.util.function.*

            @${CompileStatic.name}
            public String readInputWithReference() {
                def input = ${input.expr}
                $referenceType ref = $reference;
                if (!(ref instanceof Serializable)) {
                    throw new AssertionError("The lambda should be serializable!");
                }
                $consumerStatement
            }

            tasks.register("echo") {
                def value = readInputWithReference()
                doLast {
                    println("value = \$value")
                }
            }
        """

        testDirectory.file(file().path).text = file().expectedValue

        when:
        configurationCacheRun("echo", "-D${systemProperty().path}=${systemProperty().expectedValue}")

        then:
        outputContains("value = ${input.expectedValue}")

        problems.assertResultHasProblems(result) {
            withInput("Build file 'build.gradle': ${input.expectedInput}")
        }

        where:
        input            | referenceType                       | reference                  | consumerStatement
        file()           | "Function<String, FileInputStream>" | "FileInputStream::new"     | "try (InputStream in = ref.apply(input)) { return in.text } "
        file()           | "Closure"                           | "FileInputStream::new"     | "try (InputStream in = ref(input)) { return in.text } "
        file()           | "Closure"                           | "FileInputStream.&new"     | "try (InputStream in = ref(input)) { return in.text } "
        systemProperty() | "Function<String, String>"          | "System::getProperty"      | "return ref.apply(input)"
        systemProperty() | "Closure"                           | "System::getProperty"      | "return ref(input)"
        systemProperty() | "Closure"                           | "System.&getProperty"      | "return ref(input)"
        fileEntry()      | "Function<File, Boolean>"           | "File::isFile"             | "return String.valueOf(ref.apply(new File(input)))"
        fileEntry()      | "Closure"                           | "File::isFile"             | "return String.valueOf(ref(new File(input)))"
        fileEntry()      | "Closure"                           | "File.&isFile"             | "return String.valueOf(ref(new File(input)))"
        fileEntry()      | "Supplier<Boolean>"                 | "new File(input)::isFile"  | "return String.valueOf(ref.get())"
        fileEntry()      | "Closure"                           | "new File(input)::isFile"  | "return String.valueOf(ref())"
        fileEntry()      | "Closure"                           | "new File(input).&isFile"  | "return String.valueOf(ref())"
        file()           | "Function<Path, BufferedReader>"    | "Files::newBufferedReader" | "try (BufferedReader in = ref.apply(Paths.get(input))) { return in.readLine() }"
        file()           | "Closure"                           | "Files::newBufferedReader" | "try (BufferedReader in = ref(Paths.get(input))) { return in.readLine() }"
        file()           | "Closure"                           | "Files.&newBufferedReader" | "try (BufferedReader in = ref(Paths.get(input))) { return in.readLine() }"
    }

    def "reference #reference is instrumented in dynamic groovy"() {
        given:
        testDirectory.file(file().path).text = file().expectedValue

        buildFile """
            import java.nio.file.*
            import java.util.function.*

            public String readInputWithReference() {
                def input = ${input.expr}
                $referenceType ref = $reference;
                if (!(ref instanceof Serializable)) {
                    throw new AssertionError("The lambda should be serializable!");
                }
                $consumerStatement
            }

            tasks.register("echo") {
                def value = readInputWithReference()
                doLast {
                    println("value = \$value")
                }
            }
        """

        when:
        configurationCacheRun("echo", "-D${systemProperty().path}=${systemProperty().expectedValue}")

        then:
        outputContains("value = ${input.expectedValue}")

        problems.assertResultHasProblems(result) {
            withInput("Build file 'build.gradle': ${input.expectedInput}")
        }

        where:
        input            | referenceType                       | reference                  | consumerStatement
        file()           | "Function<String, FileInputStream>" | "FileInputStream::new"     | "try (InputStream in = ref.apply(input)) { return in.text } "
        file()           | "Closure"                           | "FileInputStream::new"     | "try (InputStream in = ref(input)) { return in.text } "
        file()           | "Closure"                           | "FileInputStream.&new"     | "try (InputStream in = ref(input)) { return in.text } "
        systemProperty() | "Function<String, String>"          | "System::getProperty"      | "return ref.apply(input)"
        systemProperty() | "Closure"                           | "System::getProperty"      | "return ref(input)"
        systemProperty() | "Closure"                           | "System.&getProperty"      | "return ref(input)"
        fileEntry()      | "Function<File, Boolean>"           | "File::isFile"             | "return String.valueOf(ref.apply(new File(input)))"
        fileEntry()      | "Closure"                           | "File::isFile"             | "return String.valueOf(ref(new File(input)))"
        fileEntry()      | "Closure"                           | "File.&isFile"             | "return String.valueOf(ref(new File(input)))"
        fileEntry()      | "Supplier<Boolean>"                 | "new File(input)::isFile"  | "return String.valueOf(ref.get())"
        fileEntry()      | "Closure"                           | "new File(input)::isFile"  | "return String.valueOf(ref())"
        fileEntry()      | "Closure"                           | "new File(input).&isFile"  | "return String.valueOf(ref())"
        file()           | "Function<Path, BufferedReader>"    | "Files::newBufferedReader" | "try (BufferedReader in = ref.apply(Paths.get(input))) { return in.readLine() }"
        file()           | "Closure"                           | "Files::newBufferedReader" | "try (BufferedReader in = ref(Paths.get(input))) { return in.readLine() }"
        file()           | "Closure"                           | "Files.&newBufferedReader" | "try (BufferedReader in = ref(Paths.get(input))) { return in.readLine() }"
    }

    def "reference #reference is instrumented in dynamic untyped groovy"() {
        given:
        testDirectory.file(file().path).text = file().expectedValue

        buildFile """
            import java.nio.file.*
            import java.util.function.*

            public String readInputWithReference() {
                def input = ${input.expr}
                def ref = $reference;
                if (!(ref instanceof Serializable)) {
                    throw new AssertionError("The lambda should be serializable!");
                }
                $consumerStatement
            }

            tasks.register("echo") {
                def value = readInputWithReference()
                doLast {
                    println("value = \$value")
                }
            }
        """

        when:
        configurationCacheRun("echo", "-D${systemProperty().path}=${systemProperty().expectedValue}")

        then:
        outputContains("value = ${input.expectedValue}")

        problems.assertResultHasProblems(result) {
            withInput("Build file 'build.gradle': ${input.expectedInput}")
        }

        where:
        input            | reference                  | consumerStatement
        file()           | "FileInputStream::new"     | "try (InputStream in = ref(input)) { return in.text } "
        file()           | "FileInputStream.&new"     | "try (InputStream in = ref(input)) { return in.text } "
        systemProperty() | "System::getProperty"      | "return ref(input)"
        systemProperty() | "System.&getProperty"      | "return ref(input)"
        fileEntry()      | "File::isFile"             | "return String.valueOf(ref(new File(input)))"
        fileEntry()      | "File.&isFile"             | "return String.valueOf(ref(new File(input)))"
        fileEntry()      | "new File(input)::isFile"  | "return String.valueOf(ref())"
        fileEntry()      | "new File(input).&isFile"  | "return String.valueOf(ref())"
        file()           | "Files::newBufferedReader" | "try (BufferedReader in = ref(Paths.get(input))) { return in.readLine() }"
        file()           | "Files.&newBufferedReader" | "try (BufferedReader in = ref(Paths.get(input))) { return in.readLine() }"
    }

    @MapConstructor
    @SuppressWarnings('GrFinalVariableAccess')
    private static class Input {
        final String path
        final String expr
        final String expectedValue
        final String expectedInput
    }

    private static Input file(String path = "input.txt") {
        new Input(path: path, expr: "file(\"$path\").absolutePath", expectedValue: "file value", expectedInput: "file '$path'")
    }

    private static Input systemProperty(String path = "my.system.property") {
        new Input(path: path, expr: "\"$path\"", expectedValue: "property value", expectedInput: "system property '$path'")
    }

    private static Input fileEntry(String path = "input.txt") {
        new Input(path: path, expr: "file(\"$path\").absolutePath", expectedValue: "true", expectedInput: "file system entry '$path'")
    }
}

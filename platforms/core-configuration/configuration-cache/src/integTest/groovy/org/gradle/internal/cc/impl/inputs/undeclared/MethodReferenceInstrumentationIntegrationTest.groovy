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


import groovy.transform.MapConstructor
import org.gradle.internal.cc.impl.AbstractConfigurationCacheIntegrationTest

class MethodReferenceInstrumentationIntegrationTest extends AbstractConfigurationCacheIntegrationTest {
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

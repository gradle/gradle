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

package org.gradle.internal.instrumentation.extensions.property

import com.google.testing.compile.Compilation
import com.google.testing.compile.JavaFileObjects
import org.gradle.internal.instrumentation.processor.ConfigurationCacheInstrumentationProcessor
import spock.lang.Specification

import javax.tools.JavaFileObject

import static com.google.testing.compile.CompilationSubject.assertThat
import static com.google.testing.compile.Compiler.javac

class PropertyUpgradeClassGeneratorTest extends Specification {

    def "should generate adapter for upgraded property with originalType"() {
        given:
        def fQName = "org.gradle.test.Task"
        def givenSource = JavaFileObjects.forSourceString(fQName, """
            package org.gradle.test;

            import org.gradle.api.provider.Property;
            import org.gradle.internal.instrumentation.api.annotations.VisitForInstrumentation;
            import org.gradle.internal.instrumentation.api.annotations.UpgradedProperty;

            @VisitForInstrumentation(value = {Task.class})
            public abstract class Task {
                @UpgradedProperty(originalType = int.class)
                public abstract Property<Integer> getMaxErrors();
            }
        """)

        when:
        Compilation compilation = compile(givenSource)

        then:
        def generatedClassName =  "org.gradle.internal.instrumentation.Task_Adapter"
        def expectedOutput = JavaFileObjects.forSourceLines(generatedClassName, """
            package org.gradle.internal.instrumentation;
            import org.gradle.test.Task;

            public class Task_Adapter {
                public static int access_get_maxErrors(Task self) {
                    return self.getMaxErrors().get();
                }

                public static void access_set_maxErrors(Task self, int arg0) {
                    self.getMaxErrors().set(arg0);
                }
            }
        """)
        assertThat(compilation).succeededWithoutWarnings()
        assertThat(compilation)
            .generatedSourceFile(generatedClassName)
            .hasSourceEquivalentTo(expectedOutput)
    }

    def "should generate adapter for upgraded property with type #upgradedType"() {
        given:
        def fQName = "org.gradle.test.Task"
        def givenSource = JavaFileObjects.forSourceString(fQName, """
            package org.gradle.test;

            import org.gradle.api.provider.*;
            import org.gradle.api.file.*;
            import org.gradle.internal.instrumentation.api.annotations.VisitForInstrumentation;
            import org.gradle.internal.instrumentation.api.annotations.UpgradedProperty;

            @VisitForInstrumentation(value = {Task.class})
            public abstract class Task {
                @UpgradedProperty
                public abstract $upgradedType getProperty();
            }
        """)

        when:
        Compilation compilation = compile(givenSource)

        then:
        def generatedClassName = "org.gradle.internal.instrumentation.Task_Adapter"
        def expectedOutput = JavaFileObjects.forSourceLines(generatedClassName, """
            package org.gradle.internal.instrumentation;
            import $fullImport;
            import org.gradle.test.Task;

            public class Task_Adapter {
                public static $originalType access_get_property(Task self) {
                    return self.getProperty().get();
                }

                public static void access_set_property(Task self, $originalType arg0) {
                    self.getProperty().set(arg0);
                }
            }
        """)
        assertThat(compilation).succeededWithoutWarnings()
        assertThat(compilation)
            .generatedSourceFile(generatedClassName)
            .hasSourceEquivalentTo(expectedOutput)

        where:
        upgradedType                  | originalType | fullImport
        "Property<Integer>"           | "Integer"    | "java.lang.Integer"
        "Property<String>"            | "String"     | "java.lang.String"
        "ListProperty<String>"        | "List"       | "java.util.List"
        "MapProperty<String, String>" | "Map"        | "java.util.Map"
        // TODO: Not yet supported
        // "RegularFileProperty"         | "File"       | "java.io.File"
        // "DirectoryProperty"           | "File"       | "java.io.File"
        // "ConfigurableFileCollection"           | "FileCollection"       | "java.io.File"
    }

    private static Compilation compile(JavaFileObject fileObject) {
        return javac()
            .withOptions("--release=8")
            .withProcessors(new ConfigurationCacheInstrumentationProcessor())
            .compile(fileObject)
    }
}

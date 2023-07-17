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
import org.gradle.internal.instrumentation.InstrumentationCodeGenTest

import static com.google.testing.compile.CompilationSubject.assertThat
import static org.gradle.internal.instrumentation.api.declarations.InterceptorDeclaration.GROOVY_INTERCEPTORS_GENERATED_CLASS_NAME_FOR_PROPERTY_UPGRADES

class PropertyUpgradeCodeGenTest extends InstrumentationCodeGenTest {

    private static final String GENERATED_CLASSES_PACKAGE_NAME = GROOVY_INTERCEPTORS_GENERATED_CLASS_NAME_FOR_PROPERTY_UPGRADES
        .split("\\.").dropRight(1).join(".")

    def "should auto generate adapter for upgraded property with originalType"() {
        given:
        def givenSource = source """
            package org.gradle.test;

            import org.gradle.api.provider.Property;
            import org.gradle.internal.instrumentation.api.annotations.VisitForInstrumentation;
            import org.gradle.internal.instrumentation.api.annotations.UpgradedProperty;

            @VisitForInstrumentation(value = {Task.class})
            public abstract class Task {
                @UpgradedProperty(originalType = int.class)
                public abstract Property<Integer> getMaxErrors();
            }
        """

        when:
        Compilation compilation = compile(givenSource)

        then:
        def generatedClass = source """
            package $GENERATED_CLASSES_PACKAGE_NAME;
            import org.gradle.test.Task;

            public class Task_Adapter {
                public static int access_get_maxErrors(Task self) {
                    return self.getMaxErrors().get();
                }

                public static void access_set_maxErrors(Task self, int arg0) {
                    self.getMaxErrors().set(arg0);
                }
            }
        """
        assertThat(compilation).succeededWithoutWarnings()
        assertThat(compilation)
            .generatedSourceFile(fqName(generatedClass))
            .hasSourceEquivalentTo(generatedClass)
    }

    def "should auto generate adapter for upgraded property with boolean"() {
        given:
        def givenSource = source """
            package org.gradle.test;

            import org.gradle.api.provider.Property;
            import org.gradle.internal.instrumentation.api.annotations.VisitForInstrumentation;
            import org.gradle.internal.instrumentation.api.annotations.UpgradedProperty;

            @VisitForInstrumentation(value = {Task.class})
            public abstract class Task {
                @UpgradedProperty(originalType = boolean.class, fluentSetter = true)
                public abstract Property<Boolean> getIncremental();
            }
        """

        when:
        Compilation compilation = compile(givenSource)

        then:
        def generatedClass = source """
            package $GENERATED_CLASSES_PACKAGE_NAME;
            public class InterceptorDeclaration_PropertyUpgradesJvmBytecode extends MethodVisitorScope implements JvmBytecodeCallInterceptor {
                @Override
                public boolean visitMethodInsn(String className, int opcode, String owner, String name,
                                               String descriptor, boolean isInterface, Supplier<MethodNode> readMethodNode) {
                    if (metadata.isInstanceOf(owner, "org/gradle/test/Task")) {
                         if (name.equals("isIncremental") && descriptor.equals("()Z") && (opcode == Opcodes.INVOKEVIRTUAL || opcode == Opcodes.INVOKEINTERFACE)) {
                             _INVOKESTATIC(TASK__ADAPTER_TYPE, "access_get_incremental", "(Lorg/gradle/test/Task;)Z");
                             return true;
                         }
                     }
                     if (metadata.isInstanceOf(owner, "org/gradle/test/Task")) {
                      if (name.equals("setIncremental") && descriptor.equals("(Z)Lorg/gradle/test/Task;") && (opcode == Opcodes.INVOKEVIRTUAL || opcode == Opcodes.INVOKEINTERFACE)) {
                             _INVOKESTATIC(TASK__ADAPTER_TYPE, "access_set_incremental", "(Lorg/gradle/test/Task;Z)Lorg/gradle/test/Task;");
                             return true;
                         }
                     }
                    return false;
                }
            }
        """
        def adapterClass = source """
            package $GENERATED_CLASSES_PACKAGE_NAME;
            import org.gradle.test.Task;

            public class Task_Adapter {
                public static boolean access_get_incremental(Task self) {
                    return self.getIncremental().get();
                }

                public static Task access_set_incremental(Task self, boolean arg0) {
                    self.getIncremental().set(arg0);
                    return self;
                }
            }
        """
        assertThat(compilation).succeededWithoutWarnings()
        assertThat(compilation)
            .generatedSourceFile(fqName(generatedClass))
            .containsElementsIn(generatedClass)
        assertThat(compilation)
            .generatedSourceFile(fqName(adapterClass))
            .hasSourceEquivalentTo(adapterClass)
    }

    def "should auto generate adapter for upgraded property with type #upgradedType"() {
        given:
        def givenSource = source"""
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
        """

        when:
        Compilation compilation = compile(givenSource)

        then:
        def generatedClass = source """
            package $GENERATED_CLASSES_PACKAGE_NAME;
            import $fullImport;
            import org.gradle.test.Task;

            public class Task_Adapter {
                public static $originalType access_get_property(Task self) {
                    return self.getProperty()$getCall;
                }

                public static void access_set_property(Task self, $originalType arg0) {
                    self.getProperty()$setCall;
                }
            }
        """
        assertThat(compilation).succeededWithoutWarnings()
        assertThat(compilation)
            .generatedSourceFile(fqName(generatedClass))
            .hasSourceEquivalentTo(generatedClass)

        where:
        upgradedType                  | originalType     | getCall              | setCall            | fullImport
        "Property<Integer>"           | "Integer"        | ".get()"             | ".set(arg0)"       | "java.lang.Integer"
        "Property<String>"            | "String"         | ".get()"             | ".set(arg0)"       | "java.lang.String"
        "ListProperty<String>"        | "List"           | ".get()"             | ".set(arg0)"       | "java.util.List"
        "MapProperty<String, String>" | "Map"            | ".get()"             | ".set(arg0)"       | "java.util.Map"
        "RegularFileProperty"         | "File"           | ".getAsFile().get()" | ".fileValue(arg0)" | "java.io.File"
        "DirectoryProperty"           | "File"           | ".getAsFile().get()" | ".fileValue(arg0)" | "java.io.File"
        "ConfigurableFileCollection"  | "FileCollection" | ""                   | ".setFrom(arg0)"   | "org.gradle.api.file.FileCollection"
    }

    def "should correctly generate interceptor when property name contains get"() {
        given:
        def givenSource = source"""
            package org.gradle.test;

            import org.gradle.api.provider.*;
            import org.gradle.api.file.*;
            import org.gradle.internal.instrumentation.api.annotations.*;

            @VisitForInstrumentation(value = {Task.class})
            public abstract class Task {
                @UpgradedProperty
                public abstract Property<String> getTargetCompatibility();
            }
        """

        when:
        Compilation compilation = compile(givenSource)

        then:
        def generatedClass = source """
            package $GENERATED_CLASSES_PACKAGE_NAME;
            public class InterceptorDeclaration_PropertyUpgradesJvmBytecode extends MethodVisitorScope implements JvmBytecodeCallInterceptor {
                @Override
                public boolean visitMethodInsn(String className, int opcode, String owner, String name,
                                               String descriptor, boolean isInterface, Supplier<MethodNode> readMethodNode) {
                    if (metadata.isInstanceOf(owner, "org/gradle/test/Task")) {
                         if (name.equals("getTargetCompatibility") && descriptor.equals("()Ljava/lang/String;") && (opcode == Opcodes.INVOKEVIRTUAL || opcode == Opcodes.INVOKEINTERFACE)) {
                             _INVOKESTATIC(TASK__ADAPTER_TYPE, "access_get_targetCompatibility", "(Lorg/gradle/test/Task;)Ljava/lang/String;");
                             return true;
                         }
                    }
                    if (metadata.isInstanceOf(owner, "org/gradle/test/Task")) {
                       if (name.equals("setTargetCompatibility") && descriptor.equals("(Ljava/lang/String;)V") && (opcode == Opcodes.INVOKEVIRTUAL || opcode == Opcodes.INVOKEINTERFACE)) {
                           _INVOKESTATIC(TASK__ADAPTER_TYPE, "access_set_targetCompatibility", "(Lorg/gradle/test/Task;Ljava/lang/String;)V");
                           return true;
                       }
                    }
                    return false;
                }
            }
        """
        assertThat(compilation).succeededWithoutWarnings()
        assertThat(compilation)
            .generatedSourceFile(fqName(generatedClass))
            .containsElementsIn(generatedClass)
    }
}

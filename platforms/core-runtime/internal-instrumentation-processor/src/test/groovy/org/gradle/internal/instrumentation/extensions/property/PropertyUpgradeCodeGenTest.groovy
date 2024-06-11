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
import org.gradle.api.file.FileCollection
import org.gradle.api.internal.provider.views.ListPropertyListView
import org.gradle.api.internal.provider.views.MapPropertyMapView
import org.gradle.api.internal.provider.views.SetPropertySetView
import org.gradle.internal.instrumentation.InstrumentationCodeGenTest

import static com.google.testing.compile.CompilationSubject.assertThat
import static org.gradle.internal.instrumentation.api.declarations.InterceptorDeclaration.GROOVY_INTERCEPTORS_GENERATED_CLASS_NAME_FOR_PROPERTY_UPGRADES

class PropertyUpgradeCodeGenTest extends InstrumentationCodeGenTest {

    private static final String GENERATED_CLASSES_PACKAGE_NAME = GROOVY_INTERCEPTORS_GENERATED_CLASS_NAME_FOR_PROPERTY_UPGRADES
        .split("\\.").dropRight(1).join(".")
    private static final Set<String> PRIMITIVE_TYPES = ["byte", "short", "int", "long", "float", "double", "char", "boolean"] as Set<String>

    def "should auto generate adapter for upgraded property with originalType"() {
        given:
        def givenSource = source """
            package org.gradle.test;

            import org.gradle.api.provider.Property;
            import org.gradle.internal.instrumentation.api.annotations.VisitForInstrumentation;
            import org.gradle.internal.instrumentation.api.annotations.ReplacesEagerProperty;
            import org.gradle.internal.instrumentation.api.annotations.ReplacedDeprecation;

            @VisitForInstrumentation(value = {Task.class})
            public abstract class Task {
                @ReplacesEagerProperty(originalType = int.class)
                public abstract Property<Integer> getMaxErrors();
            }
        """

        when:
        Compilation compilation = compile(givenSource)

        then:
        def generatedClass = source """
            package $GENERATED_CLASSES_PACKAGE_NAME;
            import org.gradle.test.Task;

            @Generated
            public class Task_Adapter {
                public static int access_get_getMaxErrors(Task self) {
                    ${getDefaultDeprecation("Task", "maxErrors")}
                    return self.getMaxErrors().getOrElse(0);
                }

                public static void access_set_setMaxErrors(Task self, int arg0) {
                    ${getDefaultDeprecation("Task", "maxErrors")}
                    self.getMaxErrors().set(arg0);
                }
            }
        """
        assertThat(compilation).succeededWithoutWarnings()
        assertThat(compilation)
            .generatedSourceFile(fqName(generatedClass))
            .containsElementsIn(generatedClass)
    }

    def "should auto generate adapter for upgraded property with boolean"() {
        given:
        def givenSource = source """
            package org.gradle.test;

            import org.gradle.api.provider.Property;
            import org.gradle.internal.instrumentation.api.annotations.VisitForInstrumentation;
            import org.gradle.internal.instrumentation.api.annotations.ReplacesEagerProperty;

            @VisitForInstrumentation(value = {Task.class})
            public abstract class Task {
                @ReplacesEagerProperty(originalType = boolean.class, fluentSetter = true)
                public abstract Property<Boolean> getIncremental();
            }
        """

        when:
        Compilation compilation = compile(givenSource)

        then:
        def generatedClass = source """
            package $GENERATED_CLASSES_PACKAGE_NAME;

            @Generated
            public class InterceptorDeclaration_PropertyUpgradesJvmBytecode_TestProject implements JvmBytecodeCallInterceptor, FilterableBytecodeInterceptor.BytecodeUpgradeInterceptor {
                @Override
                public boolean visitMethodInsn(String className, int opcode, String owner, String name,
                                               String descriptor, boolean isInterface, Supplier<MethodNode> readMethodNode) {
                    if (metadata.isInstanceOf(owner, "org/gradle/test/Task")) {
                         if (name.equals("isIncremental") && descriptor.equals("()Z") && (opcode == Opcodes.INVOKEVIRTUAL || opcode == Opcodes.INVOKEINTERFACE)) {
                             mv._INVOKESTATIC(TASK__ADAPTER_TYPE, "access_get_isIncremental", "(Lorg/gradle/test/Task;)Z");
                             return true;
                         }
                        if (name.equals("setIncremental") && descriptor.equals("(Z)Lorg/gradle/test/Task;") && (opcode == Opcodes.INVOKEVIRTUAL || opcode == Opcodes.INVOKEINTERFACE)) {
                             mv._INVOKESTATIC(TASK__ADAPTER_TYPE, "access_set_setIncremental", "(Lorg/gradle/test/Task;Z)Lorg/gradle/test/Task;");
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

            @Generated
            public class Task_Adapter {
                public static boolean access_get_isIncremental(Task self) {
                    ${getDefaultDeprecation("Task", "incremental")}
                    return self.getIncremental().getOrElse(false);
                }

                public static Task access_set_setIncremental(Task self, boolean arg0) {
                    ${getDefaultDeprecation("Task", "incremental")}
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
            .containsElementsIn(adapterClass)
    }

    def "should auto generate adapter for upgraded property with type #upgradedType"() {
        given:
        def givenSource = source"""
            package org.gradle.test;

            import org.gradle.api.provider.*;
            import org.gradle.api.file.*;
            import org.gradle.internal.instrumentation.api.annotations.VisitForInstrumentation;
            import org.gradle.internal.instrumentation.api.annotations.ReplacesEagerProperty;

            @VisitForInstrumentation(value = {Task.class})
            public abstract class Task {
                @ReplacesEagerProperty${PRIMITIVE_TYPES.contains(originalType) ? "(originalType = ${originalType}.class)" : ""}
                public abstract $upgradedType getProperty();
            }
        """

        when:
        Compilation compilation = compile(givenSource)

        then:
        boolean hasSuppressWarnings = originalType in ["List", "Map", "Set"]
        String getterPrefix = originalType == "boolean" ? "is" : "get"
        def generatedClass = source """
            package $GENERATED_CLASSES_PACKAGE_NAME;
            ${imports.collect { "import $it.name;" }.join("\n")}
            import org.gradle.internal.deprecation.DeprecationLogger;
            import org.gradle.test.Task;

            @Generated
            public class Task_Adapter {
                ${hasSuppressWarnings ? '@SuppressWarnings({"unchecked", "rawtypes"})' : ''}
                public static $originalType access_get_${getterPrefix}Property(Task self) {
                    ${getDefaultDeprecation("Task", "property")}
                    return $getCall;
                }

                ${hasSuppressWarnings ? '@SuppressWarnings({"unchecked", "rawtypes"})' : ''}
                public static void access_set_setProperty(Task self, $originalType arg0) {
                    ${getDefaultDeprecation("Task", "property")}
                    self.getProperty()$setCall;
                }
            }
        """
        assertThat(compilation).succeededWithoutWarnings()
        assertThat(compilation)
            .generatedSourceFile(fqName(generatedClass))
            .containsElementsIn(generatedClass)

        where:
        upgradedType                  | originalType     | getCall                                          | setCall            | imports
        "Property<Integer>"           | "int"            | "self.getProperty().getOrElse(0)"                | ".set(arg0)"       | []
        "Property<Boolean>"           | "boolean"        | "self.getProperty().getOrElse(false)"            | ".set(arg0)"       | []
        "Property<Long>"              | "long"           | "self.getProperty().getOrElse(0L)"               | ".set(arg0)"       | []
        "Property<Integer>"           | "Integer"        | "self.getProperty().getOrElse(null)"             | ".set(arg0)"       | [Integer]
        "Property<String>"            | "String"         | "self.getProperty().getOrElse(null)"             | ".set(arg0)"       | [String]
        "ListProperty<String>"        | "List"           | "new ListPropertyListView<>(self.getProperty())" | ".set(arg0)"       | [SuppressWarnings, List, ListPropertyListView]
        "MapProperty<String, String>" | "Map"            | "new MapPropertyMapView<>(self.getProperty())"   | ".set(arg0)"       | [SuppressWarnings, Map, MapPropertyMapView]
        "SetProperty<String>"         | "Set"            | "new SetPropertySetView<>(self.getProperty())"   | ".set(arg0)"       | [SuppressWarnings, Set, SetPropertySetView]
        "RegularFileProperty"         | "File"           | "self.getProperty().getAsFile().getOrNull()"     | ".fileValue(arg0)" | [File]
        "DirectoryProperty"           | "File"           | "self.getProperty().getAsFile().getOrNull()"     | ".fileValue(arg0)" | [File]
        "ConfigurableFileCollection"  | "FileCollection" | "self.getProperty()"                             | ".setFrom(arg0)"   | [FileCollection]
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
                @ReplacesEagerProperty
                public abstract Property<String> getTargetCompatibility();
            }
        """

        when:
        Compilation compilation = compile(givenSource)

        then:
        def generatedClass = source """
            package $GENERATED_CLASSES_PACKAGE_NAME;

            @Generated
            public class InterceptorDeclaration_PropertyUpgradesJvmBytecode_TestProject implements JvmBytecodeCallInterceptor, FilterableBytecodeInterceptor.BytecodeUpgradeInterceptor {
                @Override
                public boolean visitMethodInsn(String className, int opcode, String owner, String name,
                                               String descriptor, boolean isInterface, Supplier<MethodNode> readMethodNode) {
                    if (metadata.isInstanceOf(owner, "org/gradle/test/Task")) {
                         if (name.equals("getTargetCompatibility") && descriptor.equals("()Ljava/lang/String;") && (opcode == Opcodes.INVOKEVIRTUAL || opcode == Opcodes.INVOKEINTERFACE)) {
                             mv._INVOKESTATIC(TASK__ADAPTER_TYPE, "access_get_getTargetCompatibility", "(Lorg/gradle/test/Task;)Ljava/lang/String;");
                             return true;
                         }
                         if (name.equals("setTargetCompatibility") && descriptor.equals("(Ljava/lang/String;)V") && (opcode == Opcodes.INVOKEVIRTUAL || opcode == Opcodes.INVOKEINTERFACE)) {
                           mv._INVOKESTATIC(TASK__ADAPTER_TYPE, "access_set_setTargetCompatibility", "(Lorg/gradle/test/Task;Ljava/lang/String;)V");
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

    def "should visit classes annotated with just @ReplacesEagerProperty on properties"() {
        given:
        def givenSource = source """
            package org.gradle.test;

            import org.gradle.api.provider.Property;
            import org.gradle.internal.instrumentation.api.annotations.ReplacesEagerProperty;

            public abstract class Task {
                @ReplacesEagerProperty
                public abstract Property<Integer> getMaxErrors();
            }
        """

        when:
        Compilation compilation = compile(givenSource)

        then:
        assertThat(compilation).succeededWithoutWarnings()
        assertThat(compilation).generatedSourceFile("${GENERATED_CLASSES_PACKAGE_NAME}.Task_Adapter")
    }

    def "should fail if @ReplacesEagerProperty is not a simple getter"() {
        given:
        def givenSource = source """
            package org.gradle.test;

            import org.gradle.api.provider.Property;
            import org.gradle.internal.instrumentation.api.annotations.ReplacesEagerProperty;

            public abstract class Task {
                @ReplacesEagerProperty
                public abstract Property<Integer> getMaxErrors(String arg0);
            }
        """

        when:
        Compilation compilation = compile(givenSource)

        then:
        assertThat(compilation).failed()
        assertThat(compilation).hadErrorContaining("Method 'org.gradle.test.Task.getMaxErrors(java.lang.String)' annotated with @ReplacesEagerProperty should be a simple getter: name should start with 'get' and method should not have any parameters.")
    }

    def "should generate interceptor for upgraded property with original accessors with different names"() {
        given:
        def givenSource = source"""
            package org.gradle.test;

            import org.gradle.api.provider.*;
            import org.gradle.api.file.*;
            import org.gradle.internal.instrumentation.api.annotations.*;
            import org.gradle.internal.instrumentation.api.annotations.ReplacedAccessor.AccessorType;
            import java.io.File;

            public abstract class Task {
                @ReplacesEagerProperty(replacedAccessors = {
                    @ReplacedAccessor(value = AccessorType.GETTER, name = "getDestinationDir"),
                    @ReplacedAccessor(value = AccessorType.SETTER, name = "setDestinationDir"),
                    @ReplacedAccessor(value = AccessorType.SETTER, name = "destinationDir", originalType = File.class)
                })
                public abstract DirectoryProperty getDestinationDirectory();
            }
        """

        when:
        Compilation compilation = compile(givenSource)

        then:
        def generatedClass = source """
            package $GENERATED_CLASSES_PACKAGE_NAME;

            @Generated
            public class InterceptorDeclaration_PropertyUpgradesJvmBytecode_TestProject implements JvmBytecodeCallInterceptor, FilterableBytecodeInterceptor.BytecodeUpgradeInterceptor {
                @Override
                public boolean visitMethodInsn(String className, int opcode, String owner, String name,
                                               String descriptor, boolean isInterface, Supplier<MethodNode> readMethodNode) {
                    if (metadata.isInstanceOf(owner, "org/gradle/test/Task")) {
                        if (name.equals("getDestinationDir") && descriptor.equals("()Ljava/io/File;") && (opcode == Opcodes.INVOKEVIRTUAL || opcode == Opcodes.INVOKEINTERFACE)) {
                            mv._INVOKESTATIC(TASK__ADAPTER_TYPE, "access_get_getDestinationDir", "(Lorg/gradle/test/Task;)Ljava/io/File;");
                            return true;
                        }
                        if (name.equals("setDestinationDir") && descriptor.equals("(Ljava/io/File;)V") && (opcode == Opcodes.INVOKEVIRTUAL || opcode == Opcodes.INVOKEINTERFACE)) {
                            mv._INVOKESTATIC(TASK__ADAPTER_TYPE, "access_set_setDestinationDir", "(Lorg/gradle/test/Task;Ljava/io/File;)V");
                            return true;
                        }
                        if (name.equals("destinationDir") && descriptor.equals("(Ljava/io/File;)V") && (opcode == Opcodes.INVOKEVIRTUAL || opcode == Opcodes.INVOKEINTERFACE)) {
                            mv._INVOKESTATIC(TASK__ADAPTER_TYPE, "access_set_destinationDir", "(Lorg/gradle/test/Task;Ljava/io/File;)V");
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

    private static String getDefaultDeprecation(String className, String propertyName) {
        return "DeprecationLogger.deprecateProperty(" + className + ".class, \"" + propertyName + "\")\n" +
            ".withContext(\"Property was automatically upgraded to the lazy version.\")\n" +
            ".startingWithGradle9(\"this property is replaced with a lazy version\")\n" +
            ".undocumented()\n" +
            ".nagUser();";
    }
}

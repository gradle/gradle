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

class PropertyUpgradeCodeGenTest extends InstrumentationCodeGenTest {

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
        """
        assertThat(compilation).succeededWithoutWarnings()
        assertThat(compilation)
            .generatedSourceFile(fqName(generatedClass))
            .hasSourceEquivalentTo(generatedClass)
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
            package org.gradle.internal.instrumentation;
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

    def "should generate interceptors for custom accessors"() {
        given:
        def givenSource = source """
            package org.gradle.test;

            import org.gradle.api.provider.Property;
            import org.gradle.internal.instrumentation.api.annotations.VisitForInstrumentation;
            import org.gradle.internal.instrumentation.api.annotations.UpgradedProperty;
            import org.gradle.internal.instrumentation.api.annotations.UpgradedProperty.UpgradedGetter;
            import org.gradle.internal.instrumentation.api.annotations.UpgradedProperty.UpgradedSetter;

            @VisitForInstrumentation(value = {Task.class})
            public abstract class Task {
                @UpgradedProperty(accessors = TaskCustomAccessors.class)
                public abstract Property<Integer> getMaxErrors();
            }
        """
        def givenAccessorsSource = source """
                package org.gradle.test;

                import org.gradle.internal.instrumentation.api.annotations.UpgradedProperty.UpgradedGetter;
                import org.gradle.internal.instrumentation.api.annotations.UpgradedProperty.UpgradedSetter;
                import org.gradle.internal.instrumentation.api.annotations.UpgradedProperty.UpgradedGroovyProperty;

                public class TaskCustomAccessors {
                    @UpgradedSetter(forProperty = "maxErrors")
                    public static void access_setMaxErrors(Task self, int value) {
                    }
                    @UpgradedSetter(forProperty = "maxErrors")
                    public static Task access_setMaxErrors(Task self, Object value) {
                        return self;
                    }

                    @UpgradedGetter(forProperty = "maxErrors")
                    @UpgradedGroovyProperty(forProperty = "maxErrors")
                    public static int access_getMaxErrors(Task self) {
                        return self.getMaxErrors().get();
                    }
                }
        """

        when:
        Compilation compilation = compile(givenSource, givenAccessorsSource)

        then:
        def expectedJvmInterceptors = source """
            package org.gradle.internal.classpath;

            import java.lang.Override;
            import java.lang.String;
            import java.util.function.Supplier;
            import org.gradle.internal.instrumentation.api.jvmbytecode.JvmBytecodeCallInterceptor;
            import org.gradle.model.internal.asm.MethodVisitorScope;
            import org.gradle.test.Task;
            import org.gradle.test.TaskCustomAccessors;
            import org.objectweb.asm.Opcodes;
            import org.objectweb.asm.Type;
            import org.objectweb.asm.tree.MethodNode;

            class InterceptorDeclaration_JvmBytecodeImplPropertyUpgrades extends MethodVisitorScope implements JvmBytecodeCallInterceptor {
                private static final Type TASK_CUSTOM_ACCESSORS_TYPE = Type.getType(TaskCustomAccessors.class);

                @Override
                public boolean visitMethodInsn(String className, int opcode, String owner, String name,
                        String descriptor, boolean isInterface, Supplier<MethodNode> readMethodNode) {
                    if (metadata.isInstanceOf(owner, "org/gradle/test/Task")) {
                        if (name.equals("getMaxErrors") && descriptor.equals("()I") && opcode == Opcodes.INVOKEVIRTUAL) {
                            _INVOKESTATIC(TASK_CUSTOM_ACCESSORS_TYPE, "access_getMaxErrors", "(Lorg/gradle/test/Task;)I");
                            return true;
                        }
                    }
                    if (metadata.isInstanceOf(owner, "org/gradle/test/Task")) {
                        if (name.equals("setMaxErrors") && descriptor.equals("(I)V") && opcode == Opcodes.INVOKEVIRTUAL) {
                            _INVOKESTATIC(TASK_CUSTOM_ACCESSORS_TYPE, "access_setMaxErrors", "(Lorg/gradle/test/Task;I)V");
                            return true;
                        }
                    }
                    if (metadata.isInstanceOf(owner, "org/gradle/test/Task")) {
                        if (name.equals("setMaxErrors") && descriptor.equals("(Ljava/lang/Object;)Lorg/gradle/test/Task;") && opcode == Opcodes.INVOKEVIRTUAL) {
                            _INVOKESTATIC(TASK_CUSTOM_ACCESSORS_TYPE, "access_setMaxErrors", "(Lorg/gradle/test/Task;Ljava/lang/Object;)Lorg/gradle/test/Task;");
                            return true;
                        }
                    }
                    return false;
                }
            }
        """
        def expectedGroovyInterceptors = source """
            package org.gradle.internal.classpath;

            import java.lang.Object;
            import java.lang.Override;
            import java.lang.String;
            import java.lang.Throwable;
            import java.util.Arrays;
            import java.util.List;
            import org.gradle.internal.classpath.intercept.CallInterceptor;
            import org.gradle.internal.classpath.intercept.InterceptScope;
            import org.gradle.internal.classpath.intercept.Invocation;
            import org.gradle.test.Task;
            import org.gradle.test.TaskCustomAccessors;

            class InterceptorDeclaration_GroovyInterceptorsImplPropertyUpgrades {
                public static List<CallInterceptor> getCallInterceptors() {
                    return Arrays.asList(
                        new GetMaxErrorsCallInterceptor()
                    );
                }

                /**
                 * Intercepts the following declarations:<ul>
                 *
                 * <li> Groovy property getter {@link org.gradle.test.Task#maxErrors}
                 *      with {@link TaskCustomAccessors#access_getMaxErrors(Task)}
                 *
                 * </ul>
                 */
                private static class GetMaxErrorsCallInterceptor extends CallInterceptor {
                    public GetMaxErrorsCallInterceptor() {
                        super(InterceptScope.readsOfPropertiesNamed("maxErrors"), InterceptScope.methodsNamed("getMaxErrors"));
                    }

                    @Override
                    protected Object doIntercept(Invocation invocation, String consumer) throws Throwable {
                        Object receiver = invocation.getReceiver();
                        if (receiver instanceof Task) {
                            Task receiverTyped = (Task) receiver;
                            if (invocation.getArgsCount() == 0) {
                                return TaskCustomAccessors.access_getMaxErrors(receiverTyped);
                            }
                        }
                        return invocation.callOriginal();
                    }
                }
            }
        """
        assertThat(compilation).succeededWithoutWarnings()
        assertThat(compilation)
            .generatedSourceFile(fqName(expectedJvmInterceptors))
            .containsElementsIn(expectedJvmInterceptors)
        assertThat(compilation)
            .generatedSourceFile(fqName(expectedGroovyInterceptors))
            .hasSourceEquivalentTo(expectedGroovyInterceptors)
    }
}

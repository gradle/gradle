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
import spock.lang.Issue

import static com.google.testing.compile.CompilationSubject.assertThat

class PropertyUpgradeCustomInterceptorCodeGenTest extends InstrumentationCodeGenTest {

    def "should generate bridge class for upgraded property with custom adapter"() {
        given:
        def givenSource = source """
            package org.gradle.test;

            import org.gradle.api.provider.Property;
            import org.gradle.internal.instrumentation.api.annotations.ReplacesEagerProperty;
            import org.gradle.internal.instrumentation.api.annotations.BytecodeUpgrade;

            public abstract class Task {
                @ReplacesEagerProperty(adapter = Task.MaxErrorsAdapter.class)
                public abstract Property<Integer> getMaxErrors();

                static class MaxErrorsAdapter {
                    @BytecodeUpgrade
                    static int maxErrors(Task task) {
                        return 0;
                    }

                    @BytecodeUpgrade
                    static int getMaxErrors(Task task) {
                        return 0;
                    }

                    @BytecodeUpgrade
                    static Task maxErrors(Task task, int maxErrors) {
                        return task;
                    }

                    @BytecodeUpgrade
                    static void setMaxErrors(Task task, int maxErrors) {
                    }
                }
            }
        """

        when:
        Compilation compilation = compile(givenSource)

        then:
        def expectedGeneratedClass = source """
             package org.gradle.test;
             import org.gradle.api.Generated;

             @Generated
             public final class \$\$BridgeFor\$\$Task\$\$MaxErrorsAdapter {
                 public static int access_get_getMaxErrors(Task task) {
                     return Task.MaxErrorsAdapter.getMaxErrors(task);
                 }
                 public static int access_get_maxErrors(Task task) {
                     return Task.MaxErrorsAdapter.maxErrors(task);
                 }
                 public static Task access_set_maxErrors(Task task, int maxErrors) {
                     return Task.MaxErrorsAdapter.maxErrors(task, maxErrors);
                 }
                 public static void access_set_setMaxErrors(Task task, int maxErrors) {
                     Task.MaxErrorsAdapter.setMaxErrors(task, maxErrors);
                 }
             }
        """
        assertThat(compilation).succeededWithoutWarnings()
        assertThat(compilation)
            .generatedSourceFile(fqName(expectedGeneratedClass))
            .containsElementsIn(expectedGeneratedClass)
    }

    def "should generate interceptor for custom adapter"() {
        given:
        def givenSource = source """
            package org.gradle.test;

            import org.gradle.api.provider.Property;
            import org.gradle.internal.instrumentation.api.annotations.ReplacesEagerProperty;
            import org.gradle.internal.instrumentation.api.annotations.BytecodeUpgrade;

            public abstract class Task {
                @ReplacesEagerProperty(adapter = Task.MaxErrorsAdapter.class)
                public abstract Property<Integer> getMaxErrors();

                static class MaxErrorsAdapter {
                    @BytecodeUpgrade
                    static int getMaxErrors(Task task) {
                        return 0;
                    }

                    @BytecodeUpgrade
                    static Task maxErrors(Task task, int maxErrors) {
                        return task;
                    }
                }
            }
        """

        when:
        Compilation compilation = compile(givenSource)

        then:
        def expectedGeneratedClass = source """
            package org.gradle.internal.classpath.generated;

            @Generated
            public class InterceptorDeclaration_PropertyUpgradesJvmBytecode_TestProject implements JvmBytecodeCallInterceptor, FilterableBytecodeInterceptor.BytecodeUpgradeInterceptor {
                 @Override
                 public boolean visitMethodInsn(MethodVisitorScope mv, String className, int opcode, String owner, String name,
                         String descriptor, boolean isInterface, Supplier<MethodNode> readMethodNode) {
                     if (metadata.isInstanceOf(owner, "org/gradle/test/Task")) {
                         if (name.equals("getMaxErrors") && descriptor.equals("()I") && (opcode == Opcodes.INVOKEVIRTUAL || opcode == Opcodes.INVOKEINTERFACE)) {
                             mv._INVOKESTATIC(\$\$_BRIDGE_FOR\$\$_TASK\$\$_MAX_ERRORS_ADAPTER_TYPE, "access_get_getMaxErrors", "(Lorg/gradle/test/Task;)I");
                             return true;
                         }
                         if (name.equals("maxErrors") && descriptor.equals("(I)Lorg/gradle/test/Task;") && (opcode == Opcodes.INVOKEVIRTUAL || opcode == Opcodes.INVOKEINTERFACE)) {
                             mv._INVOKESTATIC(\$\$_BRIDGE_FOR\$\$_TASK\$\$_MAX_ERRORS_ADAPTER_TYPE, "access_set_maxErrors", "(Lorg/gradle/test/Task;I)Lorg/gradle/test/Task;");
                             return true;
                         }
                     }
                     return false;
                 }
            }
        """
        assertThat(compilation).succeededWithoutWarnings()
        assertThat(compilation)
            .generatedSourceFile(fqName(expectedGeneratedClass))
            .containsElementsIn(expectedGeneratedClass)
    }

    def "should fail compilation if adapter and it's methods are not package-private"() {
        given:
        def givenSource = source """
            package org.gradle.test;

            import org.gradle.api.provider.Property;
            import org.gradle.internal.instrumentation.api.annotations.ReplacesEagerProperty;
            import org.gradle.internal.instrumentation.api.annotations.BytecodeUpgrade;

            public abstract class Task {
                @ReplacesEagerProperty(adapter = Task.MaxErrorsAdapter.class)
                public abstract Property<Integer> getMaxErrors();

                public static class MaxErrorsAdapter {
                    @BytecodeUpgrade
                    public static int firstMethod(Task task) {
                        return 0;
                    }

                    @BytecodeUpgrade
                    int secondMethod(Task task) {
                        return 0;
                    }

                    @BytecodeUpgrade
                    private Task thirdMethod(Task task, int maxErrors) {
                        return task;
                    }

                    @BytecodeUpgrade
                    static int forthMethod() {
                        return 0;
                    }

                    @BytecodeUpgrade
                    static int fifthMethod(int param) {
                        return 0;
                    }
                }
            }
        """

        when:
        Compilation compilation = compile(givenSource)

        then:
        assertThat(compilation).hadErrorCount(1)
        assertThat(compilation).hadErrorContaining("Adapter class 'org.gradle.test.Task.MaxErrorsAdapter' should be package private, but it's not.")
        assertThat(compilation).hadErrorContaining("Adapter method 'org.gradle.test.Task.MaxErrorsAdapter.firstMethod(org.gradle.test.Task)' should be package-private but it's not.")
        assertThat(compilation).hadErrorContaining("Adapter method 'org.gradle.test.Task.MaxErrorsAdapter.secondMethod(org.gradle.test.Task)' should be static but it's not.")
        assertThat(compilation).hadErrorContaining("Adapter method 'org.gradle.test.Task.MaxErrorsAdapter.thirdMethod(org.gradle.test.Task,int)' should be package-private but it's not.")
        assertThat(compilation).hadErrorContaining("Adapter method 'org.gradle.test.Task.MaxErrorsAdapter.thirdMethod(org.gradle.test.Task,int)' should be static but it's not.")
        assertThat(compilation).hadErrorContaining("'org.gradle.test.Task.MaxErrorsAdapter.forthMethod()' has no parameters, but it should have at least one of type 'org.gradle.test.Task'.")
        assertThat(compilation).hadErrorContaining("Adapter method 'org.gradle.test.Task.MaxErrorsAdapter.fifthMethod(int)' should have first parameter of type 'org.gradle.test.Task', but first parameter is of type 'int'.")
    }

    def "should correctly intercept Java code"() {
        given:
        def newTask = source """
            package org.gradle.test;

            import org.gradle.api.provider.Property;
            import org.gradle.internal.instrumentation.api.annotations.ReplacesEagerProperty;
            import org.gradle.internal.instrumentation.api.annotations.BytecodeUpgrade;

            public abstract class Task {
                @ReplacesEagerProperty(adapter = Task.MaxErrorsAdapter.class)
                public abstract Property<Integer> getMaxErrors();

                static class MaxErrorsAdapter {
                    @BytecodeUpgrade
                    static int getMaxErrors(Task task) {
                        return task.getMaxErrors().getOrElse(0);
                    }

                    @BytecodeUpgrade
                    static Task maxErrors(Task task, int maxErrors) {
                        task.getMaxErrors().set(maxErrors);
                        return task;
                    }
                }
            }
        """
        def oldTask = source """
            package org.gradle.test;

            public class Task {
                public int getMaxErrors() {
                    return 0;
                }
                public Task maxErrors(int maxErrors) {
                    return this;
                }
            }
        """
        def taskRunner = source """
            package org.gradle.test;

            import org.gradle.util.TestUtil;
            import java.lang.Runnable;

            public class TaskRunner implements Runnable {
                public void run() {
                    Task task = TestUtil.newInstance(Task.class);
                    assert task.getMaxErrors() == 0;
                    assert task.maxErrors(5) == task;
                    assert task.getMaxErrors() == 5;
                }
            }
        """

        when:
        Compilation oldTaskCompilation = compile(oldTask, taskRunner)
        Compilation newTaskCompilation = compile(newTask)

        then:
        assertThat(oldTaskCompilation).succeeded()
        assertThat(newTaskCompilation).succeeded()

        when:
        Runnable instrumentedTaskRunner = instrumentRunnerJavaClass(
            "org.gradle.test.TaskRunner",
            "org.gradle.internal.classpath.generated.InterceptorDeclaration_PropertyUpgradesJvmBytecode_TestProject\$Factory",
            oldTaskCompilation,
            newTaskCompilation
        )

        then:
        instrumentedTaskRunner.run()
    }

    @Issue("https://github.com/gradle/gradle/issues/29539")
    def "should intercept and bridge a method with any new return type"() {
        given:
        def newTask = source """
            package org.gradle.test;

            import org.gradle.internal.instrumentation.api.annotations.ReplacesEagerProperty;
            import org.gradle.internal.instrumentation.api.annotations.BytecodeUpgrade;

            public class Task {
                private final MaxErrors maxErrors = new MaxErrors();

                @ReplacesEagerProperty(adapter = Task.MaxErrorsAdapter.class)
                public MaxErrors getMaxErrors() {
                    return maxErrors;
                }

                class MaxErrors {
                    int maxErrors = 0;
                }

                static class MaxErrorsAdapter {
                    @BytecodeUpgrade
                    static int getMaxErrors(Task task) {
                        return task.getMaxErrors().maxErrors;
                    }

                    @BytecodeUpgrade
                    static void setMaxErrors(Task task, int maxErrors) {
                        task.getMaxErrors().maxErrors = maxErrors;
                    }
                }
            }
        """
        def oldTask = source """
            package org.gradle.test;

            public class Task {
                public int getMaxErrors() {
                    return 0;
                }
                public void setMaxErrors(int maxErrors) {
                }
            }
        """
        def taskRunner = source """
            package org.gradle.test;

            import java.lang.Runnable;

            public class TaskRunner implements Runnable {
                public void run() {
                    Task task = new Task();
                    assert task.getMaxErrors() == 0;
                    task.setMaxErrors(5);
                    assert task.getMaxErrors() == 5;
                }
            }
        """

        when:
        Compilation oldTaskCompilation = compile(oldTask, taskRunner)
        Compilation newTaskCompilation = compile(newTask)

        then:
        assertThat(oldTaskCompilation).succeeded()
        assertThat(newTaskCompilation).succeeded()

        when:
        Runnable instrumentedTaskRunner = instrumentRunnerJavaClass(
            "org.gradle.test.TaskRunner",
            "org.gradle.internal.classpath.generated.InterceptorDeclaration_PropertyUpgradesJvmBytecode_TestProject\$Factory",
            oldTaskCompilation,
            newTaskCompilation
        )

        then:
        instrumentedTaskRunner.run()
    }
}

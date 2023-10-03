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

package org.gradle.internal.instrumentation.processor.codegen.jvmbytecode

import com.google.testing.compile.Compilation
import org.gradle.internal.instrumentation.InstrumentationCodeGenTest

import static com.google.testing.compile.CompilationSubject.assertThat
import static org.gradle.internal.instrumentation.api.declarations.InterceptorDeclaration.JVM_BYTECODE_GENERATED_CLASS_NAME_FOR_PROPERTY_UPGRADES

class InterceptJvmCallsGeneratorTest extends InstrumentationCodeGenTest {

    def "should generate interceptors for inherited calls"() {
        given:
        def givenSource = source """
            package org.gradle.test;
            import org.gradle.internal.instrumentation.api.annotations.*;
            import org.gradle.internal.instrumentation.api.annotations.CallableKind.*;
            import org.gradle.internal.instrumentation.api.annotations.ParameterKind.*;
            import org.gradle.internal.classpath.declarations.*;
            import org.gradle.api.*;

            @SpecificJvmCallInterceptors(generatedClassName = "my.InterceptorDeclaration_JvmBytecodeImpl")
            public class FileInterceptorsDeclaration {
                @InterceptCalls
                @InstanceMethod
                @InterceptInherited
                public static String intercept_getDescription(@Receiver Rule thisRule) {
                    return "";
                }
            }
        """

        when:
        Compilation compilation = compile(givenSource)

        then:
        def expectedJvmInterceptors = source """
            package my;
            public class InterceptorDeclaration_JvmBytecodeImpl extends MethodVisitorScope implements JvmBytecodeCallInterceptor {

                private final MethodVisitor methodVisitor;
                private final InstrumentationMetadata metadata;

                private InterceptorDeclaration_JvmBytecodeImpl(MethodVisitor methodVisitor, InstrumentationMetadata metadata) {
                    super(methodVisitor);
                    this.methodVisitor = methodVisitor;
                    this.metadata = metadata;
                }

                @Override
                public boolean visitMethodInsn(String className, int opcode, String owner, String name,
                        String descriptor, boolean isInterface, Supplier<MethodNode> readMethodNode) {
                    if (metadata.isInstanceOf(owner, "org/gradle/api/Rule")) {
                        if (name.equals("getDescription") && descriptor.equals("()Ljava/lang/String;") && (opcode == Opcodes.INVOKEVIRTUAL || opcode == Opcodes.INVOKEINTERFACE)) {
                            _INVOKESTATIC(FILE_INTERCEPTORS_DECLARATION_TYPE, "intercept_getDescription", "(Lorg/gradle/api/Rule;)Ljava/lang/String;");
                            return true;
                        }
                    }
                    return false;
                }
            }
        """
        assertThat(compilation).succeededWithoutWarnings()
        assertThat(compilation)
            .generatedSourceFile(fqName(expectedJvmInterceptors))
            .containsElementsIn(expectedJvmInterceptors)
    }

    def "compilation should fail if non-gradle type is instrumented with @InterceptInherited"() {
        given:
        def givenSource = source """
            package org.gradle.test;
            import org.gradle.internal.instrumentation.api.annotations.*;
            import org.gradle.internal.instrumentation.api.annotations.CallableKind.*;
            import org.gradle.internal.instrumentation.api.annotations.ParameterKind.*;
            import java.io.File;

            @SpecificJvmCallInterceptors(generatedClassName = "my.InterceptorDeclaration_JvmBytecodeImpl")
            public class FileInterceptorsDeclaration {
                @InterceptCalls
                @InstanceMethod
                @InterceptInherited
                public static File[] intercept_listFiles(@Receiver File thisFile) {
                    return new File[0];
                }
            }
        """

        when:
        Compilation compilation = compile(givenSource)

        then:
        assertThat(compilation).failed()
        assertThat(compilation).hadErrorContaining("Intercepting inherited methods is supported only for Gradle types for now, but type was: java/io/File")
    }

    def "should generate interceptor with public modifier and a public factory class"() {
        given:
        def givenSource = source """
            package org.gradle.test;
            import org.gradle.internal.instrumentation.api.annotations.*;
            import org.gradle.internal.instrumentation.api.annotations.CallableKind.*;
            import org.gradle.internal.instrumentation.api.annotations.ParameterKind.*;
            import java.io.File;

            @SpecificJvmCallInterceptors(generatedClassName = "my.InterceptorDeclaration_JvmBytecodeImpl")
            public class FileInterceptorsDeclaration {
                @InterceptCalls
                @InstanceMethod
                public static File[] intercept_listFiles(@Receiver File thisFile) {
                    return new File[0];
                }
            }
        """

        when:
        Compilation compilation = compile(givenSource)

        then:
        def expectedJvmInterceptors = source """
            package my;
            public class InterceptorDeclaration_JvmBytecodeImpl extends MethodVisitorScope implements JvmBytecodeCallInterceptor {

                private InterceptorDeclaration_JvmBytecodeImpl(MethodVisitor methodVisitor, InstrumentationMetadata metadata) {
                    super(methodVisitor);
                    this.methodVisitor = methodVisitor;
                    this.metadata = metadata;
                }

                public static class Factory implements JvmBytecodeCallInterceptor.Factory {
                    @Override
                    public JvmBytecodeCallInterceptor create(MethodVisitor methodVisitor, InstrumentationMetadata metadata) {
                        return new my.InterceptorDeclaration_JvmBytecodeImpl(methodVisitor, metadata);
                    }
                }
            }
        """
        assertThat(compilation).succeededWithoutWarnings()
        assertThat(compilation)
            .generatedSourceFile(fqName(expectedJvmInterceptors))
            .containsElementsIn(expectedJvmInterceptors)
    }

    def "should group visitMethodInsn logic by call owner"() {
        given:
        def givenFirstSource = source """
            package org.gradle.test;
            import org.gradle.internal.instrumentation.api.annotations.*;
            import org.gradle.internal.instrumentation.api.annotations.CallableKind.*;
            import org.gradle.internal.instrumentation.api.annotations.ParameterKind.*;
            import java.io.File;

            @SpecificJvmCallInterceptors(generatedClassName = "my.InterceptorDeclaration_JvmBytecodeImpl")
            public class FileInterceptorsDeclaration {
                @InterceptCalls
                @InstanceMethod
                public static File[] intercept_listFiles(@Receiver File thisFile) {
                    return new File[0];
                }
            }
        """
        def givenSecondSource = source """
            package org.gradle.test;
            import org.gradle.internal.instrumentation.api.annotations.*;
            import org.gradle.internal.instrumentation.api.annotations.CallableKind.*;
            import org.gradle.internal.instrumentation.api.annotations.ParameterKind.*;
            import java.io.File;

            @SpecificJvmCallInterceptors(generatedClassName = "my.InterceptorDeclaration_JvmBytecodeImpl")
            public class FileInterceptorsDeclaration2 {
                @InterceptCalls
                @InstanceMethod
                public static boolean intercept_exists(@Receiver File thisFile) {
                    return false;
                }
            }
        """

        when:
        Compilation compilation = compile(givenFirstSource, givenSecondSource)

        then:
        def expectedJvmInterceptors = source """
            package my;
            public class InterceptorDeclaration_JvmBytecodeImpl extends MethodVisitorScope implements JvmBytecodeCallInterceptor {
                @Override
                public boolean visitMethodInsn(String className, int opcode, String owner, String name,
                        String descriptor, boolean isInterface, Supplier<MethodNode> readMethodNode) {
                    if (owner.equals("java/io/File")) {
                        if (name.equals("listFiles") && descriptor.equals("()[Ljava/io/File;") && (opcode == Opcodes.INVOKEVIRTUAL || opcode == Opcodes.INVOKEINTERFACE)) {
                            _INVOKESTATIC(FILE_INTERCEPTORS_DECLARATION_TYPE, "intercept_listFiles", "(Ljava/io/File;)[Ljava/io/File;");
                            return true;
                        }
                        if (name.equals("exists") && descriptor.equals("()Z") && (opcode == Opcodes.INVOKEVIRTUAL || opcode == Opcodes.INVOKEINTERFACE)) {
                            _INVOKESTATIC(FILE_INTERCEPTORS_DECLARATION2_TYPE, "intercept_exists", "(Ljava/io/File;)Z");
                            return true;
                        }
                    }
                    return false;
                }
            }
        """
        assertThat(compilation).succeededWithoutWarnings()
        assertThat(compilation)
            .generatedSourceFile(fqName(expectedJvmInterceptors))
            .containsElementsIn(expectedJvmInterceptors)
    }

    def "should fail if we use the same class for instrumentation as is for property upgrades"() {
        given:
        def instrumentation = source """
            package org.gradle.test;
            import org.gradle.internal.instrumentation.api.annotations.*;
            import org.gradle.internal.instrumentation.api.annotations.CallableKind.*;
            import org.gradle.internal.instrumentation.api.annotations.ParameterKind.*;
            import org.gradle.internal.classpath.declarations.*;
            import org.gradle.api.*;

            @SpecificJvmCallInterceptors(generatedClassName = "${JVM_BYTECODE_GENERATED_CLASS_NAME_FOR_PROPERTY_UPGRADES}_TestProject")
            public class FileInterceptorsDeclaration {
                @InterceptCalls
                @InstanceMethod
                @InterceptInherited
                public static String intercept_getDescription(@Receiver Rule thisRule) {
                    return "";
                }
            }
        """
        def propertyUpgrade = source """
            package org.gradle.test;

            import org.gradle.api.provider.Property;
            import org.gradle.internal.instrumentation.api.annotations.VisitForInstrumentation;
            import org.gradle.internal.instrumentation.api.annotations.UpgradedProperty;

            public abstract class Task {
                @UpgradedProperty
                public abstract Property<Boolean> getIncremental();
            }
        """

        when:
        Compilation compilation = compile(instrumentation, propertyUpgrade)

        then:
        assertThat(compilation).failed()
        assertThat(compilation).hadErrorContaining("${JVM_BYTECODE_GENERATED_CLASS_NAME_FOR_PROPERTY_UPGRADES}_TestProject' does instrumentation and bytecode upgrades at the same time. This is not supported!")
    }
}

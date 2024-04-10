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
import org.gradle.internal.instrumentation.api.types.BytecodeInterceptorType

import static com.google.testing.compile.CompilationSubject.assertThat

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

            @Generated
            public class InterceptorDeclaration_JvmBytecodeImpl implements JvmBytecodeCallInterceptor, FilterableBytecodeInterceptor.InstrumentationInterceptor {
                @Override
                public boolean visitMethodInsn(String className, int opcode, String owner, String name,
                        String descriptor, boolean isInterface, Supplier<MethodNode> readMethodNode) {
                    if (metadata.isInstanceOf(owner, "org/gradle/api/Rule")) {
                        if (name.equals("getDescription") && descriptor.equals("()Ljava/lang/String;") && (opcode == Opcodes.INVOKEVIRTUAL || opcode == Opcodes.INVOKEINTERFACE)) {
                            mv._INVOKESTATIC(FILE_INTERCEPTORS_DECLARATION_TYPE, "intercept_getDescription", "(Lorg/gradle/api/Rule;)Ljava/lang/String;");
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

    def "should generate interceptor with public modifier and a public factory class for #type"() {
        given:
        def givenSource = source """
            package org.gradle.test;
            import org.gradle.internal.instrumentation.api.annotations.*;
            import org.gradle.internal.instrumentation.api.annotations.CallableKind.*;
            import org.gradle.internal.instrumentation.api.annotations.ParameterKind.*;
            import java.io.File;

            @SpecificJvmCallInterceptors(generatedClassName = "my.InterceptorDeclaration_JvmBytecodeImpl", type = ${type.getClass().getName()}.$type)
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
        def capability = type.getInterceptorMarkerInterface().getCanonicalName() - (type.getInterceptorMarkerInterface().package.name + ".")
        def factoryCapability = type.getInterceptorFactoryMarkerInterface().getCanonicalName() - (type.getInterceptorFactoryMarkerInterface().package.name + ".")
        def expectedJvmInterceptors = source """
            package my;

            @Generated
            public class InterceptorDeclaration_JvmBytecodeImpl implements JvmBytecodeCallInterceptor, $capability {

                public static class Factory implements JvmBytecodeCallInterceptor.Factory, $factoryCapability {
                }
            }
        """
        assertThat(compilation).succeededWithoutWarnings()
        assertThat(compilation)
            .generatedSourceFile(fqName(expectedJvmInterceptors))
            .containsElementsIn(expectedJvmInterceptors)

        where:
        type << [BytecodeInterceptorType.INSTRUMENTATION, BytecodeInterceptorType.BYTECODE_UPGRADE]
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

            @Generated
            public class InterceptorDeclaration_JvmBytecodeImpl implements JvmBytecodeCallInterceptor, FilterableBytecodeInterceptor.InstrumentationInterceptor {
                @Override
                public boolean visitMethodInsn(String className, int opcode, String owner, String name,
                        String descriptor, boolean isInterface, Supplier<MethodNode> readMethodNode) {
                    if (owner.equals("java/io/File")) {
                        if (name.equals("listFiles") && descriptor.equals("()[Ljava/io/File;") && (opcode == Opcodes.INVOKEVIRTUAL || opcode == Opcodes.INVOKEINTERFACE)) {
                            mv._INVOKESTATIC(FILE_INTERCEPTORS_DECLARATION_TYPE, "intercept_listFiles", "(Ljava/io/File;)[Ljava/io/File;");
                            return true;
                        }
                        if (name.equals("exists") && descriptor.equals("()Z") && (opcode == Opcodes.INVOKEVIRTUAL || opcode == Opcodes.INVOKEINTERFACE)) {
                            mv._INVOKESTATIC(FILE_INTERCEPTORS_DECLARATION2_TYPE, "intercept_exists", "(Ljava/io/File;)Z");
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

    def "should be able to inject visitor context"() {
        given:
        def givenSource = source """
            package org.gradle.test;
            import org.gradle.internal.instrumentation.api.annotations.*;
            import org.gradle.internal.instrumentation.api.annotations.CallableKind.*;
            import org.gradle.internal.instrumentation.api.annotations.ParameterKind.*;
            import org.gradle.internal.instrumentation.api.types.*;
            import java.io.File;

            @SpecificJvmCallInterceptors(generatedClassName = "my.InterceptorDeclaration_JvmBytecodeImpl")
            public class FileInterceptorsDeclaration2 {
                @InterceptCalls
                @InstanceMethod
                public static boolean intercept_exists(@Receiver File thisFile, @InjectVisitorContext BytecodeInterceptorFilter filter) {
                    return false;
                }
            }
        """

        when:
        Compilation compilation = compile(givenSource)

        then:
        def expectedJvmInterceptors = source """
            package my;

            @Generated
            public class InterceptorDeclaration_JvmBytecodeImpl implements JvmBytecodeCallInterceptor, FilterableBytecodeInterceptor.InstrumentationInterceptor {
                @Override
                public boolean visitMethodInsn(String className, int opcode, String owner, String name,
                        String descriptor, boolean isInterface, Supplier<MethodNode> readMethodNode) {
                    if (owner.equals("java/io/File")) {
                        if (name.equals("exists") && descriptor.equals("()Z") && (opcode == Opcodes.INVOKEVIRTUAL || opcode == Opcodes.INVOKEINTERFACE)) {
                            mv._GETSTATIC(INTERCEPTORS_REQUEST_TYPE, context.name(), INTERCEPTORS_REQUEST_TYPE.getDescriptor());
                            mv._INVOKESTATIC(FILE_INTERCEPTORS_DECLARATION2_TYPE, "intercept_exists", "(Ljava/io/File;Lorg/gradle/internal/instrumentation/api/types/BytecodeInterceptorFilter;)Z");
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
}

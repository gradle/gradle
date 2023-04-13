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

class InterceptJvmCallsGeneratorTest extends InstrumentationCodeGenTest {

    def "should generate interceptors for inherited calls"() {
        given:
        def givenSource = source """
            package org.gradle.test;
            import org.gradle.internal.instrumentation.api.annotations.*;
            import org.gradle.internal.instrumentation.api.annotations.CallableKind.*;
            import org.gradle.internal.instrumentation.api.annotations.ParameterKind.*;
            import org.gradle.internal.classpath.declarations.*;
            import java.io.File;

            @SpecificJvmCallInterceptors(generatedClassName = InterceptorDeclaration.JVM_BYTECODE_GENERATED_CLASS_NAME)
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
        def expectedJvmInterceptors = source """
            package org.gradle.internal.classpath;
            class InterceptorDeclaration_JvmBytecodeImpl extends MethodVisitorScope implements JvmBytecodeCallInterceptor {

                private final MethodVisitor methodVisitor;
                private final InstrumentationMetadata metadata;

                public InterceptorDeclaration_JvmBytecodeImpl(MethodVisitor methodVisitor, InstrumentationMetadata metadata) {
                    super(methodVisitor);
                    this.methodVisitor = methodVisitor;
                    this.metadata = metadata;
                }

                @Override
                public boolean visitMethodInsn(String className, int opcode, String owner, String name,
                        String descriptor, boolean isInterface, Supplier<MethodNode> readMethodNode) {
                    if (metadata.isInstanceOf(owner, "java/io/File")) {
                        if (name.equals("listFiles") && descriptor.equals("()[Ljava/io/File;") && opcode == Opcodes.INVOKEVIRTUAL) {
                            _INVOKESTATIC(FILE_INTERCEPTORS_DECLARATION_TYPE, "intercept_listFiles", "(Ljava/io/File;)[Ljava/io/File;");
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

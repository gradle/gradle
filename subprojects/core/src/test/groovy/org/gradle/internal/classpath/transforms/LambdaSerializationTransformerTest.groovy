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

package org.gradle.internal.classpath.transforms

import org.gradle.internal.classanalysis.AsmConstants
import org.gradle.internal.classpath.ClassWithActionLambda
import org.gradle.internal.classpath.ClassWithCapturelessLambda
import org.gradle.internal.classpath.ClassWithObjectCapturingLambda
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Type
import org.objectweb.asm.commons.CodeSizeEvaluator
import spock.lang.Specification

class LambdaSerializationTransformerTest extends Specification {
    def "transformer estimates deserialization code size of #cl correctly"() {
        when:
        def classWriter = new ClassWriter(ClassWriter.COMPUTE_MAXS)

        LambdaSerializationTransformer transformer = acceptClass(cl, new LambdaSerializationTransformer(classWriter))

        def estimatedDeserializationMethodSize = (
            transformer.estimatedDeserializationPrologueLength
                + transformer.estimatedEpilogueLength
                + transformer.getEstimatedSingleLambdaHandlingCodeLength(lambdaArgs as Type[])
        )

        then:
        // ASM has no API to figure out the exact method size, so we have to rely on estimations.
        estimatedDeserializationMethodSize == getMaxEvaluatedDeserializationMethodSize(classWriter.toByteArray())

        where:
        cl                             | lambdaArgs
        ClassWithActionLambda          | [Type.getType(int.class)]
        ClassWithCapturelessLambda     | []
        ClassWithObjectCapturingLambda | [Type.getType(String)]
    }

    int getMaxEvaluatedDeserializationMethodSize(byte[] classData) {
        int maxMethodSize = 0;
        def deserializeLambdaSizeEvaluator = new ClassVisitor(AsmConstants.ASM_LEVEL) {
            @Override
            MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                if ('$deserializeLambda$' == name) {
                    return new CodeSizeEvaluator(super.visitMethod(access, name, descriptor, signature, exceptions)) {
                        @Override
                        void visitEnd() {
                            maxMethodSize = getMaxSize()
                            super.visitEnd()
                        }
                    }
                }
                return super.visitMethod(access, name, descriptor, signature, exceptions)
            }
        }

        acceptClass(classData, deserializeLambdaSizeEvaluator)

        return maxMethodSize
    }

    <T extends ClassVisitor> T acceptClass(Class cl, T visitor) {
        def fileName = cl.name.replace('.', '/') + ".class"
        byte[] classData = cl.classLoader.getResource(fileName).bytes
        acceptClass(classData, visitor)
        return visitor
    }

    <T extends ClassVisitor> T acceptClass(byte[] classData, T visitor) {
        new ClassReader(classData).accept(visitor, 0)
        return visitor
    }
}

/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.internal.classloader

import org.apache.commons.io.IOUtils
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Type
import spock.lang.Unroll

import static org.objectweb.asm.Opcodes.ACC_PUBLIC
import static org.objectweb.asm.Opcodes.ALOAD
import static org.objectweb.asm.Opcodes.H_INVOKESPECIAL
import static org.objectweb.asm.Opcodes.RETURN
import static org.objectweb.asm.Opcodes.V1_5

@Requires(TestPrecondition.JDK9_OR_LATER)
class ClassLoaderUtilsIntegrationTest extends AbstractIntegrationSpec {
    private String packageName = ClassLoaderUtils.getPackageName()

    @Unroll
    def 'have illegal access warning when trying to inject into #classLoader'() {
        given:
        buildFile << """ 
            apply plugin:'java'
            
            ${jcenterRepository()}

            dependencies {
                testCompile gradleApi()
                testCompile 'junit:junit:4.12'
            }
        """

        file("src/test/java/${packageName.replace('.', '/')}/Test.java") << """
            package ${packageName};
            import java.nio.file.*;
            
            public class Test {
                @org.junit.Test
                public void test() throws Exception {
                    Path path = Paths.get("${file('MyClass.class').absolutePath.replace('\\', '/')}");
                    byte[] bytes = Files.readAllBytes(path);
                    ClassLoaderUtils.define(${classLoader}, "MyClass", bytes);
                }
                
                private static class MyClassLoader extends ClassLoader { }
            }
        """

        createClassFile()

        when:
        succeeds('test')

        then:
        result.hasErrorOutput("Illegal reflective access using Lookup on ${ClassLoaderUtils.name}") == hasWarning

        where:
        classLoader                          | hasWarning
        'ClassLoader.getSystemClassLoader()' | true
        'new MyClassLoader()'                | false
    }

    void createClassFile() {
        ClassWriter cw = new ClassWriter(0)
        cw.visit(V1_5, ACC_PUBLIC, "MyClass", null, 'java/lang/Object', null)
        MethodVisitor mv = cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null)
        mv.visitMaxs(2, 1)
        mv.visitVarInsn(ALOAD, 0) // push `this` to the operand stack
        mv.visitMethodInsn(H_INVOKESPECIAL, Type.getInternalName(Object.class), "<init>", "()V", false)
        mv.visitInsn(RETURN)
        mv.visitEnd()
        cw.visitEnd()

        IOUtils.write(cw.toByteArray(), new FileOutputStream(file('MyClass.class')))
    }

}

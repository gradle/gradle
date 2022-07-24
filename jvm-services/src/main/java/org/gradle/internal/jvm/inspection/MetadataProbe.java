/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.internal.jvm.inspection;

import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import org.gradle.api.GradleException;
import org.gradle.internal.ErroringAction;
import org.gradle.internal.IoActions;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;

import static org.objectweb.asm.Opcodes.ACC_PUBLIC;
import static org.objectweb.asm.Opcodes.ACC_STATIC;
import static org.objectweb.asm.Opcodes.ACC_SUPER;
import static org.objectweb.asm.Opcodes.ALOAD;
import static org.objectweb.asm.Opcodes.GETSTATIC;
import static org.objectweb.asm.Opcodes.INVOKESPECIAL;
import static org.objectweb.asm.Opcodes.INVOKESTATIC;
import static org.objectweb.asm.Opcodes.INVOKEVIRTUAL;
import static org.objectweb.asm.Opcodes.RETURN;
import static org.objectweb.asm.Opcodes.V1_1;

class MetadataProbe {

    private final Supplier<byte[]> probeClass = Suppliers.memoize(() -> createProbeClass());

    public File writeClass(File outputDirectory) {
        File probeFile = new File(outputDirectory, "JavaProbe.class");
        try {
            IoActions.withResource(new FileOutputStream(probeFile), new ErroringAction<FileOutputStream>() {
                @Override
                protected void doExecute(FileOutputStream thing) throws Exception {
                    thing.write(probeClass.get());
                }
            });
        } catch (FileNotFoundException e) {
            throw new GradleException("Unable to write Java probe file", e);
        }
        return probeFile;
    }

    private static byte[] createProbeClass() {
        ClassWriter cw = new ClassWriter(0);
        createClassHeader(cw);
        createConstructor(cw);
        createMainMethod(cw);
        cw.visitEnd();
        return cw.toByteArray();
    }

    private static void createClassHeader(ClassWriter cw) {
        cw.visit(V1_1, ACC_PUBLIC + ACC_SUPER, "JavaProbe", null, "java/lang/Object", null);
    }

    private static void createMainMethod(ClassWriter cw) {
        MethodVisitor mv = cw.visitMethod(ACC_PUBLIC + ACC_STATIC, "main", "([Ljava/lang/String;)V", null, null);
        mv.visitCode();
        Label l0 = new Label();
        mv.visitLabel(l0);
        for (ProbedSystemProperty type : ProbedSystemProperty.values()) {
            if (type != ProbedSystemProperty.Z_ERROR) {
                dumpProperty(mv, type.getSystemPropertyKey());
            }
        }
        mv.visitInsn(RETURN);
        Label l3 = new Label();
        mv.visitLabel(l3);
        mv.visitLocalVariable("args", "[Ljava/lang/String;", null, l0, l3, 0);
        mv.visitMaxs(3, 1);
        mv.visitEnd();
    }

    private static void dumpProperty(MethodVisitor mv, String property) {
        mv.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
        mv.visitLdcInsn(property);
        mv.visitLdcInsn("unknown");
        mv.visitMethodInsn(INVOKESTATIC, "java/lang/System", "getProperty", "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;", false);
        mv.visitMethodInsn(INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/String;)V", false);
    }

    private static void createConstructor(ClassWriter cw) {
        MethodVisitor mv = cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
        mv.visitCode();
        Label l0 = new Label();
        mv.visitLabel(l0);
        mv.visitVarInsn(ALOAD, 0);
        mv.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        mv.visitInsn(RETURN);
        Label l1 = new Label();
        mv.visitLabel(l1);
        mv.visitLocalVariable("this", "LJavaProbe;", null, l0, l1, 0);
        mv.visitMaxs(1, 1);
        mv.visitEnd();
    }

}

/*
 * Copyright 2016 the original author or authors.
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
package org.gradle.jvm.toolchain.internal;

import org.gradle.api.GradleException;
import org.gradle.internal.ErroringAction;
import org.gradle.internal.IoActions;
import org.gradle.process.ExecResult;
import org.gradle.process.internal.ExecActionFactory;
import org.gradle.process.internal.JavaExecAction;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.util.EnumMap;

public class JavaInstallationProbe {
    private final ExecActionFactory factory;

    public enum Type {
        VERSION("java.version"),
        VENDOR("java.vendor"),
        ARCH("os.arch"),
        VM("java.vm.name");

        private final String sysProp;

        Type(String sysProp) {
            this.sysProp = sysProp;
        }

        private static EnumMap<Type, String> parseExecOutput(String probeResult) {
            EnumMap<Type, String> result = new EnumMap<Type, String>(Type.class);
            String[] split = probeResult.split(System.getProperty("line.separator"));
            for (Type type : Type.values()) {
                result.put(type, split[type.ordinal()]);
            }
            return result;
        }
    }

    public JavaInstallationProbe(ExecActionFactory factory) {
        this.factory = factory;
    }

    public EnumMap<Type, String> getMetadata(File jdkPath) {
        JavaExecAction exec = factory.newJavaExecAction();
        exec.executable(javaExe(jdkPath));
        writeProbe(exec.getWorkingDir());
        exec.setMain(JavaProbe.CLASSNAME);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        exec.setStandardOutput(baos);
        exec.setErrorOutput(new ByteArrayOutputStream());
        exec.setIgnoreExitValue(true);
        ExecResult result = exec.execute();
        int exitValue = result.getExitValue();
        if (exitValue == 0) {
            return Type.parseExecOutput(baos.toString());
        }
        return null;
    }

    private static void writeProbe(File workingDir) {
        File probeFile = new File(workingDir, JavaProbe.CLASSNAME + ".class");
        try {
            IoActions.withResource(new FileOutputStream(probeFile), new ErroringAction<FileOutputStream>() {
                @Override
                protected void doExecute(FileOutputStream thing) throws Exception {
                    thing.write(JavaProbe.dump());
                }
            });
        } catch (FileNotFoundException e) {
            throw new GradleException("Unable to write Java probe file", e);
        }
    }

    private static File javaExe(File jdkPath) {
        return new File(new File(jdkPath, "bin"), "java");
    }


    /**
     * This is the ASM version of a probe class that is the equivalent of the following source code:
     *
     * <code>
     * public static class Probe {
     *    public static void main(String[] args) {
     *      System.out.println(System.getProperty("java.version", "unknown"));
     *      System.out.println(System.getProperty("java.vendor", "unknown"));
     *   }
     * }
     * </code>
     *
     * We're using ASM because we need to generate a class which bytecode level is compatible with the lowest JDK version supported (1.1),
     * while being practical to add to classpath when executing the probe. You can add new system properties to be probed just by changing
     * the {@link Type} enum.
     *
     */
    private static class JavaProbe implements Opcodes {

        public static final String CLASSNAME = "JavaProbe";

        public static byte[] dump() throws Exception {

            ClassWriter cw = new ClassWriter(0);
            createClassHeader(cw);
            createConstructor(cw);
            createMainMethod(cw);

            cw.visitEnd();

            return cw.toByteArray();
        }

        private static void createClassHeader(ClassWriter cw) {
            cw.visit(V1_1, ACC_PUBLIC + ACC_SUPER, CLASSNAME, null, "java/lang/Object", null);
        }

        private static void createMainMethod(ClassWriter cw) {
            MethodVisitor mv = cw.visitMethod(ACC_PUBLIC + ACC_STATIC, "main", "([Ljava/lang/String;)V", null, null);
            mv.visitCode();
            Label l0 = new Label();
            mv.visitLabel(l0);
            for (Type type : Type.values()) {
                dumpProperty(mv, type.sysProp);
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
            MethodVisitor mv;
            mv = cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
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

}

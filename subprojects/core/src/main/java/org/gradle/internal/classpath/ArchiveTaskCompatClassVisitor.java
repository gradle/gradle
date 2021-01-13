/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.internal.classpath;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

import static org.objectweb.asm.Opcodes.ACC_PRIVATE;
import static org.objectweb.asm.Opcodes.ACC_STATIC;
import static org.objectweb.asm.Opcodes.ALOAD;
import static org.objectweb.asm.Opcodes.ARETURN;
import static org.objectweb.asm.Opcodes.ASM7;
import static org.objectweb.asm.Opcodes.CHECKCAST;
import static org.objectweb.asm.Opcodes.INVOKEINTERFACE;
import static org.objectweb.asm.Opcodes.INVOKESTATIC;
import static org.objectweb.asm.Opcodes.INVOKEVIRTUAL;
import static org.objectweb.asm.Opcodes.POP;
import static org.objectweb.asm.Opcodes.RETURN;

/**
 * Reroutes invocations from methods no longer available in AbstractArchiveTask to static bridge methods.
 *
 * <p>The goal of the transformer is to ensure that existing plugins still work with newer Gradle versions, even if they use methods that are no longer part of the API</p>
 *
 * <p>The bridge methods are private static methods contributed to the call site. As an example, consider the following plugin code snippet.
 *
 * <pre>
 * void configTask(Jar jar) {
 *     jar.setArchiveName("custom-archive");
 * }
 * </pre>
 *
 * The transformed bytecode will look like this:
 *
 * <pre>
 * void configTask(Jar jar) {
 *     gradleCompatSetArchiveName(jar, "custom-archive");
 * }
 *
 * private static gradleCompatSetArchiveName(AbstractArchiveTask task, String name) {
 *      task.getArchiveFileName().set(name);
 * }
 * </pre>
 * </p>
 */
public class ArchiveTaskCompatClassVisitor extends ClassVisitor {

    private Set<RemovedMethod> missingMethods = new HashSet<>();
    private String className;

    public ArchiveTaskCompatClassVisitor(ClassVisitor classVisitor) {
        super(ASM7, classVisitor);
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
        MethodVisitor methodVisitor = super.visitMethod(access, name, descriptor, signature, exceptions);
        return new ArchiveTaskCompatMethodVisitor(this, methodVisitor);
    }

    @Override
    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        this.className = name;
        super.visit(version, access, name, signature, superName, interfaces);
    }

    @Override
    public void visitEnd() {
        for (RemovedMethod m : missingMethods) {
            m.injectBridgeMethod(cv);
        }
        super.visitEnd();
    }

    private void injectBridgeMethodFor(RemovedMethod m) {
        missingMethods.add(m);
    }

    private static class ArchiveTaskCompatMethodVisitor extends MethodVisitor {

        private final ArchiveTaskCompatClassVisitor owner;
        private final String className;

        ArchiveTaskCompatMethodVisitor(ArchiveTaskCompatClassVisitor owner, MethodVisitor methodVisitor) {
            super(ASM7, methodVisitor);
            this.owner = owner;
            this.className = owner.className;
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
            if (opcode == Opcodes.INVOKEVIRTUAL) {
                RemovedMethod removedMethod = RemovedMethod.find(owner, name, descriptor);
                if (removedMethod != null) {
                    this.owner.injectBridgeMethodFor(removedMethod);
                    super.visitMethodInsn(INVOKESTATIC, className, removedMethod.getBridgeMethodName(), removedMethod.getBridgeMethodDescriptor(), false);
                    return;
                }
            }
            super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
        }
    }

    enum RemovedMethod {
        GET_ARCHIVE_NAME("getArchiveName", "()Ljava/lang/String;", "gradleCompatGetArchiveName", "(Lorg/gradle/api/tasks/bundling/AbstractArchiveTask;)Ljava/lang/String;", new Consumer<ClassVisitor>() {
            @Override
            public void accept(ClassVisitor cv) {
                MethodVisitor methodVisitor = cv.visitMethod(ACC_PRIVATE | ACC_STATIC, "gradleCompatGetArchiveName", "(Lorg/gradle/api/tasks/bundling/AbstractArchiveTask;)Ljava/lang/String;", null, null);
                methodVisitor.visitCode();
                Label label0 = new Label();
                methodVisitor.visitLabel(label0);
                methodVisitor.visitLineNumber(203, label0);
                methodVisitor.visitVarInsn(ALOAD, 0);
                methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "org/gradle/api/tasks/bundling/AbstractArchiveTask", "getArchiveFileName", "()Lorg/gradle/api/provider/Property;", false);
                methodVisitor.visitMethodInsn(INVOKEINTERFACE, "org/gradle/api/provider/Property", "get", "()Ljava/lang/Object;", true);
                methodVisitor.visitTypeInsn(CHECKCAST, "java/lang/String");
                methodVisitor.visitInsn(ARETURN);
                Label label1 = new Label();
                methodVisitor.visitLabel(label1);
                methodVisitor.visitLocalVariable("task", "Lorg/gradle/api/tasks/bundling/AbstractArchiveTask;", null, label0, label1, 0);
                methodVisitor.visitMaxs(1, 1);
                methodVisitor.visitEnd();
            }
        }),
        SET_ARCHIVE_NAME("setArchiveName", "(Ljava/lang/String;)V", "gradleCompatSetArchiveName", "(Lorg/gradle/api/tasks/bundling/AbstractArchiveTask;Ljava/lang/String;)V",  new Consumer<ClassVisitor>() {
            @Override
            public void accept(ClassVisitor cv) {
                MethodVisitor methodVisitor = cv.visitMethod(ACC_PRIVATE | ACC_STATIC, "gradleCompatSetArchiveName", "(Lorg/gradle/api/tasks/bundling/AbstractArchiveTask;Ljava/lang/String;)V", null, null);
                methodVisitor.visitCode();
                Label label0 = new Label();
                methodVisitor.visitLabel(label0);
                methodVisitor.visitLineNumber(38, label0);
                methodVisitor.visitVarInsn(ALOAD, 0);
                methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "org/gradle/api/tasks/bundling/AbstractArchiveTask", "getArchiveFileName", "()Lorg/gradle/api/provider/Property;", false);
                methodVisitor.visitVarInsn(ALOAD, 1);
                methodVisitor.visitMethodInsn(INVOKEINTERFACE, "org/gradle/api/provider/Property", "convention", "(Ljava/lang/Object;)Lorg/gradle/api/provider/Property;", true);
                methodVisitor.visitInsn(POP);
                Label label1 = new Label();
                methodVisitor.visitLabel(label1);
                methodVisitor.visitLineNumber(39, label1);
                methodVisitor.visitVarInsn(ALOAD, 0);
                methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "org/gradle/api/tasks/bundling/AbstractArchiveTask", "getArchiveFileName", "()Lorg/gradle/api/provider/Property;", false);
                methodVisitor.visitVarInsn(ALOAD, 1);
                methodVisitor.visitMethodInsn(INVOKEINTERFACE, "org/gradle/api/provider/Property", "set", "(Ljava/lang/Object;)V", true);
                Label label2 = new Label();
                methodVisitor.visitLabel(label2);
                methodVisitor.visitLineNumber(40, label2);
                methodVisitor.visitInsn(RETURN);
                Label label3 = new Label();
                methodVisitor.visitLabel(label3);
                methodVisitor.visitLocalVariable("task", "Lorg/gradle/api/tasks/bundling/AbstractArchiveTask;", null, label0, label3, 0);
                methodVisitor.visitLocalVariable("name", "Ljava/lang/String;", null, label0, label3, 1);
                methodVisitor.visitMaxs(2, 2);
            }
        }),
        GET_ARCHIVE_PATH("getArchivePath", "()Ljava/io/File;", "gradleCompatGetArchivePath", "(Lorg/gradle/api/tasks/bundling/AbstractArchiveTask;)Ljava/io/File;", new Consumer<ClassVisitor>() {
            @Override
            public void accept(ClassVisitor cv) {
                MethodVisitor methodVisitor = cv.visitMethod(ACC_PRIVATE | ACC_STATIC, "gradleCompatGetArchivePath", "(Lorg/gradle/api/tasks/bundling/AbstractArchiveTask;)Ljava/io/File;", null, null);
                methodVisitor.visitCode();
                Label label0 = new Label();
                methodVisitor.visitLabel(label0);
                methodVisitor.visitLineNumber(212, label0);
                methodVisitor.visitVarInsn(ALOAD, 0);
                methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "org/gradle/api/tasks/bundling/AbstractArchiveTask", "getArchiveFile", "()Lorg/gradle/api/provider/Provider;", false);
                methodVisitor.visitMethodInsn(INVOKEINTERFACE, "org/gradle/api/provider/Provider", "get", "()Ljava/lang/Object;", true);
                methodVisitor.visitTypeInsn(CHECKCAST, "org/gradle/api/file/RegularFile");
                methodVisitor.visitMethodInsn(INVOKEINTERFACE, "org/gradle/api/file/RegularFile", "getAsFile", "()Ljava/io/File;", true);
                methodVisitor.visitInsn(ARETURN);
                Label label1 = new Label();
                methodVisitor.visitLabel(label1);
                methodVisitor.visitLocalVariable("task", "Lorg/gradle/api/tasks/bundling/AbstractArchiveTask;", null, label0, label1, 0);
                methodVisitor.visitMaxs(1, 1);
                methodVisitor.visitEnd();
            }
        }),
        GET_DESTINATION_DIR("getDestinationDir", "()Ljava/io/File;", "gradleCompatGetDestinationDir", "(Lorg/gradle/api/tasks/bundling/AbstractArchiveTask;)Ljava/io/File;", new Consumer<ClassVisitor>() {
            @Override
            public void accept(ClassVisitor cv) {
                MethodVisitor methodVisitor = cv.visitMethod(ACC_PRIVATE | ACC_STATIC, "gradleCompatGetDestinationDir", "(Lorg/gradle/api/tasks/bundling/AbstractArchiveTask;)Ljava/io/File;", null, null);
                methodVisitor.visitCode();
                Label label0 = new Label();
                methodVisitor.visitLabel(label0);
                methodVisitor.visitLineNumber(216, label0);
                methodVisitor.visitVarInsn(ALOAD, 0);
                methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "org/gradle/api/tasks/bundling/AbstractArchiveTask", "getDestinationDirectory", "()Lorg/gradle/api/file/DirectoryProperty;", false);
                methodVisitor.visitMethodInsn(INVOKEINTERFACE, "org/gradle/api/file/DirectoryProperty", "get", "()Ljava/lang/Object;", true);
                methodVisitor.visitTypeInsn(CHECKCAST, "org/gradle/api/file/Directory");
                methodVisitor.visitMethodInsn(INVOKEINTERFACE, "org/gradle/api/file/Directory", "getAsFile", "()Ljava/io/File;", true);
                methodVisitor.visitInsn(ARETURN);
                Label label1 = new Label();
                methodVisitor.visitLabel(label1);
                methodVisitor.visitLocalVariable("task", "Lorg/gradle/api/tasks/bundling/AbstractArchiveTask;", null, label0, label1, 0);
                methodVisitor.visitMaxs(1, 1);
                methodVisitor.visitEnd();
            }
        }),
        SET_DESTINATION_DIR("setDestinationDir", "(Ljava/io/File;)V", "gradleCompatSetDestinationDir", "(Lorg/gradle/api/tasks/bundling/AbstractArchiveTask;Ljava/io/File;)V", new Consumer<ClassVisitor>() {
            @Override
            public void accept(ClassVisitor cv) {
                MethodVisitor methodVisitor = cv.visitMethod(ACC_PRIVATE | ACC_STATIC, "gradleCompatSetDestinationDir", "(Lorg/gradle/api/tasks/bundling/AbstractArchiveTask;Ljava/io/File;)V", null, null);
                methodVisitor.visitCode();
                Label label0 = new Label();
                methodVisitor.visitLabel(label0);
                methodVisitor.visitLineNumber(220, label0);
                methodVisitor.visitVarInsn(ALOAD, 0);
                methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "org/gradle/api/tasks/bundling/AbstractArchiveTask", "getDestinationDirectory", "()Lorg/gradle/api/file/DirectoryProperty;", false);
                methodVisitor.visitVarInsn(ALOAD, 0);
                methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "org/gradle/api/tasks/bundling/AbstractArchiveTask", "getProject", "()Lorg/gradle/api/Project;", false);
                methodVisitor.visitVarInsn(ALOAD, 1);
                methodVisitor.visitMethodInsn(INVOKEINTERFACE, "org/gradle/api/Project", "file", "(Ljava/lang/Object;)Ljava/io/File;", true);
                methodVisitor.visitMethodInsn(INVOKEINTERFACE, "org/gradle/api/file/DirectoryProperty", "set", "(Ljava/io/File;)V", true);
                Label label1 = new Label();
                methodVisitor.visitLabel(label1);
                methodVisitor.visitLineNumber(221, label1);
                methodVisitor.visitInsn(RETURN);
                Label label2 = new Label();
                methodVisitor.visitLabel(label2);
                methodVisitor.visitLocalVariable("task", "Lorg/gradle/api/tasks/bundling/AbstractArchiveTask;", null, label0, label2, 0);
                methodVisitor.visitLocalVariable("f", "Ljava/io/File;", null, label0, label2, 1);
                methodVisitor.visitMaxs(3, 2);
                methodVisitor.visitEnd();
            }
        }),
        GET_BASE_NAME("getBaseName", "()Ljava/lang/String;", "gradleCompatGetBaseName", "(Lorg/gradle/api/tasks/bundling/AbstractArchiveTask;)Ljava/lang/String;", new Consumer<ClassVisitor>() {
            @Override
            public void accept(ClassVisitor cv) {
                MethodVisitor methodVisitor = cv.visitMethod(ACC_PRIVATE | ACC_STATIC, "gradleCompatGetBaseName", "(Lorg/gradle/api/tasks/bundling/AbstractArchiveTask;)Ljava/lang/String;", null, null);
                methodVisitor.visitCode();
                Label label0 = new Label();
                methodVisitor.visitLabel(label0);
                methodVisitor.visitLineNumber(224, label0);
                methodVisitor.visitVarInsn(ALOAD, 0);
                methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "org/gradle/api/tasks/bundling/AbstractArchiveTask", "getArchiveBaseName", "()Lorg/gradle/api/provider/Property;", false);
                methodVisitor.visitMethodInsn(INVOKEINTERFACE, "org/gradle/api/provider/Property", "getOrNull", "()Ljava/lang/Object;", true);
                methodVisitor.visitTypeInsn(CHECKCAST, "java/lang/String");
                methodVisitor.visitInsn(ARETURN);
                Label label1 = new Label();
                methodVisitor.visitLabel(label1);
                methodVisitor.visitLocalVariable("task", "Lorg/gradle/api/tasks/bundling/AbstractArchiveTask;", null, label0, label1, 0);
                methodVisitor.visitMaxs(1, 1);
                methodVisitor.visitEnd();
            }
        }),
        SET_BASE_NAME("setBaseName", "(Ljava/lang/String;)V", "gradleCompatSetBaseName", "(Lorg/gradle/api/tasks/bundling/AbstractArchiveTask;Ljava/lang/String;)V", new Consumer<ClassVisitor>() {
            @Override
            public void accept(ClassVisitor cv) {
                MethodVisitor methodVisitor = cv.visitMethod(ACC_PRIVATE | ACC_STATIC, "gradleCompatSetBaseName", "(Lorg/gradle/api/tasks/bundling/AbstractArchiveTask;Ljava/lang/String;)V", null, null);
                methodVisitor.visitCode();
                Label label0 = new Label();
                methodVisitor.visitLabel(label0);
                methodVisitor.visitLineNumber(228, label0);
                methodVisitor.visitVarInsn(ALOAD, 0);
                methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "org/gradle/api/tasks/bundling/AbstractArchiveTask", "getArchiveBaseName", "()Lorg/gradle/api/provider/Property;", false);
                methodVisitor.visitVarInsn(ALOAD, 1);
                methodVisitor.visitMethodInsn(INVOKEINTERFACE, "org/gradle/api/provider/Property", "convention", "(Ljava/lang/Object;)Lorg/gradle/api/provider/Property;", true);
                methodVisitor.visitInsn(POP);
                Label label1 = new Label();
                methodVisitor.visitLabel(label1);
                methodVisitor.visitLineNumber(229, label1);
                methodVisitor.visitVarInsn(ALOAD, 0);
                methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "org/gradle/api/tasks/bundling/AbstractArchiveTask", "getArchiveBaseName", "()Lorg/gradle/api/provider/Property;", false);
                methodVisitor.visitVarInsn(ALOAD, 1);
                methodVisitor.visitMethodInsn(INVOKEINTERFACE, "org/gradle/api/provider/Property", "set", "(Ljava/lang/Object;)V", true);
                Label label2 = new Label();
                methodVisitor.visitLabel(label2);
                methodVisitor.visitLineNumber(230, label2);
                methodVisitor.visitInsn(RETURN);
                Label label3 = new Label();
                methodVisitor.visitLabel(label3);
                methodVisitor.visitLocalVariable("task", "Lorg/gradle/api/tasks/bundling/AbstractArchiveTask;", null, label0, label3, 0);
                methodVisitor.visitLocalVariable("name", "Ljava/lang/String;", null, label0, label3, 1);
                methodVisitor.visitMaxs(2, 2);
                methodVisitor.visitEnd();
            }
        }),
        GET_APPENDIX("getAppendix", "()Ljava/lang/String;", "gradleCompatGetAppendix", "(Lorg/gradle/api/tasks/bundling/AbstractArchiveTask;)Ljava/lang/String;", new Consumer<ClassVisitor>() {
            @Override
            public void accept(ClassVisitor cv) {
                MethodVisitor methodVisitor = cv.visitMethod(ACC_PRIVATE | ACC_STATIC, "gradleCompatGetAppendix", "(Lorg/gradle/api/tasks/bundling/AbstractArchiveTask;)Ljava/lang/String;", null, null);
                methodVisitor.visitCode();
                Label label0 = new Label();
                methodVisitor.visitLabel(label0);
                methodVisitor.visitLineNumber(233, label0);
                methodVisitor.visitVarInsn(ALOAD, 0);
                methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "org/gradle/api/tasks/bundling/AbstractArchiveTask", "getArchiveAppendix", "()Lorg/gradle/api/provider/Property;", false);
                methodVisitor.visitMethodInsn(INVOKEINTERFACE, "org/gradle/api/provider/Property", "getOrNull", "()Ljava/lang/Object;", true);
                methodVisitor.visitTypeInsn(CHECKCAST, "java/lang/String");
                methodVisitor.visitInsn(ARETURN);
                Label label1 = new Label();
                methodVisitor.visitLabel(label1);
                methodVisitor.visitLocalVariable("task", "Lorg/gradle/api/tasks/bundling/AbstractArchiveTask;", null, label0, label1, 0);
                methodVisitor.visitMaxs(1, 1);
                methodVisitor.visitEnd();
            }
        }),
        SET_APPENDIX("setAppendix", "(Ljava/lang/String;)V", "gradleCompatSetAppendix", "(Lorg/gradle/api/tasks/bundling/AbstractArchiveTask;Ljava/lang/String;)V", new Consumer<ClassVisitor>() {
            @Override
            public void accept(ClassVisitor cv) {
                MethodVisitor methodVisitor = cv.visitMethod(ACC_PRIVATE | ACC_STATIC, "gradleCompatSetAppendix", "(Lorg/gradle/api/tasks/bundling/AbstractArchiveTask;Ljava/lang/String;)V", null, null);
                methodVisitor.visitCode();
                Label label0 = new Label();
                methodVisitor.visitLabel(label0);
                methodVisitor.visitLineNumber(237, label0);
                methodVisitor.visitVarInsn(ALOAD, 0);
                methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "org/gradle/api/tasks/bundling/AbstractArchiveTask", "getArchiveAppendix", "()Lorg/gradle/api/provider/Property;", false);
                methodVisitor.visitVarInsn(ALOAD, 1);
                methodVisitor.visitMethodInsn(INVOKEINTERFACE, "org/gradle/api/provider/Property", "convention", "(Ljava/lang/Object;)Lorg/gradle/api/provider/Property;", true);
                methodVisitor.visitInsn(POP);
                Label label1 = new Label();
                methodVisitor.visitLabel(label1);
                methodVisitor.visitLineNumber(238, label1);
                methodVisitor.visitVarInsn(ALOAD, 0);
                methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "org/gradle/api/tasks/bundling/AbstractArchiveTask", "getArchiveAppendix", "()Lorg/gradle/api/provider/Property;", false);
                methodVisitor.visitVarInsn(ALOAD, 1);
                methodVisitor.visitMethodInsn(INVOKEINTERFACE, "org/gradle/api/provider/Property", "set", "(Ljava/lang/Object;)V", true);
                Label label2 = new Label();
                methodVisitor.visitLabel(label2);
                methodVisitor.visitLineNumber(239, label2);
                methodVisitor.visitInsn(RETURN);
                Label label3 = new Label();
                methodVisitor.visitLabel(label3);
                methodVisitor.visitLocalVariable("task", "Lorg/gradle/api/tasks/bundling/AbstractArchiveTask;", null, label0, label3, 0);
                methodVisitor.visitLocalVariable("name", "Ljava/lang/String;", null, label0, label3, 1);
                methodVisitor.visitMaxs(2, 2);
                methodVisitor.visitEnd();
            }
        }),
        GET_VERSION("getVersion", "()Ljava/lang/String;", "gradleCompatGetVersion", "(Lorg/gradle/api/tasks/bundling/AbstractArchiveTask;)Ljava/lang/String;", new Consumer<ClassVisitor>() {
            @Override
            public void accept(ClassVisitor cv) {
                MethodVisitor methodVisitor = cv.visitMethod(ACC_PRIVATE | ACC_STATIC, "gradleCompatGetVersion", "(Lorg/gradle/api/tasks/bundling/AbstractArchiveTask;)Ljava/lang/String;", null, null);
                methodVisitor.visitCode();
                Label label0 = new Label();
                methodVisitor.visitLabel(label0);
                methodVisitor.visitLineNumber(242, label0);
                methodVisitor.visitVarInsn(ALOAD, 0);
                methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "org/gradle/api/tasks/bundling/AbstractArchiveTask", "getArchiveVersion", "()Lorg/gradle/api/provider/Property;", false);
                methodVisitor.visitMethodInsn(INVOKEINTERFACE, "org/gradle/api/provider/Property", "getOrNull", "()Ljava/lang/Object;", true);
                methodVisitor.visitTypeInsn(CHECKCAST, "java/lang/String");
                methodVisitor.visitInsn(ARETURN);
                Label label1 = new Label();
                methodVisitor.visitLabel(label1);
                methodVisitor.visitLocalVariable("task", "Lorg/gradle/api/tasks/bundling/AbstractArchiveTask;", null, label0, label1, 0);
                methodVisitor.visitMaxs(1, 1);
                methodVisitor.visitEnd();
            }
        }),
        SET_VERSION("setVersion", "(Ljava/lang/String;)V", "gradleCompatSetVersion", "(Lorg/gradle/api/tasks/bundling/AbstractArchiveTask;Ljava/lang/String;)V", new Consumer<ClassVisitor>() {
            @Override
            public void accept(ClassVisitor cv) {
                MethodVisitor methodVisitor = cv.visitMethod(ACC_PRIVATE | ACC_STATIC, "gradleCompatSetVersion", "(Lorg/gradle/api/tasks/bundling/AbstractArchiveTask;Ljava/lang/String;)V", null, null);
                methodVisitor.visitCode();
                Label label0 = new Label();
                methodVisitor.visitLabel(label0);
                methodVisitor.visitLineNumber(246, label0);
                methodVisitor.visitVarInsn(ALOAD, 0);
                methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "org/gradle/api/tasks/bundling/AbstractArchiveTask", "getArchiveVersion", "()Lorg/gradle/api/provider/Property;", false);
                methodVisitor.visitVarInsn(ALOAD, 1);
                methodVisitor.visitMethodInsn(INVOKEINTERFACE, "org/gradle/api/provider/Property", "convention", "(Ljava/lang/Object;)Lorg/gradle/api/provider/Property;", true);
                methodVisitor.visitInsn(POP);
                Label label1 = new Label();
                methodVisitor.visitLabel(label1);
                methodVisitor.visitLineNumber(247, label1);
                methodVisitor.visitVarInsn(ALOAD, 0);
                methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "org/gradle/api/tasks/bundling/AbstractArchiveTask", "getArchiveVersion", "()Lorg/gradle/api/provider/Property;", false);
                methodVisitor.visitVarInsn(ALOAD, 1);
                methodVisitor.visitMethodInsn(INVOKEINTERFACE, "org/gradle/api/provider/Property", "set", "(Ljava/lang/Object;)V", true);
                Label label2 = new Label();
                methodVisitor.visitLabel(label2);
                methodVisitor.visitLineNumber(248, label2);
                methodVisitor.visitInsn(RETURN);
                Label label3 = new Label();
                methodVisitor.visitLabel(label3);
                methodVisitor.visitLocalVariable("task", "Lorg/gradle/api/tasks/bundling/AbstractArchiveTask;", null, label0, label3, 0);
                methodVisitor.visitLocalVariable("name", "Ljava/lang/String;", null, label0, label3, 1);
                methodVisitor.visitMaxs(2, 2);
                methodVisitor.visitEnd();
            }
        }),
        GET_EXTENSION("getExtension", "()Ljava/lang/String;", "gradleCompatGetExtension", "(Lorg/gradle/api/tasks/bundling/AbstractArchiveTask;)Ljava/lang/String;", new Consumer<ClassVisitor>() {
            @Override
            public void accept(ClassVisitor cv) {
                MethodVisitor methodVisitor = cv.visitMethod(ACC_PRIVATE | ACC_STATIC, "gradleCompatGetExtension", "(Lorg/gradle/api/tasks/bundling/AbstractArchiveTask;)Ljava/lang/String;", null, null);
                methodVisitor.visitCode();
                Label label0 = new Label();
                methodVisitor.visitLabel(label0);
                methodVisitor.visitLineNumber(251, label0);
                methodVisitor.visitVarInsn(ALOAD, 0);
                methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "org/gradle/api/tasks/bundling/AbstractArchiveTask", "getArchiveExtension", "()Lorg/gradle/api/provider/Property;", false);
                methodVisitor.visitMethodInsn(INVOKEINTERFACE, "org/gradle/api/provider/Property", "getOrNull", "()Ljava/lang/Object;", true);
                methodVisitor.visitTypeInsn(CHECKCAST, "java/lang/String");
                methodVisitor.visitInsn(ARETURN);
                Label label1 = new Label();
                methodVisitor.visitLabel(label1);
                methodVisitor.visitLocalVariable("task", "Lorg/gradle/api/tasks/bundling/AbstractArchiveTask;", null, label0, label1, 0);
                methodVisitor.visitMaxs(1, 1);
                methodVisitor.visitEnd();
            }
        }),
        SET_EXTENSION("setExtension", "(Ljava/lang/String;)V", "gradleCompatSetExtension", "(Lorg/gradle/api/tasks/bundling/AbstractArchiveTask;Ljava/lang/String;)V", new Consumer<ClassVisitor>() {
            @Override
            public void accept(ClassVisitor cv) {
                MethodVisitor methodVisitor = cv.visitMethod(ACC_PRIVATE | ACC_STATIC, "gradleCompatSetExtension", "(Lorg/gradle/api/tasks/bundling/AbstractArchiveTask;Ljava/lang/String;)V", null, null);
                methodVisitor.visitCode();
                Label label0 = new Label();
                methodVisitor.visitLabel(label0);
                methodVisitor.visitLineNumber(255, label0);
                methodVisitor.visitVarInsn(ALOAD, 0);
                methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "org/gradle/api/tasks/bundling/AbstractArchiveTask", "getArchiveExtension", "()Lorg/gradle/api/provider/Property;", false);
                methodVisitor.visitVarInsn(ALOAD, 1);
                methodVisitor.visitMethodInsn(INVOKEINTERFACE, "org/gradle/api/provider/Property", "convention", "(Ljava/lang/Object;)Lorg/gradle/api/provider/Property;", true);
                methodVisitor.visitInsn(POP);
                Label label1 = new Label();
                methodVisitor.visitLabel(label1);
                methodVisitor.visitLineNumber(256, label1);
                methodVisitor.visitVarInsn(ALOAD, 0);
                methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "org/gradle/api/tasks/bundling/AbstractArchiveTask", "getArchiveExtension", "()Lorg/gradle/api/provider/Property;", false);
                methodVisitor.visitVarInsn(ALOAD, 1);
                methodVisitor.visitMethodInsn(INVOKEINTERFACE, "org/gradle/api/provider/Property", "set", "(Ljava/lang/Object;)V", true);
                Label label2 = new Label();
                methodVisitor.visitLabel(label2);
                methodVisitor.visitLineNumber(257, label2);
                methodVisitor.visitInsn(RETURN);
                Label label3 = new Label();
                methodVisitor.visitLabel(label3);
                methodVisitor.visitLocalVariable("task", "Lorg/gradle/api/tasks/bundling/AbstractArchiveTask;", null, label0, label3, 0);
                methodVisitor.visitLocalVariable("name", "Ljava/lang/String;", null, label0, label3, 1);
                methodVisitor.visitMaxs(2, 2);
                methodVisitor.visitEnd();
            }
        }),
        GET_CLASSIFIER("getClassifier", "()Ljava/lang/String;", "gradleCompatGetClassifier", "(Lorg/gradle/api/tasks/bundling/AbstractArchiveTask;)Ljava/lang/String;", new Consumer<ClassVisitor>() {
            @Override
            public void accept(ClassVisitor cv) {
                MethodVisitor methodVisitor = cv.visitMethod(ACC_PRIVATE | ACC_STATIC, "gradleCompatGetClassifier", "(Lorg/gradle/api/tasks/bundling/AbstractArchiveTask;)Ljava/lang/String;", null, null);
                methodVisitor.visitCode();
                Label label0 = new Label();
                methodVisitor.visitLabel(label0);
                methodVisitor.visitLineNumber(260, label0);
                methodVisitor.visitVarInsn(ALOAD, 0);
                methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "org/gradle/api/tasks/bundling/AbstractArchiveTask", "getArchiveClassifier", "()Lorg/gradle/api/provider/Property;", false);
                methodVisitor.visitMethodInsn(INVOKEINTERFACE, "org/gradle/api/provider/Property", "getOrNull", "()Ljava/lang/Object;", true);
                methodVisitor.visitTypeInsn(CHECKCAST, "java/lang/String");
                methodVisitor.visitInsn(ARETURN);
                Label label1 = new Label();
                methodVisitor.visitLabel(label1);
                methodVisitor.visitLocalVariable("task", "Lorg/gradle/api/tasks/bundling/AbstractArchiveTask;", null, label0, label1, 0);
                methodVisitor.visitMaxs(1, 1);
                methodVisitor.visitEnd();
            }
        }),
        SET_CLASSIFIER("setClassifier", "(Ljava/lang/String;)V", "gradleCompatSetClassifier", "(Lorg/gradle/api/tasks/bundling/AbstractArchiveTask;Ljava/lang/String;)V", new Consumer<ClassVisitor>() {
            @Override
            public void accept(ClassVisitor cv) {
                MethodVisitor methodVisitor = cv.visitMethod(ACC_PRIVATE | ACC_STATIC, "gradleCompatSetClassifier", "(Lorg/gradle/api/tasks/bundling/AbstractArchiveTask;Ljava/lang/String;)V", null, null);
                methodVisitor.visitCode();
                Label label0 = new Label();
                methodVisitor.visitLabel(label0);
                methodVisitor.visitLineNumber(264, label0);
                methodVisitor.visitVarInsn(ALOAD, 0);
                methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "org/gradle/api/tasks/bundling/AbstractArchiveTask", "getArchiveClassifier", "()Lorg/gradle/api/provider/Property;", false);
                methodVisitor.visitVarInsn(ALOAD, 1);
                methodVisitor.visitMethodInsn(INVOKEINTERFACE, "org/gradle/api/provider/Property", "convention", "(Ljava/lang/Object;)Lorg/gradle/api/provider/Property;", true);
                methodVisitor.visitInsn(POP);
                Label label1 = new Label();
                methodVisitor.visitLabel(label1);
                methodVisitor.visitLineNumber(265, label1);
                methodVisitor.visitVarInsn(ALOAD, 0);
                methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "org/gradle/api/tasks/bundling/AbstractArchiveTask", "getArchiveClassifier", "()Lorg/gradle/api/provider/Property;", false);
                methodVisitor.visitVarInsn(ALOAD, 1);
                methodVisitor.visitMethodInsn(INVOKEINTERFACE, "org/gradle/api/provider/Property", "set", "(Ljava/lang/Object;)V", true);
                Label label2 = new Label();
                methodVisitor.visitLabel(label2);
                methodVisitor.visitLineNumber(266, label2);
                methodVisitor.visitInsn(RETURN);
                Label label3 = new Label();
                methodVisitor.visitLabel(label3);
                methodVisitor.visitLocalVariable("task", "Lorg/gradle/api/tasks/bundling/AbstractArchiveTask;", null, label0, label3, 0);
                methodVisitor.visitLocalVariable("name", "Ljava/lang/String;", null, label0, label3, 1);
                methodVisitor.visitMaxs(2, 2);
                methodVisitor.visitEnd();
            }
        });

        private final String name;
        private final String descriptor;
        private final String bridgeMethodName;
        private final String bridgeMethodDescriptor;
        private final Consumer<ClassVisitor> bridgeMethodBody;

        RemovedMethod(String name, String descriptor, String bridgeMethodName, String bridgeMethodDescriptor, Consumer<ClassVisitor> bridgeMethodBody) {
            this.name = name;
            this.descriptor = descriptor;
            this.bridgeMethodName = bridgeMethodName;
            this.bridgeMethodDescriptor = bridgeMethodDescriptor;
            this.bridgeMethodBody = bridgeMethodBody;
        }

        String getBridgeMethodName() {
            return bridgeMethodName;
        }

        String getBridgeMethodDescriptor() {
            return bridgeMethodDescriptor;
        }

        static RemovedMethod find(String owner, String name, String descriptor) {
            if (!isArchiveTaskType(owner)) {
                return null;
            }
            for (RemovedMethod m : RemovedMethod.values()) {
                if (name.equals(m.name) && descriptor.equals(m.descriptor)) {
                    return m;
                }
            }
            return null;
        }

        private static boolean isArchiveTaskType(String typeName) {
            return typeName.equals("org/gradle/api/tasks/bundling/AbstractArchiveTask") ||
                typeName.equals("org/gradle/api/tasks/bundling/Tar") ||
                typeName.equals("org/gradle/api/tasks/bundling/Jar") ||
                typeName.equals("org/gradle/api/tasks/bundling/Zip") ||
                typeName.equals("org/gradle/api/tasks/bundling/War") ||
                typeName.equals("org/gradle/plugins/ear/Ear") ||
                typeName.equals("org/gradle/jvm/tasks/Jar");
        }

        void injectBridgeMethod(ClassVisitor cv) {
            bridgeMethodBody.accept(cv);
        }
    }
}

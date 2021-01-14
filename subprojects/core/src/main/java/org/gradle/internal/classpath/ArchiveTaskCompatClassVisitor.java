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

import org.apache.commons.lang.StringUtils;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.io.File;
import java.util.HashSet;
import java.util.Set;

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
import static org.objectweb.asm.Type.getMethodDescriptor;
import static org.objectweb.asm.Type.getType;

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
    private static final Type STRING_TYPE = getType(String.class);
    private static final Type FILE_TYPE = getType(File.class);
    private static final String ARCHIVE_TASK_NAME = "org/gradle/api/tasks/bundling/AbstractArchiveTask";
    private static final Type ARCHIVE_TASK_TYPE = getType("L" + ARCHIVE_TASK_NAME + ";");
    private static final String ARCHIVE_TASK_DESCRIPTOR = ARCHIVE_TASK_TYPE.getDescriptor();
    private static final String RETURN_STRING = getMethodDescriptor(STRING_TYPE);
    private static final String RETURN_FILE = getMethodDescriptor(FILE_TYPE);
    private static final String RETURN_VOID_FROM_FILE = getMethodDescriptor(Type.VOID_TYPE, FILE_TYPE);
    private static final String RETURN_VOID_FROM_STRING = getMethodDescriptor(Type.VOID_TYPE, STRING_TYPE);
    private static final String RETURN_VOID_FROM_ARCHIVE_TASK_AND_STRING = getMethodDescriptor(Type.VOID_TYPE, ARCHIVE_TASK_TYPE, STRING_TYPE);
    private static final String RETURN_VOID_FROM_ARCHIVE_TASK_AND_FILE = getMethodDescriptor(Type.VOID_TYPE, ARCHIVE_TASK_TYPE, FILE_TYPE);
    private static final String RETURN_STRING_FROM_ARCHIVE_TASK = getMethodDescriptor(STRING_TYPE, ARCHIVE_TASK_TYPE);
    private static final String RETURN_FILE_FROM_ARCHIVE_TASK = getMethodDescriptor(FILE_TYPE, ARCHIVE_TASK_TYPE);

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

    static abstract class BridgeMethodImplementation {

        private BridgeMethodImplementation() {
        }

        abstract void injectImplementation(String bridgeMethodName, ClassVisitor cv);

        static BridgeMethodImplementation forStringPropertyGetter(final String methodNameReturningStringProperty) {
            return new BridgeMethodImplementation() {
                @Override
                void injectImplementation(String bridgeMethodName, ClassVisitor cv) {
                    MethodVisitor methodVisitor = cv.visitMethod(ACC_PRIVATE | ACC_STATIC, bridgeMethodName, RETURN_STRING_FROM_ARCHIVE_TASK, null, null);
                    methodVisitor.visitCode();
                    Label label0 = new Label();
                    methodVisitor.visitLabel(label0);
                    methodVisitor.visitVarInsn(ALOAD, 0);
                    methodVisitor.visitMethodInsn(INVOKEVIRTUAL, ARCHIVE_TASK_NAME, methodNameReturningStringProperty, "()Lorg/gradle/api/provider/Property;", false);
                    methodVisitor.visitMethodInsn(INVOKEINTERFACE, "org/gradle/api/provider/Property", "get", "()Ljava/lang/Object;", true);
                    methodVisitor.visitTypeInsn(CHECKCAST, "java/lang/String");
                    methodVisitor.visitInsn(ARETURN);
                    Label label1 = new Label();
                    methodVisitor.visitLabel(label1);
                    methodVisitor.visitLocalVariable("task", ARCHIVE_TASK_DESCRIPTOR, null, label0, label1, 0);
                    methodVisitor.visitMaxs(1, 1);
                    methodVisitor.visitEnd();
                }
            };
        }

        static BridgeMethodImplementation forStringPropertySetter(final String methodNameReturningStringProperty) {
            return new BridgeMethodImplementation() {
                @Override
                void injectImplementation(String bridgeMethodName, ClassVisitor cv) {
                    MethodVisitor methodVisitor = cv.visitMethod(ACC_PRIVATE | ACC_STATIC, bridgeMethodName, RETURN_VOID_FROM_ARCHIVE_TASK_AND_STRING, null, null);
                    methodVisitor.visitCode();
                    Label label0 = new Label();
                    methodVisitor.visitLabel(label0);
                    methodVisitor.visitVarInsn(ALOAD, 0);
                    methodVisitor.visitMethodInsn(INVOKEVIRTUAL, ARCHIVE_TASK_NAME, methodNameReturningStringProperty, "()Lorg/gradle/api/provider/Property;", false);
                    methodVisitor.visitVarInsn(ALOAD, 1);
                    methodVisitor.visitMethodInsn(INVOKEINTERFACE, "org/gradle/api/provider/Property", "convention", "(Ljava/lang/Object;)Lorg/gradle/api/provider/Property;", true);
                    methodVisitor.visitInsn(POP);
                    Label label1 = new Label();
                    methodVisitor.visitLabel(label1);
                    methodVisitor.visitVarInsn(ALOAD, 0);
                    methodVisitor.visitMethodInsn(INVOKEVIRTUAL, ARCHIVE_TASK_NAME, methodNameReturningStringProperty, "()Lorg/gradle/api/provider/Property;", false);
                    methodVisitor.visitVarInsn(ALOAD, 1);
                    methodVisitor.visitMethodInsn(INVOKEINTERFACE, "org/gradle/api/provider/Property", "set", "(Ljava/lang/Object;)V", true);
                    Label label2 = new Label();
                    methodVisitor.visitLabel(label2);
                    methodVisitor.visitInsn(RETURN);
                    Label label3 = new Label();
                    methodVisitor.visitLabel(label3);
                    methodVisitor.visitLocalVariable("task", ARCHIVE_TASK_DESCRIPTOR, null, label0, label3, 0);
                    methodVisitor.visitLocalVariable("name", "Ljava/lang/String;", null, label0, label3, 1);
                    methodVisitor.visitMaxs(2, 2);
                    methodVisitor.visitEnd();
                }
            };
        }

        static BridgeMethodImplementation forGetArchivePath() {
            return new BridgeMethodImplementation() {
                @Override
                void injectImplementation(String bridgeMethodName, ClassVisitor cv) {
                    MethodVisitor methodVisitor = cv.visitMethod(ACC_PRIVATE | ACC_STATIC, bridgeMethodName, RETURN_FILE_FROM_ARCHIVE_TASK, null, null);
                    methodVisitor.visitCode();
                    Label label0 = new Label();
                    methodVisitor.visitLabel(label0);
                    methodVisitor.visitLineNumber(212, label0);
                    methodVisitor.visitVarInsn(ALOAD, 0);
                    methodVisitor.visitMethodInsn(INVOKEVIRTUAL, ARCHIVE_TASK_NAME, "getArchiveFile", "()Lorg/gradle/api/provider/Provider;", false);
                    methodVisitor.visitMethodInsn(INVOKEINTERFACE, "org/gradle/api/provider/Provider", "get", "()Ljava/lang/Object;", true);
                    methodVisitor.visitTypeInsn(CHECKCAST, "org/gradle/api/file/RegularFile");
                    methodVisitor.visitMethodInsn(INVOKEINTERFACE, "org/gradle/api/file/RegularFile", "getAsFile", "()Ljava/io/File;", true);
                    methodVisitor.visitInsn(ARETURN);
                    Label label1 = new Label();
                    methodVisitor.visitLabel(label1);
                    methodVisitor.visitLocalVariable("task", ARCHIVE_TASK_DESCRIPTOR, null, label0, label1, 0);
                    methodVisitor.visitMaxs(1, 1);
                    methodVisitor.visitEnd();
                }
            };
        }

        static BridgeMethodImplementation forGetDestinationDir() {
            return new BridgeMethodImplementation() {
                @Override
                void injectImplementation(String bridgeMethodName, ClassVisitor cv) {
                    MethodVisitor methodVisitor = cv.visitMethod(ACC_PRIVATE | ACC_STATIC, bridgeMethodName, RETURN_FILE_FROM_ARCHIVE_TASK, null, null);
                    methodVisitor.visitCode();
                    Label label0 = new Label();
                    methodVisitor.visitLabel(label0);
                    methodVisitor.visitVarInsn(ALOAD, 0);
                    methodVisitor.visitMethodInsn(INVOKEVIRTUAL, ARCHIVE_TASK_NAME, "getDestinationDirectory", "()Lorg/gradle/api/file/DirectoryProperty;", false);
                    methodVisitor.visitMethodInsn(INVOKEINTERFACE, "org/gradle/api/file/DirectoryProperty", "get", "()Ljava/lang/Object;", true);
                    methodVisitor.visitTypeInsn(CHECKCAST, "org/gradle/api/file/Directory");
                    methodVisitor.visitMethodInsn(INVOKEINTERFACE, "org/gradle/api/file/Directory", "getAsFile", "()Ljava/io/File;", true);
                    methodVisitor.visitInsn(ARETURN);
                    Label label1 = new Label();
                    methodVisitor.visitLabel(label1);
                    methodVisitor.visitLocalVariable("task", ARCHIVE_TASK_DESCRIPTOR, null, label0, label1, 0);
                    methodVisitor.visitMaxs(1, 1);
                    methodVisitor.visitEnd();
                }
            };
        }

        static BridgeMethodImplementation forSetDestinationDir() {
            return new BridgeMethodImplementation() {
                @Override
                void injectImplementation(String bridgeMethodName, ClassVisitor cv) {
                    MethodVisitor methodVisitor = cv.visitMethod(ACC_PRIVATE | ACC_STATIC, bridgeMethodName, RETURN_VOID_FROM_ARCHIVE_TASK_AND_FILE, null, null);
                    methodVisitor.visitCode();
                    Label label0 = new Label();
                    methodVisitor.visitLabel(label0);
                    methodVisitor.visitVarInsn(ALOAD, 0);
                    methodVisitor.visitMethodInsn(INVOKEVIRTUAL, ARCHIVE_TASK_NAME, "getDestinationDirectory", "()Lorg/gradle/api/file/DirectoryProperty;", false);
                    methodVisitor.visitVarInsn(ALOAD, 0);
                    methodVisitor.visitMethodInsn(INVOKEVIRTUAL, ARCHIVE_TASK_NAME, "getProject", "()Lorg/gradle/api/Project;", false);
                    methodVisitor.visitVarInsn(ALOAD, 1);
                    methodVisitor.visitMethodInsn(INVOKEINTERFACE, "org/gradle/api/Project", "file", "(Ljava/lang/Object;)Ljava/io/File;", true);
                    methodVisitor.visitMethodInsn(INVOKEINTERFACE, "org/gradle/api/file/DirectoryProperty", "set", "(Ljava/io/File;)V", true);
                    Label label1 = new Label();
                    methodVisitor.visitLabel(label1);
                    methodVisitor.visitInsn(RETURN);
                    Label label2 = new Label();
                    methodVisitor.visitLabel(label2);
                    methodVisitor.visitLocalVariable("task", ARCHIVE_TASK_DESCRIPTOR, null, label0, label2, 0);
                    methodVisitor.visitLocalVariable("f", "Ljava/io/File;", null, label0, label2, 1);
                    methodVisitor.visitMaxs(3, 2);
                    methodVisitor.visitEnd();
                }
            };
        }
    }

    enum RemovedMethod {
        GET_ARCHIVE_NAME("getArchiveName", RETURN_STRING, RETURN_STRING_FROM_ARCHIVE_TASK, BridgeMethodImplementation.forStringPropertyGetter("getArchiveFileName")),
        SET_ARCHIVE_NAME("setArchiveName", RETURN_VOID_FROM_STRING, RETURN_VOID_FROM_ARCHIVE_TASK_AND_STRING,  BridgeMethodImplementation.forStringPropertySetter("getArchiveFileName")),
        GET_ARCHIVE_PATH("getArchivePath", RETURN_FILE, RETURN_FILE_FROM_ARCHIVE_TASK, BridgeMethodImplementation.forGetArchivePath()),
        GET_DESTINATION_DIR("getDestinationDir", RETURN_FILE, RETURN_FILE_FROM_ARCHIVE_TASK, BridgeMethodImplementation.forGetDestinationDir()),
        SET_DESTINATION_DIR("setDestinationDir", RETURN_VOID_FROM_FILE, RETURN_VOID_FROM_ARCHIVE_TASK_AND_FILE, BridgeMethodImplementation.forSetDestinationDir()),
        GET_BASE_NAME("getBaseName", RETURN_STRING, RETURN_STRING_FROM_ARCHIVE_TASK, BridgeMethodImplementation.forStringPropertyGetter("getArchiveBaseName")),
        SET_BASE_NAME("setBaseName", RETURN_VOID_FROM_STRING, RETURN_VOID_FROM_ARCHIVE_TASK_AND_STRING, BridgeMethodImplementation.forStringPropertySetter("getArchiveBaseName")),
        GET_APPENDIX("getAppendix", RETURN_STRING, RETURN_STRING_FROM_ARCHIVE_TASK, BridgeMethodImplementation.forStringPropertyGetter("getArchiveAppendix")),
        SET_APPENDIX("setAppendix", RETURN_VOID_FROM_STRING, RETURN_VOID_FROM_ARCHIVE_TASK_AND_STRING, BridgeMethodImplementation.forStringPropertySetter("getArchiveAppendix")),
        GET_VERSION("getVersion", RETURN_STRING, RETURN_STRING_FROM_ARCHIVE_TASK, BridgeMethodImplementation.forStringPropertyGetter("getArchiveVersion")),
        SET_VERSION("setVersion", RETURN_VOID_FROM_STRING, RETURN_VOID_FROM_ARCHIVE_TASK_AND_STRING, BridgeMethodImplementation.forStringPropertySetter("getArchiveVersion")),
        GET_EXTENSION("getExtension", RETURN_STRING, RETURN_STRING_FROM_ARCHIVE_TASK, BridgeMethodImplementation.forStringPropertyGetter("getArchiveExtension")),
        SET_EXTENSION("setExtension", RETURN_VOID_FROM_STRING, RETURN_VOID_FROM_ARCHIVE_TASK_AND_STRING, BridgeMethodImplementation.forStringPropertySetter("getArchiveExtension")),
        GET_CLASSIFIER("getClassifier", RETURN_STRING, RETURN_STRING_FROM_ARCHIVE_TASK, BridgeMethodImplementation.forStringPropertyGetter("getArchiveClassifier")),
        SET_CLASSIFIER("setClassifier", RETURN_VOID_FROM_STRING,  RETURN_VOID_FROM_ARCHIVE_TASK_AND_STRING, BridgeMethodImplementation.forStringPropertySetter("getArchiveClassifier"));

        private final String name;
        private final String descriptor;
        private final String bridgeMethodName;
        private final String bridgeMethodDescriptor;
        private final BridgeMethodImplementation implementation;

        RemovedMethod(String name, String descriptor, String bridgeMethodDescriptor, BridgeMethodImplementation implementation) {
            this.name = name;
            this.descriptor = descriptor;
            this.bridgeMethodName = "gradleCompat" + StringUtils.capitalize(name);
            this.bridgeMethodDescriptor = bridgeMethodDescriptor;
            this.implementation = implementation;
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
            implementation.injectImplementation(bridgeMethodName, cv);
        }
    }
}

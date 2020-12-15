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

package org.gradle.initialization;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;

import java.util.Arrays;
import java.util.Collection;
import java.util.function.Consumer;

import static org.objectweb.asm.Opcodes.ACC_DEPRECATED;
import static org.objectweb.asm.Opcodes.ACC_PUBLIC;
import static org.objectweb.asm.Opcodes.ALOAD;
import static org.objectweb.asm.Opcodes.ARETURN;
import static org.objectweb.asm.Opcodes.CHECKCAST;
import static org.objectweb.asm.Opcodes.INVOKEINTERFACE;
import static org.objectweb.asm.Opcodes.INVOKEVIRTUAL;
import static org.objectweb.asm.Opcodes.POP;
import static org.objectweb.asm.Opcodes.RETURN;

/**
 * This class provides runtime implementation for methods that were removed from the API.
 * <p>
 * The primary goal is to provide backward compatibility for existing plugins using
 * deprecated APIs that are removed in a recent Gradle release.
 * </p>
 */
class LegacyMethodSupport {

    private static final LegacyMethodSupport INSTANCE = new LegacyMethodSupport();

    private static final Consumer<ClassVisitor> NOOP = new Consumer<ClassVisitor>() {
        @Override
        public void accept(ClassVisitor classWriter) {
        }
    };

    static LegacyMethodSupport getInstance() {
        return INSTANCE;
    }

    private final Collection<String> concreteArchiveTasks = Arrays.asList(
        "org.gradle.jvm.tasks.Jar",
        "org.gradle.api.tasks.bundling.Jar",
        "org.gradle.api.tasks.bundling.Tar",
        "org.gradle.plugins.Ear",
        "org.gradle.plugins.War"
    );

    private LegacyMethodSupport() {
    }

    boolean hasMissingMethods(String className) {
        return concreteArchiveTasks.contains(className);
    }

    Consumer<ClassVisitor> legacyMethodImplementationFor(String className) {
        if (!concreteArchiveTasks.contains(className)) {
            return NOOP;
        } else {
            return new ArchiveTaskLegacyMethods();
        }
    }

    private static class ArchiveTaskLegacyMethods implements Consumer<ClassVisitor> {
        @Override
        public void accept(ClassVisitor cv) {
            // Generated with org.objectweb.asm.util.ASMifier
            MethodVisitor methodVisitor;
            AnnotationVisitor annotationVisitor0;
            Label label0;
            Label label1;
            Label label2;
            Label label3;

            methodVisitor = cv.visitMethod(ACC_PUBLIC | ACC_DEPRECATED, "getArchiveName", "()Ljava/lang/String;", null, null);
            annotationVisitor0 = methodVisitor.visitAnnotation("Ljava/lang/Deprecated;", true);
            annotationVisitor0.visitEnd();
            annotationVisitor0 = methodVisitor.visitAnnotation("Lorg/gradle/api/model/ReplacedBy;", true);
            annotationVisitor0.visit("value", "archiveFileName");
            annotationVisitor0.visitEnd();
            methodVisitor.visitCode();
            label0 = new Label();
            methodVisitor.visitLabel(label0);
            methodVisitor.visitLineNumber(111, label0);
            methodVisitor.visitVarInsn(ALOAD, 0);
            methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "org/gradle/api/tasks/bundling/AbstractArchiveTask", "getArchiveFileName", "()Lorg/gradle/api/provider/Property;", false);
            methodVisitor.visitMethodInsn(INVOKEINTERFACE, "org/gradle/api/provider/Property", "get", "()Ljava/lang/Object;", true);
            methodVisitor.visitTypeInsn(CHECKCAST, "java/lang/String");
            methodVisitor.visitInsn(ARETURN);
            label1 = new Label();
            methodVisitor.visitLabel(label1);
            methodVisitor.visitLocalVariable("this", "Lorg/gradle/api/tasks/bundling/AbstractArchiveTask;", null, label0, label1, 0);
            methodVisitor.visitMaxs(1, 1);
            methodVisitor.visitEnd();

            methodVisitor = cv.visitMethod(ACC_PUBLIC | ACC_DEPRECATED, "setArchiveName", "(Ljava/lang/String;)V", null, null);
            annotationVisitor0 = methodVisitor.visitAnnotation("Ljava/lang/Deprecated;", true);
            annotationVisitor0.visitEnd();
            methodVisitor.visitCode();
            label0 = new Label();
            methodVisitor.visitLabel(label0);
            methodVisitor.visitLineNumber(122, label0);
            methodVisitor.visitVarInsn(ALOAD, 0);
            methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "org/gradle/api/tasks/bundling/AbstractArchiveTask", "getArchiveFileName", "()Lorg/gradle/api/provider/Property;", false);
            methodVisitor.visitVarInsn(ALOAD, 1);
            methodVisitor.visitMethodInsn(INVOKEINTERFACE, "org/gradle/api/provider/Property", "convention", "(Ljava/lang/Object;)Lorg/gradle/api/provider/Property;", true);
            methodVisitor.visitInsn(POP);
            label1 = new Label();
            methodVisitor.visitLabel(label1);
            methodVisitor.visitLineNumber(123, label1);
            methodVisitor.visitVarInsn(ALOAD, 0);
            methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "org/gradle/api/tasks/bundling/AbstractArchiveTask", "getArchiveFileName", "()Lorg/gradle/api/provider/Property;", false);
            methodVisitor.visitVarInsn(ALOAD, 1);
            methodVisitor.visitMethodInsn(INVOKEINTERFACE, "org/gradle/api/provider/Property", "set", "(Ljava/lang/Object;)V", true);
            label2 = new Label();
            methodVisitor.visitLabel(label2);
            methodVisitor.visitLineNumber(124, label2);
            methodVisitor.visitInsn(RETURN);
            label3 = new Label();
            methodVisitor.visitLabel(label3);
            methodVisitor.visitLocalVariable("this", "Lorg/gradle/api/tasks/bundling/AbstractArchiveTask;", null, label0, label3, 0);
            methodVisitor.visitLocalVariable("name", "Ljava/lang/String;", null, label0, label3, 1);
            methodVisitor.visitMaxs(2, 2);
            methodVisitor.visitEnd();

            methodVisitor = cv.visitMethod(ACC_PUBLIC | ACC_DEPRECATED, "getArchivePath", "()Ljava/io/File;", null, null);
            annotationVisitor0 = methodVisitor.visitAnnotation("Ljava/lang/Deprecated;", true);
            annotationVisitor0.visitEnd();
            annotationVisitor0 = methodVisitor.visitAnnotation("Lorg/gradle/api/model/ReplacedBy;", true);
            annotationVisitor0.visit("value", "archiveFile");
            annotationVisitor0.visitEnd();
            methodVisitor.visitCode();
            label0 = new Label();
            methodVisitor.visitLabel(label0);
            methodVisitor.visitLineNumber(149, label0);
            methodVisitor.visitVarInsn(ALOAD, 0);
            methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "org/gradle/api/tasks/bundling/AbstractArchiveTask", "getArchiveFile", "()Lorg/gradle/api/provider/Provider;", false);
            methodVisitor.visitMethodInsn(INVOKEINTERFACE, "org/gradle/api/provider/Provider", "get", "()Ljava/lang/Object;", true);
            methodVisitor.visitTypeInsn(CHECKCAST, "org/gradle/api/file/RegularFile");
            methodVisitor.visitMethodInsn(INVOKEINTERFACE, "org/gradle/api/file/RegularFile", "getAsFile", "()Ljava/io/File;", true);
            methodVisitor.visitInsn(ARETURN);
            label1 = new Label();
            methodVisitor.visitLabel(label1);
            methodVisitor.visitLocalVariable("this", "Lorg/gradle/api/tasks/bundling/AbstractArchiveTask;", null, label0, label1, 0);
            methodVisitor.visitMaxs(1, 1);
            methodVisitor.visitEnd();

            methodVisitor = cv.visitMethod(ACC_PUBLIC | ACC_DEPRECATED, "getDestinationDir", "()Ljava/io/File;", null, null);
            annotationVisitor0 = methodVisitor.visitAnnotation("Ljava/lang/Deprecated;", true);
            annotationVisitor0.visitEnd();
            annotationVisitor0 = methodVisitor.visitAnnotation("Lorg/gradle/api/model/ReplacedBy;", true);
            annotationVisitor0.visit("value", "destinationDirectory");
            annotationVisitor0.visitEnd();
            methodVisitor.visitCode();
            label0 = new Label();
            methodVisitor.visitLabel(label0);
            methodVisitor.visitLineNumber(183, label0);
            methodVisitor.visitVarInsn(ALOAD, 0);
            methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "org/gradle/api/tasks/bundling/AbstractArchiveTask", "getDestinationDirectory", "()Lorg/gradle/api/file/DirectoryProperty;", false);
            methodVisitor.visitMethodInsn(INVOKEINTERFACE, "org/gradle/api/file/DirectoryProperty", "getAsFile", "()Lorg/gradle/api/provider/Provider;", true);
            methodVisitor.visitMethodInsn(INVOKEINTERFACE, "org/gradle/api/provider/Provider", "get", "()Ljava/lang/Object;", true);
            methodVisitor.visitTypeInsn(CHECKCAST, "java/io/File");
            methodVisitor.visitInsn(ARETURN);
            label1 = new Label();
            methodVisitor.visitLabel(label1);
            methodVisitor.visitLocalVariable("this", "Lorg/gradle/api/tasks/bundling/AbstractArchiveTask;", null, label0, label1, 0);
            methodVisitor.visitMaxs(1, 1);
            methodVisitor.visitEnd();

            methodVisitor = cv.visitMethod(ACC_PUBLIC | ACC_DEPRECATED, "setDestinationDir", "(Ljava/io/File;)V", null, null);
            annotationVisitor0 = methodVisitor.visitAnnotation("Ljava/lang/Deprecated;", true);
            annotationVisitor0.visitEnd();
            methodVisitor.visitCode();
            label0 = new Label();
            methodVisitor.visitLabel(label0);
            methodVisitor.visitLineNumber(193, label0);
            methodVisitor.visitVarInsn(ALOAD, 0);
            methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "org/gradle/api/tasks/bundling/AbstractArchiveTask", "getDestinationDirectory", "()Lorg/gradle/api/file/DirectoryProperty;", false);
            methodVisitor.visitVarInsn(ALOAD, 0);
            methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "org/gradle/api/tasks/bundling/AbstractArchiveTask", "getProject", "()Lorg/gradle/api/Project;", false);
            methodVisitor.visitVarInsn(ALOAD, 1);
            methodVisitor.visitMethodInsn(INVOKEINTERFACE, "org/gradle/api/Project", "file", "(Ljava/lang/Object;)Ljava/io/File;", true);
            methodVisitor.visitMethodInsn(INVOKEINTERFACE, "org/gradle/api/file/DirectoryProperty", "set", "(Ljava/io/File;)V", true);
            label1 = new Label();
            methodVisitor.visitLabel(label1);
            methodVisitor.visitLineNumber(194, label1);
            methodVisitor.visitInsn(RETURN);
            label2 = new Label();
            methodVisitor.visitLabel(label2);
            methodVisitor.visitLocalVariable("this", "Lorg/gradle/api/tasks/bundling/AbstractArchiveTask;", null, label0, label2, 0);
            methodVisitor.visitLocalVariable("destinationDir", "Ljava/io/File;", null, label0, label2, 1);
            methodVisitor.visitMaxs(3, 2);
            methodVisitor.visitEnd();

            methodVisitor = cv.visitMethod(ACC_PUBLIC | ACC_DEPRECATED, "getBaseName", "()Ljava/lang/String;", null, null);
            annotationVisitor0 = methodVisitor.visitAnnotation("Ljavax/annotation/Nullable;", true);
            annotationVisitor0.visitEnd();
            annotationVisitor0 = methodVisitor.visitAnnotation("Ljava/lang/Deprecated;", true);
            annotationVisitor0.visitEnd();
            annotationVisitor0 = methodVisitor.visitAnnotation("Lorg/gradle/api/model/ReplacedBy;", true);
            annotationVisitor0.visit("value", "archiveBaseName");
            annotationVisitor0.visitEnd();
            methodVisitor.visitCode();
            label0 = new Label();
            methodVisitor.visitLabel(label0);
            methodVisitor.visitLineNumber(216, label0);
            methodVisitor.visitVarInsn(ALOAD, 0);
            methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "org/gradle/api/tasks/bundling/AbstractArchiveTask", "getArchiveBaseName", "()Lorg/gradle/api/provider/Property;", false);
            methodVisitor.visitMethodInsn(INVOKEINTERFACE, "org/gradle/api/provider/Property", "getOrNull", "()Ljava/lang/Object;", true);
            methodVisitor.visitTypeInsn(CHECKCAST, "java/lang/String");
            methodVisitor.visitInsn(ARETURN);
            label1 = new Label();
            methodVisitor.visitLabel(label1);
            methodVisitor.visitLocalVariable("this", "Lorg/gradle/api/tasks/bundling/AbstractArchiveTask;", null, label0, label1, 0);
            methodVisitor.visitMaxs(1, 1);
            methodVisitor.visitEnd();

            methodVisitor = cv.visitMethod(ACC_PUBLIC | ACC_DEPRECATED, "setBaseName", "(Ljava/lang/String;)V", null, null);
            annotationVisitor0 = methodVisitor.visitAnnotation("Ljava/lang/Deprecated;", true);
            annotationVisitor0.visitEnd();
            methodVisitor.visitAnnotableParameterCount(1, true);
            annotationVisitor0 = methodVisitor.visitParameterAnnotation(0, "Ljavax/annotation/Nullable;", true);
            annotationVisitor0.visitEnd();
            methodVisitor.visitCode();
            label0 = new Label();
            methodVisitor.visitLabel(label0);
            methodVisitor.visitLineNumber(226, label0);
            methodVisitor.visitVarInsn(ALOAD, 0);
            methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "org/gradle/api/tasks/bundling/AbstractArchiveTask", "getArchiveBaseName", "()Lorg/gradle/api/provider/Property;", false);
            methodVisitor.visitVarInsn(ALOAD, 1);
            methodVisitor.visitMethodInsn(INVOKEINTERFACE, "org/gradle/api/provider/Property", "convention", "(Ljava/lang/Object;)Lorg/gradle/api/provider/Property;", true);
            methodVisitor.visitInsn(POP);
            label1 = new Label();
            methodVisitor.visitLabel(label1);
            methodVisitor.visitLineNumber(227, label1);
            methodVisitor.visitVarInsn(ALOAD, 0);
            methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "org/gradle/api/tasks/bundling/AbstractArchiveTask", "getArchiveBaseName", "()Lorg/gradle/api/provider/Property;", false);
            methodVisitor.visitVarInsn(ALOAD, 1);
            methodVisitor.visitMethodInsn(INVOKEINTERFACE, "org/gradle/api/provider/Property", "set", "(Ljava/lang/Object;)V", true);
            label2 = new Label();
            methodVisitor.visitLabel(label2);
            methodVisitor.visitLineNumber(228, label2);
            methodVisitor.visitInsn(RETURN);
            label3 = new Label();
            methodVisitor.visitLabel(label3);
            methodVisitor.visitLocalVariable("this", "Lorg/gradle/api/tasks/bundling/AbstractArchiveTask;", null, label0, label3, 0);
            methodVisitor.visitLocalVariable("baseName", "Ljava/lang/String;", null, label0, label3, 1);
            methodVisitor.visitMaxs(2, 2);
            methodVisitor.visitEnd();

            methodVisitor = cv.visitMethod(ACC_PUBLIC | ACC_DEPRECATED, "getAppendix", "()Ljava/lang/String;", null, null);
            annotationVisitor0 = methodVisitor.visitAnnotation("Ljavax/annotation/Nullable;", true);
            annotationVisitor0.visitEnd();
            annotationVisitor0 = methodVisitor.visitAnnotation("Ljava/lang/Deprecated;", true);
            annotationVisitor0.visitEnd();
            annotationVisitor0 = methodVisitor.visitAnnotation("Lorg/gradle/api/model/ReplacedBy;", true);
            annotationVisitor0.visit("value", "archiveAppendix");
            annotationVisitor0.visitEnd();
            methodVisitor.visitCode();
            label0 = new Label();
            methodVisitor.visitLabel(label0);
            methodVisitor.visitLineNumber(251, label0);
            methodVisitor.visitVarInsn(ALOAD, 0);
            methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "org/gradle/api/tasks/bundling/AbstractArchiveTask", "getArchiveAppendix", "()Lorg/gradle/api/provider/Property;", false);
            methodVisitor.visitMethodInsn(INVOKEINTERFACE, "org/gradle/api/provider/Property", "getOrNull", "()Ljava/lang/Object;", true);
            methodVisitor.visitTypeInsn(CHECKCAST, "java/lang/String");
            methodVisitor.visitInsn(ARETURN);
            label1 = new Label();
            methodVisitor.visitLabel(label1);
            methodVisitor.visitLocalVariable("this", "Lorg/gradle/api/tasks/bundling/AbstractArchiveTask;", null, label0, label1, 0);
            methodVisitor.visitMaxs(1, 1);
            methodVisitor.visitEnd();

            methodVisitor = cv.visitMethod(ACC_PUBLIC | ACC_DEPRECATED, "setAppendix", "(Ljava/lang/String;)V", null, null);
            annotationVisitor0 = methodVisitor.visitAnnotation("Ljava/lang/Deprecated;", true);
            annotationVisitor0.visitEnd();
            methodVisitor.visitAnnotableParameterCount(1, true);
            annotationVisitor0 = methodVisitor.visitParameterAnnotation(0, "Ljavax/annotation/Nullable;", true);
            annotationVisitor0.visitEnd();
            methodVisitor.visitCode();
            label0 = new Label();
            methodVisitor.visitLabel(label0);
            methodVisitor.visitLineNumber(263, label0);
            methodVisitor.visitVarInsn(ALOAD, 0);
            methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "org/gradle/api/tasks/bundling/AbstractArchiveTask", "getArchiveAppendix", "()Lorg/gradle/api/provider/Property;", false);
            methodVisitor.visitVarInsn(ALOAD, 1);
            methodVisitor.visitMethodInsn(INVOKEINTERFACE, "org/gradle/api/provider/Property", "convention", "(Ljava/lang/Object;)Lorg/gradle/api/provider/Property;", true);
            methodVisitor.visitInsn(POP);
            label1 = new Label();
            methodVisitor.visitLabel(label1);
            methodVisitor.visitLineNumber(264, label1);
            methodVisitor.visitVarInsn(ALOAD, 0);
            methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "org/gradle/api/tasks/bundling/AbstractArchiveTask", "getArchiveAppendix", "()Lorg/gradle/api/provider/Property;", false);
            methodVisitor.visitVarInsn(ALOAD, 1);
            methodVisitor.visitMethodInsn(INVOKEINTERFACE, "org/gradle/api/provider/Property", "set", "(Ljava/lang/Object;)V", true);
            label2 = new Label();
            methodVisitor.visitLabel(label2);
            methodVisitor.visitLineNumber(265, label2);
            methodVisitor.visitInsn(RETURN);
            label3 = new Label();
            methodVisitor.visitLabel(label3);
            methodVisitor.visitLocalVariable("this", "Lorg/gradle/api/tasks/bundling/AbstractArchiveTask;", null, label0, label3, 0);
            methodVisitor.visitLocalVariable("appendix", "Ljava/lang/String;", null, label0, label3, 1);
            methodVisitor.visitMaxs(2, 2);
            methodVisitor.visitEnd();

            methodVisitor = cv.visitMethod(ACC_PUBLIC | ACC_DEPRECATED, "getVersion", "()Ljava/lang/String;", null, null);
            annotationVisitor0 = methodVisitor.visitAnnotation("Ljavax/annotation/Nullable;", true);
            annotationVisitor0.visitEnd();
            annotationVisitor0 = methodVisitor.visitAnnotation("Ljava/lang/Deprecated;", true);
            annotationVisitor0.visitEnd();
            annotationVisitor0 = methodVisitor.visitAnnotation("Lorg/gradle/api/model/ReplacedBy;", true);
            annotationVisitor0.visit("value", "archiveVersion");
            annotationVisitor0.visitEnd();
            methodVisitor.visitCode();
            label0 = new Label();
            methodVisitor.visitLabel(label0);
            methodVisitor.visitLineNumber(288, label0);
            methodVisitor.visitVarInsn(ALOAD, 0);
            methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "org/gradle/api/tasks/bundling/AbstractArchiveTask", "getArchiveVersion", "()Lorg/gradle/api/provider/Property;", false);
            methodVisitor.visitMethodInsn(INVOKEINTERFACE, "org/gradle/api/provider/Property", "getOrNull", "()Ljava/lang/Object;", true);
            methodVisitor.visitTypeInsn(CHECKCAST, "java/lang/String");
            methodVisitor.visitInsn(ARETURN);
            label1 = new Label();
            methodVisitor.visitLabel(label1);
            methodVisitor.visitLocalVariable("this", "Lorg/gradle/api/tasks/bundling/AbstractArchiveTask;", null, label0, label1, 0);
            methodVisitor.visitMaxs(1, 1);
            methodVisitor.visitEnd();

            methodVisitor = cv.visitMethod(ACC_PUBLIC | ACC_DEPRECATED, "setVersion", "(Ljava/lang/String;)V", null, null);
            annotationVisitor0 = methodVisitor.visitAnnotation("Ljava/lang/Deprecated;", true);
            annotationVisitor0.visitEnd();
            methodVisitor.visitAnnotableParameterCount(1, true);
            annotationVisitor0 = methodVisitor.visitParameterAnnotation(0, "Ljavax/annotation/Nullable;", true);
            annotationVisitor0.visitEnd();
            methodVisitor.visitCode();
            label0 = new Label();
            methodVisitor.visitLabel(label0);
            methodVisitor.visitLineNumber(298, label0);
            methodVisitor.visitVarInsn(ALOAD, 0);
            methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "org/gradle/api/tasks/bundling/AbstractArchiveTask", "getArchiveVersion", "()Lorg/gradle/api/provider/Property;", false);
            methodVisitor.visitVarInsn(ALOAD, 1);
            methodVisitor.visitMethodInsn(INVOKEINTERFACE, "org/gradle/api/provider/Property", "convention", "(Ljava/lang/Object;)Lorg/gradle/api/provider/Property;", true);
            methodVisitor.visitInsn(POP);
            label1 = new Label();
            methodVisitor.visitLabel(label1);
            methodVisitor.visitLineNumber(299, label1);
            methodVisitor.visitVarInsn(ALOAD, 0);
            methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "org/gradle/api/tasks/bundling/AbstractArchiveTask", "getArchiveVersion", "()Lorg/gradle/api/provider/Property;", false);
            methodVisitor.visitVarInsn(ALOAD, 1);
            methodVisitor.visitMethodInsn(INVOKEINTERFACE, "org/gradle/api/provider/Property", "set", "(Ljava/lang/Object;)V", true);
            label2 = new Label();
            methodVisitor.visitLabel(label2);
            methodVisitor.visitLineNumber(300, label2);
            methodVisitor.visitInsn(RETURN);
            label3 = new Label();
            methodVisitor.visitLabel(label3);
            methodVisitor.visitLocalVariable("this", "Lorg/gradle/api/tasks/bundling/AbstractArchiveTask;", null, label0, label3, 0);
            methodVisitor.visitLocalVariable("version", "Ljava/lang/String;", null, label0, label3, 1);
            methodVisitor.visitMaxs(2, 2);
            methodVisitor.visitEnd();

            methodVisitor = cv.visitMethod(ACC_PUBLIC | ACC_DEPRECATED, "getExtension", "()Ljava/lang/String;", null, null);
            annotationVisitor0 = methodVisitor.visitAnnotation("Ljavax/annotation/Nullable;", true);
            annotationVisitor0.visitEnd();
            annotationVisitor0 = methodVisitor.visitAnnotation("Ljava/lang/Deprecated;", true);
            annotationVisitor0.visitEnd();
            annotationVisitor0 = methodVisitor.visitAnnotation("Lorg/gradle/api/model/ReplacedBy;", true);
            annotationVisitor0.visit("value", "archiveExtension");
            annotationVisitor0.visitEnd();
            methodVisitor.visitCode();
            label0 = new Label();
            methodVisitor.visitLabel(label0);
            methodVisitor.visitLineNumber(322, label0);
            methodVisitor.visitVarInsn(ALOAD, 0);
            methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "org/gradle/api/tasks/bundling/AbstractArchiveTask", "getArchiveExtension", "()Lorg/gradle/api/provider/Property;", false);
            methodVisitor.visitMethodInsn(INVOKEINTERFACE, "org/gradle/api/provider/Property", "getOrNull", "()Ljava/lang/Object;", true);
            methodVisitor.visitTypeInsn(CHECKCAST, "java/lang/String");
            methodVisitor.visitInsn(ARETURN);
            label1 = new Label();
            methodVisitor.visitLabel(label1);
            methodVisitor.visitLocalVariable("this", "Lorg/gradle/api/tasks/bundling/AbstractArchiveTask;", null, label0, label1, 0);
            methodVisitor.visitMaxs(1, 1);
            methodVisitor.visitEnd();

            methodVisitor = cv.visitMethod(ACC_PUBLIC | ACC_DEPRECATED, "setExtension", "(Ljava/lang/String;)V", null, null);
            annotationVisitor0 = methodVisitor.visitAnnotation("Ljava/lang/Deprecated;", true);
            annotationVisitor0.visitEnd();
            methodVisitor.visitAnnotableParameterCount(1, true);
            annotationVisitor0 = methodVisitor.visitParameterAnnotation(0, "Ljavax/annotation/Nullable;", true);
            annotationVisitor0.visitEnd();
            methodVisitor.visitCode();
            label0 = new Label();
            methodVisitor.visitLabel(label0);
            methodVisitor.visitLineNumber(332, label0);
            methodVisitor.visitVarInsn(ALOAD, 0);
            methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "org/gradle/api/tasks/bundling/AbstractArchiveTask", "getArchiveExtension", "()Lorg/gradle/api/provider/Property;", false);
            methodVisitor.visitVarInsn(ALOAD, 1);
            methodVisitor.visitMethodInsn(INVOKEINTERFACE, "org/gradle/api/provider/Property", "convention", "(Ljava/lang/Object;)Lorg/gradle/api/provider/Property;", true);
            methodVisitor.visitInsn(POP);
            label1 = new Label();
            methodVisitor.visitLabel(label1);
            methodVisitor.visitLineNumber(333, label1);
            methodVisitor.visitVarInsn(ALOAD, 0);
            methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "org/gradle/api/tasks/bundling/AbstractArchiveTask", "getArchiveExtension", "()Lorg/gradle/api/provider/Property;", false);
            methodVisitor.visitVarInsn(ALOAD, 1);
            methodVisitor.visitMethodInsn(INVOKEINTERFACE, "org/gradle/api/provider/Property", "set", "(Ljava/lang/Object;)V", true);
            label2 = new Label();
            methodVisitor.visitLabel(label2);
            methodVisitor.visitLineNumber(334, label2);
            methodVisitor.visitInsn(RETURN);
            label3 = new Label();
            methodVisitor.visitLabel(label3);
            methodVisitor.visitLocalVariable("this", "Lorg/gradle/api/tasks/bundling/AbstractArchiveTask;", null, label0, label3, 0);
            methodVisitor.visitLocalVariable("extension", "Ljava/lang/String;", null, label0, label3, 1);
            methodVisitor.visitMaxs(2, 2);
            methodVisitor.visitEnd();

            methodVisitor = cv.visitMethod(ACC_PUBLIC | ACC_DEPRECATED, "getClassifier", "()Ljava/lang/String;", null, null);
            annotationVisitor0 = methodVisitor.visitAnnotation("Ljavax/annotation/Nullable;", true);
            annotationVisitor0.visitEnd();
            annotationVisitor0 = methodVisitor.visitAnnotation("Ljava/lang/Deprecated;", true);
            annotationVisitor0.visitEnd();
            annotationVisitor0 = methodVisitor.visitAnnotation("Lorg/gradle/api/model/ReplacedBy;", true);
            annotationVisitor0.visit("value", "archiveClassifier");
            annotationVisitor0.visitEnd();
            methodVisitor.visitCode();
            label0 = new Label();
            methodVisitor.visitLabel(label0);
            methodVisitor.visitLineNumber(356, label0);
            methodVisitor.visitVarInsn(ALOAD, 0);
            methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "org/gradle/api/tasks/bundling/AbstractArchiveTask", "getArchiveClassifier", "()Lorg/gradle/api/provider/Property;", false);
            methodVisitor.visitMethodInsn(INVOKEINTERFACE, "org/gradle/api/provider/Property", "getOrNull", "()Ljava/lang/Object;", true);
            methodVisitor.visitTypeInsn(CHECKCAST, "java/lang/String");
            methodVisitor.visitInsn(ARETURN);
            label1 = new Label();
            methodVisitor.visitLabel(label1);
            methodVisitor.visitLocalVariable("this", "Lorg/gradle/api/tasks/bundling/AbstractArchiveTask;", null, label0, label1, 0);
            methodVisitor.visitMaxs(1, 1);
            methodVisitor.visitEnd();

            methodVisitor = cv.visitMethod(ACC_PUBLIC | ACC_DEPRECATED, "setClassifier", "(Ljava/lang/String;)V", null, null);
            annotationVisitor0 = methodVisitor.visitAnnotation("Ljava/lang/Deprecated;", true);
            annotationVisitor0.visitEnd();
            methodVisitor.visitAnnotableParameterCount(1, true);
            annotationVisitor0 = methodVisitor.visitParameterAnnotation(0, "Ljavax/annotation/Nullable;", true);
            annotationVisitor0.visitEnd();
            methodVisitor.visitCode();
            label0 = new Label();
            methodVisitor.visitLabel(label0);
            methodVisitor.visitLineNumber(368, label0);
            methodVisitor.visitVarInsn(ALOAD, 0);
            methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "org/gradle/api/tasks/bundling/AbstractArchiveTask", "getArchiveClassifier", "()Lorg/gradle/api/provider/Property;", false);
            methodVisitor.visitVarInsn(ALOAD, 1);
            methodVisitor.visitMethodInsn(INVOKEINTERFACE, "org/gradle/api/provider/Property", "convention", "(Ljava/lang/Object;)Lorg/gradle/api/provider/Property;", true);
            methodVisitor.visitInsn(POP);
            label1 = new Label();
            methodVisitor.visitLabel(label1);
            methodVisitor.visitLineNumber(369, label1);
            methodVisitor.visitVarInsn(ALOAD, 0);
            methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "org/gradle/api/tasks/bundling/AbstractArchiveTask", "getArchiveClassifier", "()Lorg/gradle/api/provider/Property;", false);
            methodVisitor.visitVarInsn(ALOAD, 1);
            methodVisitor.visitMethodInsn(INVOKEINTERFACE, "org/gradle/api/provider/Property", "set", "(Ljava/lang/Object;)V", true);
            label2 = new Label();
            methodVisitor.visitLabel(label2);
            methodVisitor.visitLineNumber(370, label2);
            methodVisitor.visitInsn(RETURN);
            label3 = new Label();
            methodVisitor.visitLabel(label3);
            methodVisitor.visitLocalVariable("this", "Lorg/gradle/api/tasks/bundling/AbstractArchiveTask;", null, label0, label3, 0);
            methodVisitor.visitLocalVariable("classifier", "Ljava/lang/String;", null, label0, label3, 1);
            methodVisitor.visitMaxs(2, 2);
            methodVisitor.visitEnd();
        }
    }
}

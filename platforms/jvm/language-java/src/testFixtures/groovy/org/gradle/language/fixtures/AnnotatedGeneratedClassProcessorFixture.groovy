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

package org.gradle.language.fixtures

import groovy.transform.CompileStatic
import org.gradle.api.internal.tasks.compile.incremental.processing.IncrementalAnnotationProcessorType

import static org.gradle.integtests.fixtures.RepoScriptBlockUtil.mavenCentralRepositoryDefinition

@CompileStatic
class AnnotatedGeneratedClassProcessorFixture extends AnnotationProcessorFixture {
    String generatedAnnotation = 'Service'
    boolean generateClassFile = false

    AnnotatedGeneratedClassProcessorFixture() {
        super("Bean")
        declaredType = IncrementalAnnotationProcessorType.ISOLATING
    }

    @Override
    String getDependenciesBlock() {
        if (generateClassFile) {
            """
                implementation 'org.ow2.asm:asm:9.0'
            """
        } else {
            ""
        }
    }

    @Override
    String getRepositoriesBlock() {
        if (generateClassFile) {
            """
                ${mavenCentralRepositoryDefinition()}
            """
        } else {
            ""
        }
    }

    @Override
    protected String getGeneratorCode() {
        if (generateClassFile) {
            classGeneratorCode
        } else {
            sourceGeneratorCode
        }
    }

    private String getClassGeneratorCode() {
        """
        for (Element element : elements) {
            TypeElement typeElement = (TypeElement) element;
            String className = typeElement.getSimpleName().toString() + "Helper";

            org.objectweb.asm.ClassWriter classWriter = new org.objectweb.asm.ClassWriter(0);
            org.objectweb.asm.MethodVisitor mv = null;
            classWriter.visit(org.objectweb.asm.Opcodes.V1_8, org.objectweb.asm.Opcodes.ACC_PUBLIC | org.objectweb.asm.Opcodes.ACC_SUPER, className, null, "java/lang/Object", new String[0]);
            classWriter.visitAnnotation("L$generatedAnnotation;", true);
            mv = classWriter.visitMethod(org.objectweb.asm.Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
            mv.visitCode();
            mv.visitMethodInsn(org.objectweb.asm.Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
            mv.visitInsn(org.objectweb.asm.Opcodes.RETURN);
            mv.visitEnd();
            classWriter.visitEnd();
            try {
                JavaFileObject classFile = filer.createClassFile(className, element);
                OutputStream out = classFile.openOutputStream();
                try {
                    out.write(classWriter.toByteArray());
                } finally {
                    out.close();
                }
            } catch (IOException e) {
                messager.printMessage(Diagnostic.Kind.ERROR, "Failed to generate class file " + className + ". " + e.getMessage(), element);
            }
        }
        """
    }

    private String getSourceGeneratorCode() {
        """
    for (Element element : elements) {
        TypeElement typeElement = (TypeElement) element;
        String className = typeElement.getSimpleName().toString() + "Helper";
        try {
            JavaFileObject sourceFile = filer.createSourceFile(className, element);
            Writer writer = sourceFile.openWriter();
            try {
                writer.write("@$generatedAnnotation public class " + className + " { } ");
            } finally {
                writer.close();
            }
        } catch (IOException e) {
            messager.printMessage(Diagnostic.Kind.ERROR, "Failed to generate source file " + className + ". " + e.getMessage(), element);
        }
    }
"""
    }

    @Override
    protected String getSupportedOptionsBlock() {
        """
            @Override
            public Set<String> getSupportedOptions() {
                return new HashSet<String>(Arrays.asList("${IncrementalAnnotationProcessorType.ISOLATING.processorOption}"));
            }
        """
    }
}

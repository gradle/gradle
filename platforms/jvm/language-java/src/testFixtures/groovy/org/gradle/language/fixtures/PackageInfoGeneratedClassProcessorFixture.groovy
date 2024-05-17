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
class PackageInfoGeneratedClassProcessorFixture extends AnnotationProcessorFixture {
    PackageInfoGeneratedClassProcessorFixture() {
        super("com.my.processor", "Configuration")
        declaredType = IncrementalAnnotationProcessorType.AGGREGATING
    }

    @Override
    String getDependenciesBlock() {
        """
            implementation 'org.ow2.asm:asm:9.0'
        """
    }

    @Override
    String getRepositoriesBlock() {
        """
            ${mavenCentralRepositoryDefinition()}
        """
    }

    @Override
    protected String getGeneratorCode() {
        """

        for (PackageElement element : ElementFilter.packagesIn(elements)) {
            String className = element.getQualifiedName().toString() + ".\$" + "Configuration";

            org.objectweb.asm.ClassWriter classWriter = new org.objectweb.asm.ClassWriter(0);
            org.objectweb.asm.MethodVisitor mv = null;
            classWriter.visit(org.objectweb.asm.Opcodes.V1_8, org.objectweb.asm.Opcodes.ACC_PUBLIC | org.objectweb.asm.Opcodes.ACC_SUPER, className, null, "java/lang/Object", new String[0]);
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
            return false;
        }
        """
    }
}

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

package org.gradle.api.internal.tasks.compile;

import org.gradle.api.tasks.WorkResult;
import org.gradle.language.base.internal.compile.Compiler;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.ModuleVisitor;
import org.objectweb.asm.Opcodes;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Post processes the compilation result to add the ModuleMainClass attribute to module-info.class
 */
public class ModuleApplicationNameWritingCompiler<T extends JavaCompileSpec> implements Compiler<T> {

    private final Compiler<T> delegate;

    public ModuleApplicationNameWritingCompiler(Compiler<T> delegate) {
        this.delegate = delegate;
    }

    @Override
    public WorkResult execute(T spec) {
        WorkResult result = delegate.execute(spec);
        String mainClass = spec.getCompileOptions().getJavaModuleMainClass();
        if (mainClass != null) {
            File moduleInfo = new File(spec.getDestinationDir(), "module-info.class");
            if (moduleInfo.exists()) {
                addMainClass(moduleInfo, mainClass);
            }
        }
        return result;
    }

    private static void addMainClass(File moduleInfo, String mainClass) {
        try (InputStream inputStream = new FileInputStream(moduleInfo)) {
            ClassReader classReader = new ClassReader(inputStream);
            ClassWriter classWriter = new ClassWriter(classReader, 0);
            ClassVisitor moduleInfoVisitor = new ModuleInfoVisitor(mainClass, classWriter);
            classReader.accept(moduleInfoVisitor, 0);
            FileOutputStream out = new FileOutputStream(moduleInfo);
            out.write(classWriter.toByteArray());
            out.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }


    private static class ModuleInfoVisitor extends ClassVisitor {
        private final String mainClass;

        public ModuleInfoVisitor(String mainClass, ClassVisitor cv) {
            super(Opcodes.ASM4, cv);
            this.mainClass = mainClass;
        }

        @Override
        public ModuleVisitor visitModule(String name, int access, String version) {
            return new ModuleMainClassWriter(mainClass, cv.visitModule(name, access, version));
        }
    }

    private static class ModuleMainClassWriter extends ModuleVisitor {
        private final String mainClass;

        private ModuleMainClassWriter(String mainClass, ModuleVisitor mv) {
            super(Opcodes.ASM4, mv);
            this.mainClass = mainClass;
        }

        @Override
        public void visitEnd() {
            mv.visitMainClass(this.mainClass.replace('.', '/'));
            super.visitEnd();
        }
    }
}

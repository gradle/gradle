/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.api.internal.tasks.compile.incremental.analyzer;

import com.google.common.hash.HashCode;
import com.google.common.io.ByteStreams;
import org.gradle.api.file.FileTreeElement;
import org.gradle.api.internal.tasks.compile.incremental.asm.ClassDependenciesVisitor;
import org.gradle.api.internal.tasks.compile.incremental.deps.ClassAnalysis;
import org.gradle.util.internal.Java9ClassReader;
import org.objectweb.asm.ClassReader;

import java.io.IOException;
import java.io.InputStream;

public class DefaultClassDependenciesAnalyzer implements ClassDependenciesAnalyzer {

    public ClassAnalysis getClassAnalysis(InputStream input) throws IOException {
        ClassReader reader = new Java9ClassReader(ByteStreams.toByteArray(input));
        String className = reader.getClassName().replace("/", ".");
        return ClassDependenciesVisitor.analyze(className, reader);
    }

    @Override
    public ClassAnalysis getClassAnalysis(HashCode classFileHash, FileTreeElement classFile) {
        try {
            InputStream input = classFile.open();
            try {
                return getClassAnalysis(input);
            } finally {
                input.close();
            }
        } catch (IOException e) {
            throw new RuntimeException("Problems loading class analysis for " + classFile.toString());
        }
    }
}

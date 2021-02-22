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

package org.gradle.performance.mutator;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import org.gradle.profiler.BuildContext;
import org.gradle.profiler.mutations.AbstractFileChangeMutator;

import java.io.File;

// TODO: BM replace with gradle profiler once it moved to new java parser
public abstract class AbstractJavaSourceFileMutator extends AbstractFileChangeMutator {
    public AbstractJavaSourceFileMutator(File sourceFile) {
        super(sourceFile);
        if (!sourceFile.getName().endsWith(".java")) {
            throw new IllegalArgumentException("Can only modify Java source files");
        }
    }

    @Override
    protected void applyChangeTo(BuildContext context, StringBuilder text) {
        CompilationUnit compilationUnit = new JavaParser().parse(text.toString()).getResult().get();
        applyChangeTo(context, compilationUnit);
        text.replace(0, text.length(), compilationUnit.toString());
    }

    protected abstract void applyChangeTo(BuildContext context, CompilationUnit compilationUnit);
}

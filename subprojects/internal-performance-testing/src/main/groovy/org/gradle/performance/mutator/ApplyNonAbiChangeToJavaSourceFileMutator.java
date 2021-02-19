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
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import org.gradle.profiler.BuildContext;

import java.io.File;
import java.util.List;

// TODO: BM replace with gradle profiler once it moved to new java parser
public class ApplyNonAbiChangeToJavaSourceFileMutator extends AbstractJavaSourceFileMutator {
    public ApplyNonAbiChangeToJavaSourceFileMutator(File sourceFile) {
        super(sourceFile);
    }

    @Override
    protected void applyChangeTo(BuildContext context, CompilationUnit compilationUnit) {
        MethodDeclaration existingMethod = getExistingMethod(compilationUnit);
        existingMethod.getBody()
            .orElseThrow(() -> new RuntimeException("Method body not found"))
            .addStatement(0, new JavaParser().parseStatement("System.out.println(\"" + context.getUniqueBuildId() + "\");").getResult().get());
    }

    private MethodDeclaration getExistingMethod(CompilationUnit compilationUnit) {
        NodeList<TypeDeclaration<?>> types = compilationUnit.getTypes();
        if (types.isEmpty()){
            throw new IllegalArgumentException("No types to change in " + sourceFile);
        }
        TypeDeclaration<?> type = types.get(0);
        List<MethodDeclaration> methods = type.getMethods();
        if (methods.isEmpty()) {
            throw new IllegalArgumentException("No methods to change in " + sourceFile);
        }
        return methods.get(0);
    }
}

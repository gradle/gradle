/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.play.internal.routes;

import com.google.common.collect.Lists;
import org.gradle.api.tasks.WorkResult;
import org.gradle.api.tasks.WorkResults;
import org.gradle.language.base.internal.compile.Compiler;
import org.gradle.scala.internal.reflect.ScalaMethod;

import javax.inject.Inject;
import java.io.File;
import java.io.Serializable;
import java.util.ArrayList;

public class RoutesCompiler implements Compiler<RoutesCompileSpec>, Serializable {
    private final VersionedRoutesCompilerAdapter adapter;

    @Inject
    public RoutesCompiler(VersionedRoutesCompilerAdapter adapter) {
        this.adapter = adapter;
    }

    @Override
    public WorkResult execute(RoutesCompileSpec spec) {
        boolean didWork = false;
        // Need to compile all secondary routes ("Foo.routes") before primary ("routes")
        ArrayList<File> primaryRoutes = Lists.newArrayList();
        ArrayList<File> secondaryRoutes = Lists.newArrayList();
        for (File source : spec.getSources()) {
            if (source.getName().equals("routes")) {
                primaryRoutes.add(source);
            } else {
                secondaryRoutes.add(source);
            }
        }

        // Compile all secondary routes files first
        for (File sourceFile : secondaryRoutes) {
            Boolean ret = compile(sourceFile, spec);
            didWork = ret || didWork;
        }

        // Compile all main routes files last
        for (File sourceFile : primaryRoutes) {
            Boolean ret = compile(sourceFile, spec);
            didWork = ret || didWork;
        }

        return WorkResults.didWork(didWork);
    }

    private Boolean compile(File sourceFile, RoutesCompileSpec spec) {

        try {
            ClassLoader cl = getClass().getClassLoader();
            ScalaMethod compile = adapter.getCompileMethod(cl);
            return adapter.interpretResult(compile.invoke(adapter.createCompileParameters(cl, sourceFile, spec.getDestinationDir(), spec.isJavaProject(), spec.isNamespaceReverseRouter(), spec.isGenerateReverseRoutes(), spec.isInjectedRoutesGenerator(), spec.getAdditionalImports())));
        } catch (Exception e) {
            throw new RuntimeException("Error invoking the Play routes compiler.", e);
        }
    }
}

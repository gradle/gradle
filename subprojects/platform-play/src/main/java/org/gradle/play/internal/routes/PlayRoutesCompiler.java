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

import com.google.common.base.Function;
import org.gradle.api.internal.tasks.SimpleWorkResult;
import org.gradle.api.tasks.WorkResult;
import org.gradle.language.base.internal.compile.Compiler;
import org.gradle.play.internal.ScalaUtil;

import java.io.File;
import java.io.Serializable;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public class PlayRoutesCompiler implements Compiler<PlayRoutesCompileSpec>, Serializable {
    public WorkResult execute(PlayRoutesCompileSpec spec) {
        boolean didWork = false;
        File generatedDirectory = spec.getDestinationDir();

        List<String> additionalImports = new ArrayList<String>();
        additionalImports.addAll(spec.getAdditionalImports());

        Boolean generateReverseRoute= spec.getGenerateReverseRoute();
        boolean generateRefReverseRouter = spec.isGenerateRefReverseRouter();
        boolean namespaceReverseRouter = spec.isNamespaceReverseRouter();

        try {
            ClassLoader cl = getClass().getClassLoader();
            Class<?> bufferClass = cl.loadClass("scala.collection.mutable.ListBuffer");
            Object buffer = bufferClass.newInstance();
            Method bufferPlusEq = bufferClass.getMethod("$plus$eq", Object.class);

            if (additionalImports != null) {
                for (String additionalImport : additionalImports) {
                    bufferPlusEq.invoke(buffer, additionalImport);
                }
            }

            Method bufferToSeq = bufferClass.getMethod("toSeq");
            Object bufferSeq = bufferToSeq.invoke(buffer);

            Class<?> seqClass = cl.loadClass("scala.collection.Seq");

            Iterable<File> sources = spec.getSources();

            Function<Object[], Object> compile = ScalaUtil.scalaObjectFunction(
                    cl,
                    "play.router.RoutesCompiler", //TODO: changes to new package name soon.
                    "compile",
                    new Class<?>[]{
                            File.class, //input
                            generatedDirectory.getClass(),
                            seqClass,
                            boolean.class,
                            boolean.class,
                            boolean.class
                    }
            );

            for (File sourceFile : sources) {
                compile.apply(new Object[]{
                        sourceFile,
                        generatedDirectory,
                        bufferSeq,
                        generateReverseRoute,
                        generateRefReverseRouter,
                        namespaceReverseRouter
                });
            }
        } catch (Exception e) {
            throw new RuntimeException(e.getCause());
        }

        return new SimpleWorkResult(didWork);
    }
}

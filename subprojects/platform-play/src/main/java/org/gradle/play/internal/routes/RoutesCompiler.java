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
import org.gradle.api.internal.tasks.SimpleWorkResult;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.WorkResult;
import org.gradle.language.base.internal.compile.Compiler;
import org.gradle.scala.internal.reflect.ScalaMethod;
import org.gradle.util.CollectionUtils;

import java.io.File;
import java.io.Serializable;
import java.util.ArrayList;

public class RoutesCompiler implements Compiler<VersionedRoutesCompileSpec>, Serializable {
    public WorkResult execute(VersionedRoutesCompileSpec spec) {
        boolean didWork = false;
        Iterable<File> sources = spec.getSources();

        ArrayList<File> secondaryRoutes = Lists.newArrayList();
        CollectionUtils.filter(sources, secondaryRoutes, new Spec<File>() {
            @Override
            public boolean isSatisfiedBy(File file) {
                return !file.getName().equals("routes");
            }
        });

        ArrayList<File> routes = Lists.newArrayList();
        CollectionUtils.filter(sources, secondaryRoutes, new Spec<File>() {
            @Override
            public boolean isSatisfiedBy(File file) {
                return file.getName().equals("routes");
            }
        });

        // Compile all secondary routes files first
        for (File sourceFile : secondaryRoutes) {
            Boolean ret = compile(sourceFile, spec);
            didWork = ret || didWork;
        }

        // Compile all main routes files last
        for (File sourceFile : routes) {
            Boolean ret = compile(sourceFile, spec);
            didWork = ret || didWork;
        }

        return new SimpleWorkResult(didWork);
    }

    private Boolean compile(File sourceFile, VersionedRoutesCompileSpec spec) {

        try {
            ClassLoader cl = getClass().getClassLoader();
            ScalaMethod compile = spec.getCompileMethod(cl);
            Object ret = compile.invoke(spec.createCompileParameters(cl, sourceFile));
            if (ret != null && ret instanceof Boolean) {
                return (Boolean) ret;
            } else {
                return true; //assume we did some work
            }
        } catch (Exception e) {
            throw new RuntimeException("Error invoking the Play routes compiler.", e);
        }
    }
}

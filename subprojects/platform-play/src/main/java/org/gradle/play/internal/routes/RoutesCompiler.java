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
import org.gradle.play.internal.routes.spec.RoutesCompileSpec;

import java.io.File;
import java.io.Serializable;

public class RoutesCompiler implements Compiler<RoutesCompileSpec>, Serializable {
    public WorkResult execute(RoutesCompileSpec spec) {
        boolean didWork = false;

        try {
            ClassLoader cl = getClass().getClassLoader();
            Iterable<File> sources = spec.getSources();
            Function<Object[], Object> compile = spec.getCompileMethod(cl);
            for (File sourceFile : sources) {
                Object ret = compile.apply(spec.createCompileParameters(cl, sourceFile));
                if (ret != null && ret instanceof Boolean) {
                    didWork = (Boolean) ret || didWork;
                } else {
                    didWork = true; //assume we did some work
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Error invoking the Play routes compiler.", e);
        }

        return new SimpleWorkResult(didWork);
    }

}

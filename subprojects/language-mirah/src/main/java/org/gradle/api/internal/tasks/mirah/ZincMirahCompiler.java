/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.api.internal.tasks.mirah;

import org.gradle.api.internal.tasks.SimpleWorkResult;
import org.gradle.api.internal.tasks.compile.CompilationFailedException;
import org.gradle.language.base.internal.compile.Compiler;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.tasks.WorkResult;

import org.mirah.tool.Mirahc;

import java.io.File;
import java.io.Serializable;
import java.util.List;

public class ZincMirahCompiler implements Compiler<MirahJavaJointCompileSpec>, Serializable {
    private static final Logger LOGGER = Logging.getLogger(ZincMirahCompiler.class);
    private final Iterable<File> mirahClasspath;

    public ZincMirahCompiler(Iterable<File> mirahClasspath) {
        this.mirahClasspath = mirahClasspath;
    }

    public WorkResult execute(MirahJavaJointCompileSpec spec) {
        return Compiler.execute(mirahClasspath, spec);
    }

    // need to defer loading of Zinc/sbt/Mirah classes until we are
    // running in the compiler daemon and have them on the class path
    private static class Compiler {
        static WorkResult execute(Iterable<File> mirahClasspath, MirahJavaJointCompileSpec spec) {
            LOGGER.info("Compiling with Zinc Mirah compiler.");
            java.util.logging.Logger.getLogger("org.mirah").setLevel(java.util.logging.Level.OFF); // Logging is disabled by default.

            org.mirah.tool.Mirahc compiler = new Mirahc();
            List<String> mirahcOptions = new MirahCompilerArgumentsBuilder(spec).build();
            /*
            List<String> javacOptions = new JavaCompilerArgumentsBuilder(spec).includeClasspath(false).build();
            Inputs inputs = Inputs.create(ImmutableList.copyOf(spec.getClasspath()), ImmutableList.copyOf(spec.getSource()), spec.getDestinationDir(),
                    mirahcOptions, javacOptions, spec.getMirahCompileOptions().getIncrementalOptions().getAnalysisFile(), spec.getAnalysisMap(), "mixed", true);
            */

            int result = compiler.compile(mirahcOptions.toArray(new String[0]));
            
            if (result!=0) {
                throw new CompilationFailedException();
            }

            return new SimpleWorkResult(true);
        }
    }
}

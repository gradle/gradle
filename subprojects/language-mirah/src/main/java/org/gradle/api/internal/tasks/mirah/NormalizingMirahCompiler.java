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

import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import org.gradle.api.internal.file.collections.SimpleFileCollection;
import org.gradle.api.internal.tasks.SimpleWorkResult;
import org.gradle.api.internal.tasks.compile.CompilationFailedException;
import org.gradle.api.internal.tasks.compile.JavaCompilerArgumentsBuilder;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.tasks.WorkResult;
import org.gradle.language.base.internal.compile.Compiler;
import org.gradle.util.CollectionUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * A Mirah {@link org.gradle.language.base.internal.compile.Compiler} which does some normalization of the compile configuration and behaviour before delegating to some other compiler.
 */
public class NormalizingMirahCompiler implements Compiler<MirahJavaJointCompileSpec> {
    private static final Logger LOGGER = Logging.getLogger(NormalizingMirahCompiler.class);
    private final Compiler<MirahJavaJointCompileSpec> delegate;

    public NormalizingMirahCompiler(Compiler<MirahJavaJointCompileSpec> delegate) {
        this.delegate = delegate;
    }

    public WorkResult execute(MirahJavaJointCompileSpec spec) {
        resolveAndFilterSourceFiles(spec);
        resolveClasspath(spec);
        resolveNonStringsInCompilerArgs(spec);
        logSourceFiles(spec);
        logCompilerArguments(spec);
        return delegateAndHandleErrors(spec);
    }

    private void resolveAndFilterSourceFiles(final MirahJavaJointCompileSpec spec) {
        spec.setSource(new SimpleFileCollection(spec.getSource().getFiles()));
    }

    private void resolveClasspath(MirahJavaJointCompileSpec spec) {
        ArrayList<File> classPath = Lists.newArrayList(spec.getClasspath());
        classPath.add(spec.getDestinationDir());
        spec.setClasspath(classPath);

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Class path: {}", spec.getClasspath());
        }
    }

    private void resolveNonStringsInCompilerArgs(MirahJavaJointCompileSpec spec) {
        // in particular, this is about GStrings
        spec.getCompileOptions().setCompilerArgs(CollectionUtils.toStringList(spec.getCompileOptions().getCompilerArgs()));
    }

    private void logSourceFiles(MirahJavaJointCompileSpec spec) {
        if (!spec.getMirahCompileOptions().isListFiles()) {
            return;
        }

        StringBuilder builder = new StringBuilder();
        builder.append("Source files to be compiled:");
        for (File file : spec.getSource()) {
            builder.append('\n');
            builder.append(file);
        }

        LOGGER.quiet(builder.toString());
    }

    private void logCompilerArguments(MirahJavaJointCompileSpec spec) {
        if (!LOGGER.isDebugEnabled()) {
            return;
        }

        List<String> compilerArgs = new JavaCompilerArgumentsBuilder(spec).includeLauncherOptions(true).includeSourceFiles(true).build();
        String joinedArgs = Joiner.on(' ').join(compilerArgs);
        LOGGER.debug("Java compiler arguments: {}", joinedArgs);
    }

    private WorkResult delegateAndHandleErrors(MirahJavaJointCompileSpec spec) {
        try {
            return delegate.execute(spec);
        } catch (CompilationFailedException e) {
            if (spec.getMirahCompileOptions().isFailOnError()) {
                throw e;
            }
            LOGGER.debug("Ignoring compilation failure.");
            return new SimpleWorkResult(false);
        }
    }
}

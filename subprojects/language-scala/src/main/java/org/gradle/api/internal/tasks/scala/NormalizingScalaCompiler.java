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

package org.gradle.api.internal.tasks.scala;

import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import org.gradle.api.internal.file.collections.SimpleFileCollection;
import org.gradle.api.internal.tasks.compile.CompilationFailedException;
import org.gradle.api.internal.tasks.compile.JavaCompilerArgumentsBuilder;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.tasks.WorkResult;
import org.gradle.api.tasks.WorkResults;
import org.gradle.language.base.internal.compile.Compiler;
import org.gradle.util.CollectionUtils;

import java.io.File;
import java.util.List;

/**
 * A Scala {@link org.gradle.language.base.internal.compile.Compiler} which does some normalization of the compile configuration and behaviour before delegating to some other compiler.
 */
public class NormalizingScalaCompiler implements Compiler<ScalaJavaJointCompileSpec> {
    private static final Logger LOGGER = Logging.getLogger(NormalizingScalaCompiler.class);
    private final Compiler<ScalaJavaJointCompileSpec> delegate;

    public NormalizingScalaCompiler(Compiler<ScalaJavaJointCompileSpec> delegate) {
        this.delegate = delegate;
    }

    @Override
    public WorkResult execute(ScalaJavaJointCompileSpec spec) {
        resolveAndFilterSourceFiles(spec);
        resolveClasspath(spec);
        resolveNonStringsInCompilerArgs(spec);
        logSourceFiles(spec);
        logCompilerArguments(spec);
        return delegateAndHandleErrors(spec);
    }

    private void resolveAndFilterSourceFiles(final ScalaJavaJointCompileSpec spec) {
        spec.setSource(new SimpleFileCollection(spec.getSource().getFiles()));
    }

    private void resolveClasspath(ScalaJavaJointCompileSpec spec) {
        List<File> classPath = Lists.newArrayList(spec.getCompileClasspath());
        classPath.add(spec.getDestinationDir());
        spec.setCompileClasspath(classPath);

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Class path: {}", spec.getCompileClasspath());
        }
    }

    private void resolveNonStringsInCompilerArgs(ScalaJavaJointCompileSpec spec) {
        // in particular, this is about GStrings
        spec.getCompileOptions().setCompilerArgs(CollectionUtils.toStringList(spec.getCompileOptions().getCompilerArgs()));
    }

    private void logSourceFiles(ScalaJavaJointCompileSpec spec) {
        if (!spec.getScalaCompileOptions().isListFiles()) {
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

    private void logCompilerArguments(ScalaJavaJointCompileSpec spec) {
        if (!LOGGER.isDebugEnabled()) {
            return;
        }

        List<String> compilerArgs = new JavaCompilerArgumentsBuilder(spec).includeLauncherOptions(true).includeSourceFiles(true).build();
        String joinedArgs = Joiner.on(' ').join(compilerArgs);
        LOGGER.debug("Java compiler arguments: {}", joinedArgs);
    }

    private WorkResult delegateAndHandleErrors(ScalaJavaJointCompileSpec spec) {
        try {
            return delegate.execute(spec);
        } catch (CompilationFailedException e) {
            if (spec.getCompileOptions().isFailOnError() && spec.getScalaCompileOptions().isFailOnError()) {
                throw e;
            }
            LOGGER.debug("Ignoring compilation failure.");
            return WorkResults.didWork(false);
        }
    }
}

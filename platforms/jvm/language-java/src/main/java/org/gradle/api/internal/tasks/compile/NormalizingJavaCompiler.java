/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.api.internal.tasks.compile;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import org.apache.commons.lang.StringUtils;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.tasks.WorkResult;
import org.gradle.api.tasks.WorkResults;
import org.gradle.language.base.internal.compile.Compiler;
import org.gradle.util.internal.CollectionUtils;

import javax.annotation.Nullable;
import java.io.File;
import java.util.List;
import java.util.stream.Collectors;

import static org.gradle.internal.FileUtils.hasExtension;

/**
 * A Java {@link Compiler} which does some normalization of the compile configuration and behaviour before delegating to some other compiler.
 */
public class NormalizingJavaCompiler implements Compiler<JavaCompileSpec> {
    private static final Logger LOGGER = Logging.getLogger(NormalizingJavaCompiler.class);
    private final Compiler<JavaCompileSpec> delegate;

    public NormalizingJavaCompiler(Compiler<JavaCompileSpec> delegate) {
        this.delegate = delegate;
    }

    @Override
    public WorkResult execute(JavaCompileSpec spec) {
        resolveAndFilterSourceFiles(spec);
        resolveNonStringsInCompilerArgs(spec);
        logSourceFiles(spec);
        logCompilerArguments(spec);
        return delegateAndHandleErrors(spec);
    }

    private void resolveAndFilterSourceFiles(JavaCompileSpec spec) {
        // this mimics the behavior of the Ant javac task (and therefore AntJavaCompiler),
        // which silently excludes files not ending in .java
        Iterable<File> javaOnly = Iterables.filter(spec.getSourceFiles(), NormalizingJavaCompiler::hasJavaExtension);
        spec.setSourceFiles(ImmutableSet.copyOf(javaOnly));
    }

    private static boolean hasJavaExtension(@Nullable File input) {
        return hasExtension(input, ".java");
    }

    private void resolveNonStringsInCompilerArgs(JavaCompileSpec spec) {
        // in particular, this is about GStrings
        spec.getCompileOptions().setCompilerArgs(CollectionUtils.toStringList(spec.getCompileOptions().getCompilerArgs()));
    }

    private void logSourceFiles(JavaCompileSpec spec) {
        if (!spec.getCompileOptions().isListFiles()) {
            return;
        }

        StringBuilder builder = new StringBuilder();
        builder.append("Source files to be compiled:");
        for (File file : spec.getSourceFiles()) {
            builder.append('\n');
            builder.append(file);
        }

        LOGGER.quiet(builder.toString());
    }

    private void logCompilerArguments(JavaCompileSpec spec) {
        if (!LOGGER.isDebugEnabled()) {
            return;
        }

        List<String> compilerArgs = new JavaCompilerArgumentsBuilder(spec).includeLauncherOptions(true).includeSourceFiles(true).build();
        String joinedArgs = compilerArgs.stream().map(it -> StringUtils.isBlank(it) ? ('"' + it + '"') : it).collect(Collectors.joining(" "));
        LOGGER.debug("Compiler arguments: {}", joinedArgs);
    }

    private WorkResult delegateAndHandleErrors(JavaCompileSpec spec) {
        try {
            return delegate.execute(spec);
        } catch (CompilationFailedException e) {
            if (spec.getCompileOptions().isFailOnError()) {
                throw e;
            }
            LOGGER.debug("Ignoring compilation failure.");
            return WorkResults.didWork(false);
        }
    }
}

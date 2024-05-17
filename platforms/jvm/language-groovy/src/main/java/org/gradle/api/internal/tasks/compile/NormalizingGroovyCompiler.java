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
package org.gradle.api.internal.tasks.compile;

import com.google.common.base.Joiner;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.tasks.WorkResult;
import org.gradle.api.tasks.WorkResults;
import org.gradle.language.base.internal.compile.Compiler;
import org.gradle.util.internal.CollectionUtils;

import java.io.File;
import java.util.List;

import static org.gradle.internal.FileUtils.hasExtension;

/**
 * A Groovy {@link Compiler} which does some normalization of the compile configuration and behaviour before delegating to some other compiler.
 */
public class NormalizingGroovyCompiler implements Compiler<GroovyJavaJointCompileSpec> {
    private static final Logger LOGGER = Logging.getLogger(NormalizingGroovyCompiler.class);
    private final Compiler<GroovyJavaJointCompileSpec> delegate;

    public NormalizingGroovyCompiler(Compiler<GroovyJavaJointCompileSpec> delegate) {
        this.delegate = delegate;
    }

    @Override
    public WorkResult execute(GroovyJavaJointCompileSpec spec) {
        resolveAndFilterSourceFiles(spec);
        resolveNonStringsInCompilerArgs(spec);
        logSourceFiles(spec);
        logCompilerArguments(spec);
        return delegateAndHandleErrors(spec);
    }

    private void resolveAndFilterSourceFiles(final GroovyJavaJointCompileSpec spec) {
        final List<String> fileExtensions = CollectionUtils.collect(spec.getGroovyCompileOptions().getFileExtensions(), extension -> '.' + extension);
        Iterable<File> filtered = Iterables.filter(spec.getSourceFiles(), new Predicate<File>() {
            @Override
            public boolean apply(File element) {
                for (String fileExtension : fileExtensions) {
                    if (hasExtension(element, fileExtension)) {
                        return true;
                    }
                }
                return false;
            }
        });

        spec.setSourceFiles(ImmutableSet.copyOf(filtered));
    }

    private void resolveNonStringsInCompilerArgs(GroovyJavaJointCompileSpec spec) {
        // in particular, this is about GStrings
        spec.getCompileOptions().setCompilerArgs(CollectionUtils.toStringList(spec.getCompileOptions().getCompilerArgs()));
    }

    private void logSourceFiles(GroovyJavaJointCompileSpec spec) {
        if (!spec.getGroovyCompileOptions().isListFiles()) {
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

    private void logCompilerArguments(GroovyJavaJointCompileSpec spec) {
        if (!LOGGER.isDebugEnabled()) {
            return;
        }

        List<String> compilerArgs = new JavaCompilerArgumentsBuilder(spec).includeLauncherOptions(true).includeSourceFiles(true).build();
        String joinedArgs = Joiner.on(' ').join(compilerArgs);
        LOGGER.debug("Java compiler arguments: {}", joinedArgs);
    }

    private WorkResult delegateAndHandleErrors(GroovyJavaJointCompileSpec spec) {
        try {
            return delegate.execute(spec);
        } catch (RuntimeException e) {
            // in-process Groovy compilation throws a CompilationFailedException from another classloader, hence testing class name equality
            // TODO:pm Prefer class over class name for equality check once using WorkerExecutor for in-process groovy compilation
            if ((spec.getCompileOptions().isFailOnError() && spec.getGroovyCompileOptions().isFailOnError())
                || (!CompilationFailedException.class.getName().equals(e.getClass().getName()))) {
                throw e;
            }
            LOGGER.debug("Ignoring compilation failure.");
            return WorkResults.didWork(false);
        }
    }
}

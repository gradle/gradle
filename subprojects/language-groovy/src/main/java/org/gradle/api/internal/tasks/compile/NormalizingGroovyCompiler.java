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
import com.google.common.collect.Lists;
import org.gradle.api.Transformer;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.file.collections.SimpleFileCollection;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.WorkResult;
import org.gradle.api.tasks.WorkResults;
import org.gradle.language.base.internal.compile.Compiler;
import org.gradle.util.CollectionUtils;

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
        resolveClasspath(spec);
        resolveNonStringsInCompilerArgs(spec);
        logSourceFiles(spec);
        logCompilerArguments(spec);
        return delegateAndHandleErrors(spec);
    }

    private void resolveAndFilterSourceFiles(final GroovyJavaJointCompileSpec spec) {
        final List<String> fileExtensions = CollectionUtils.collect(spec.getGroovyCompileOptions().getFileExtensions(), new Transformer<String, String>() {
            @Override
            public String transform(String extension) {
                return '.' + extension;
            }
        });
        FileCollection filtered = spec.getSource().filter(new Spec<File>() {
            public boolean isSatisfiedBy(File element) {
                for (String fileExtension : fileExtensions) {
                    if (hasExtension(element, fileExtension)) {
                        return true;
                    }
                }
                return false;
            }
        });

        spec.setSource(new SimpleFileCollection(filtered.getFiles()));
    }

    private void resolveClasspath(GroovyJavaJointCompileSpec spec) {
        // Necessary for Groovy compilation to pick up output of regular and joint Java compilation,
        // and for joint Java compilation to pick up the output of regular Java compilation.
        // Assumes that output of regular Java compilation (which is not under this task's control) also goes
        // into spec.getDestinationDir(). We could configure this on source set level, but then spec.getDestinationDir()
        // would end up on the compile class path of every compile task for that source set, which may not be desirable.
        List<File> classPath = Lists.newArrayList(spec.getCompileClasspath());
        classPath.add(spec.getDestinationDir());
        spec.setCompileClasspath(classPath);
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
        for (File file : spec.getSource()) {
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

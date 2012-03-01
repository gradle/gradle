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

import com.google.common.collect.Lists;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.internal.file.collections.SimpleFileCollection;
import org.gradle.api.tasks.WorkResult;
import org.gradle.util.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

/**
 * A Java {@link Compiler} which does some normalization of the compile configuration and behaviour before delegating to some other compiler.
 */
public class NormalizingJavaCompiler implements Compiler<JavaCompileSpec> {
    private static final Logger LOGGER = LoggerFactory.getLogger(NormalizingJavaCompiler.class);
    private final Compiler<JavaCompileSpec> delegate;

    public NormalizingJavaCompiler(Compiler<JavaCompileSpec> delegate) {
        this.delegate = delegate;
    }

    public WorkResult execute(JavaCompileSpec spec) {
        resolveFileCollections(spec);
        resolveGStringsInCompilerArgs(spec);
        checkOnlyJavaSourceFiles(spec);
        listSourceFilesIfRequested(spec);
        return delegateAndHandleErrors(spec);
    }

    private void resolveFileCollections(JavaCompileSpec spec) {
        spec.setSource(new SimpleFileCollection(spec.getSource().getFiles()));
        spec.setClasspath(new SimpleFileCollection(Lists.newArrayList(spec.getClasspath())));
    }

    private void resolveGStringsInCompilerArgs(JavaCompileSpec spec) {
        spec.getCompileOptions().setCompilerArgs(CollectionUtils.toStringList(spec.getCompileOptions().getCompilerArgs()));
    }

    private void checkOnlyJavaSourceFiles(JavaCompileSpec spec) {
        for (File file : spec.getSource()) {
            if (!file.getName().endsWith(".java")) {
                throw new InvalidUserDataException(String.format("Cannot compile non-Java source file '%s'.", file));
            }
        }
    }

    private void listSourceFilesIfRequested(JavaCompileSpec spec) {
        if (!spec.getCompileOptions().isListFiles()) { return; }

        StringBuilder builder = new StringBuilder();
        builder.append("Source files to be compiled:");
        for (File file : spec.getSource()) {
            builder.append('\n');
            builder.append(file);
        }
        // logging happening in compiler daemon is not yet rerouted to client, hence we use println
        System.out.println(builder.toString());
    }

    private WorkResult delegateAndHandleErrors(JavaCompileSpec spec) {
        try {
            return delegate.execute(spec);
        } catch (CompilationFailedException e) {
            if (spec.getCompileOptions().isFailOnError()) {
                throw e;
            }
            LOGGER.debug("Ignoring compilation failure.");
            return new SimpleWorkResult(false);
        }
    }
}

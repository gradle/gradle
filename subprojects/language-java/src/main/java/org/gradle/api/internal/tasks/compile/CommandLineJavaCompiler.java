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

import org.gradle.api.tasks.WorkResult;
import org.gradle.api.tasks.WorkResults;
import org.gradle.language.base.internal.compile.Compiler;
import org.gradle.process.ExecResult;
import org.gradle.process.internal.ExecHandle;
import org.gradle.process.internal.ExecHandleBuilder;
import org.gradle.process.internal.ExecHandleFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;

/**
 * Executes the Java command line compiler executable.
 */
public class CommandLineJavaCompiler implements Compiler<JavaCompileSpec>, Serializable {
    private static final Logger LOGGER = LoggerFactory.getLogger(CommandLineJavaCompiler.class);

    private final CompileSpecToArguments<JavaCompileSpec> argumentsGenerator = new CommandLineJavaCompilerArgumentsGenerator();
    private final ExecHandleFactory execHandleFactory;

    public CommandLineJavaCompiler(ExecHandleFactory execHandleFactory) {
        this.execHandleFactory = execHandleFactory;
    }

    @Override
    public WorkResult execute(JavaCompileSpec spec) {
        if (!(spec instanceof CommandLineJavaCompileSpec)) {
            throw new IllegalArgumentException(String.format("Expected a %s, but got %s", CommandLineJavaCompileSpec.class.getSimpleName(), spec.getClass().getSimpleName()));
        }

        String executable = ((CommandLineJavaCompileSpec) spec).getExecutable().toString();
        LOGGER.info("Compiling with Java command line compiler '{}'.", executable);

        ExecHandle handle = createCompilerHandle(executable, spec);
        executeCompiler(handle);

        return WorkResults.didWork(true);
    }

    private ExecHandle createCompilerHandle(String executable, JavaCompileSpec spec) {
        ExecHandleBuilder builder = execHandleFactory.newExec();
        builder.setWorkingDir(spec.getWorkingDir());
        builder.setExecutable(executable);
        argumentsGenerator.collectArguments(spec, new ExecSpecBackedArgCollector(builder));
        builder.setIgnoreExitValue(true);
        return builder.build();
    }

    private void executeCompiler(ExecHandle handle) {
        handle.start();
        ExecResult result = handle.waitForFinish();
        if (result.getExitValue() != 0) {
            throw new CompilationFailedException(result.getExitValue());
        }
    }
}

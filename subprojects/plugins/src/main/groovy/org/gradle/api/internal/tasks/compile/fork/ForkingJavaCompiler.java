/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.api.internal.tasks.compile.fork;

import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.file.collections.SimpleFileCollection;
import org.gradle.api.internal.tasks.compile.JavaCompiler;
import org.gradle.api.tasks.WorkResult;
import org.gradle.api.tasks.compile.CompileOptions;
import org.gradle.api.tasks.compile.ForkOptions;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.process.internal.JavaExecHandleBuilder;
import org.gradle.process.internal.WorkerProcess;
import org.gradle.process.internal.WorkerProcessBuilder;

import java.io.File;

public class ForkingJavaCompiler implements JavaCompiler {
    private final ServiceRegistry services;
    private final File workingDir;
    
    private final CompilationAction action = new CompilationAction();
    private CompilationResult result;

    public ForkingJavaCompiler(ServiceRegistry services, File workingDir) {
        this.services = services;
        this.workingDir = workingDir;
    }

    public void setDependencyCacheDir(File dir) {
        /* do nothing */
    }

    public CompileOptions getCompileOptions() {
        return action.getCompileOptions();
    }

    public void setCompileOptions(CompileOptions compileOptions) {
        action.setCompileOptions(compileOptions);
    }

    public void setSourceCompatibility(String sourceCompatibility) {
        action.setSourceCompatibility(sourceCompatibility);
    }

    public void setTargetCompatibility(String targetCompatibility) {
        action.setTargetCompatibility(targetCompatibility);
    }

    public void setClasspath(Iterable<File> classpath) {
        action.setClasspath(classpath);
    }

    public void setSource(FileCollection source) {
        action.setSource(new SimpleFileCollection(source.getFiles()));
    }

    public void setDestinationDir(File destinationDir) {
        action.setDestinationDir(destinationDir);
    }

    public WorkResult execute() {
        WorkerProcess process = createWorkerProcess();
        process.start();
        // TODO: only works when done after start() - does this risk to lose some messages?
        registerCompilationListener(process);
        process.waitForStop();

        if (result.isSuccess()) {
            return result;
        }

        throw UncheckedException.asUncheckedException(result.getException());
    }

    private WorkerProcess createWorkerProcess() {
        WorkerProcessBuilder builder = services.getFactory(WorkerProcessBuilder.class).create();
        ForkOptions forkOptions = getCompileOptions().getForkOptions();
        JavaExecHandleBuilder javaCommand = builder.getJavaCommand();
        javaCommand.setMinHeapSize(forkOptions.getMemoryInitialSize());
        javaCommand.setMaxHeapSize(forkOptions.getMemoryMaximumSize());
        javaCommand.setJvmArgs(forkOptions.getJvmArgs());
        javaCommand.setWorkingDir(workingDir); // TODO: w/o setting this, we get a "cannot resolve '.' to absolute path" exception
        return builder.worker(action.makeSerializable()).build();
    }

    private void registerCompilationListener(WorkerProcess process) {
        process.getConnection().addIncoming(CompilationListener.class, new CompilationListener() {
            public void stdOut(CharSequence message) {
                System.out.print(message);
            }

            public void stdErr(CharSequence message) {
                System.err.print(message);
            }

            public void completed(CompilationResult result11) {
                ForkingJavaCompiler.this.result = result11;
            }
        });
    }
}

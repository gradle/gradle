package org.gradle.api.internal.tasks.compile.remote;

import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.tasks.compile.JavaCompiler;
import org.gradle.api.tasks.WorkResult;
import org.gradle.api.tasks.compile.CompileOptions;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.process.internal.WorkerProcess;
import org.gradle.process.internal.WorkerProcessBuilder;

import java.io.File;

public class ForkingJavaCompiler implements JavaCompiler {
    private final ServiceRegistry services;
    
    private final CompilationAction action = new CompilationAction();
    private CompilationResult result;

    public ForkingJavaCompiler(ServiceRegistry services) {
        this.services = services;
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
        action.setSource(source);
    }

    public void setDestinationDir(File destinationDir) {
        action.setDestinationDir(destinationDir);
    }

    public WorkResult execute() {
        WorkerProcessBuilder builder = services.get(WorkerProcessBuilder.class);
        builder.worker(new CompilationAction());
        WorkerProcess process = builder.build();
        CompilationListener listener = new CompilationListener() {
            public void stdOut(CharSequence message) {
                System.out.println(message);
            }

            public void stdErr(CharSequence message) {
                System.err.println(message);
            }

            public void completed(CompilationResult result) {
                ForkingJavaCompiler.this.result = result;
            }
        };
        process.getConnection().addIncoming(CompilationListener.class, listener);
        process.start();
        process.waitForStop();
        return result;
    }
}

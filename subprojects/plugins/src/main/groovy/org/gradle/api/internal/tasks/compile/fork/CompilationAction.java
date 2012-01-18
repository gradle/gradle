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

import com.google.common.collect.Lists;
import org.gradle.api.Action;
import org.gradle.api.AntBuilder;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.file.collections.SimpleFileCollection;
import org.gradle.api.internal.project.ant.BasicAntBuilder;
import org.gradle.api.internal.tasks.compile.AntJavaCompiler;
import org.gradle.api.internal.tasks.compile.JavaCompiler;
import org.gradle.api.logging.StandardOutputListener;
import org.gradle.api.tasks.WorkResult;
import org.gradle.api.tasks.compile.CompileOptions;
import org.gradle.internal.Factory;
import org.gradle.logging.StandardOutputRedirector;
import org.gradle.logging.internal.DefaultStandardOutputRedirector;
import org.gradle.messaging.remote.ObjectConnection;
import org.gradle.process.internal.WorkerProcessContext;

import java.io.File;
import java.io.Serializable;

public class CompilationAction implements Action<WorkerProcessContext>, Serializable {
    private FileCollection source;
    private File destinationDir;
    private Iterable<File> classpath;
    private String sourceCompatibility;
    private String targetCompatibility;
    private CompileOptions compileOptions = new CompileOptions();

    public void setSource(FileCollection source) {
        this.source = source;
    }

    public void setDestinationDir(File destinationDir) {
        this.destinationDir = destinationDir;
    }

    public void setClasspath(Iterable<File> classpath) {
        this.classpath = classpath;
    }

    public void setSourceCompatibility(String sourceCompatibility) {
        this.sourceCompatibility = sourceCompatibility;
    }

    public void setTargetCompatibility(String targetCompatibility) {
        this.targetCompatibility = targetCompatibility;
    }

    public CompileOptions getCompileOptions() {
        return compileOptions;
    }

    public void setCompileOptions(CompileOptions compileOptions) {
        this.compileOptions = compileOptions;
    }

    public void execute(WorkerProcessContext workerProcessContext) {
        ObjectConnection connection = workerProcessContext.getServerConnection();
        final CompilationListener listener = connection.addOutgoing(CompilationListener.class);
        
        StandardOutputRedirector redirector = new DefaultStandardOutputRedirector();
        redirector.redirectStandardOutputTo(new StandardOutputListener() {
            public void onOutput(CharSequence output) {
                listener.stdOut(output);
            }
        });
        redirector.redirectStandardErrorTo(new StandardOutputListener() {
            public void onOutput(CharSequence output) {
                listener.stdErr(output);
            }
        });
        
        Factory<AntBuilder> antBuilderFactory = new Factory<AntBuilder>() {
            public AntBuilder create() {
                return new BasicAntBuilder();
            }
        };
        
        redirector.start();
        try {
            JavaCompiler javaCompiler = new AntJavaCompiler(antBuilderFactory);
            javaCompiler.setSource(source);
            javaCompiler.setDestinationDir(destinationDir);
            javaCompiler.setClasspath(classpath);
            javaCompiler.setSourceCompatibility(sourceCompatibility);
            javaCompiler.setTargetCompatibility(targetCompatibility);
            javaCompiler.setCompileOptions(compileOptions);
            WorkResult result = javaCompiler.execute();
            listener.completed(new CompilationResult(result.getDidWork(), null));
        } catch (Throwable t) {
            listener.completed(new CompilationResult(true, t));
        } finally {
            redirector.stop();
        }
    }

    public void makeSerializable() {
        if (!(source instanceof Serializable)) {
            source = new SimpleFileCollection(source.getFiles());
        }
        if (!(classpath instanceof Serializable)) {
            classpath = new SimpleFileCollection(Lists.newArrayList(classpath));
        }
    }
}

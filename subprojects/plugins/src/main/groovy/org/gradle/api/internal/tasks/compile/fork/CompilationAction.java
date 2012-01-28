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
import org.gradle.api.internal.Factory;
import org.gradle.api.internal.file.collections.SimpleFileCollection;
import org.gradle.api.internal.project.ant.BasicAntBuilder;
import org.gradle.api.internal.tasks.compile.AntJavaCompiler;
import org.gradle.api.internal.tasks.compile.JavaCompiler;
import org.gradle.api.internal.tasks.compile.JavaCompilerSupport;
import org.gradle.api.tasks.WorkResult;
import org.gradle.messaging.remote.ObjectConnection;
import org.gradle.process.internal.WorkerProcessContext;

import java.io.Serializable;

public class CompilationAction extends JavaCompilerSupport implements Action<WorkerProcessContext>, Serializable {
    public WorkResult execute() {
        throw new UnsupportedOperationException();
    }

    public void execute(WorkerProcessContext workerProcessContext) {
        ObjectConnection connection = workerProcessContext.getServerConnection();
        final CompilationListener listener = connection.addOutgoing(CompilationListener.class);
        
        Factory<AntBuilder> antBuilderFactory = new Factory<AntBuilder>() {
            public AntBuilder create() {
                return new BasicAntBuilder();
            }
        };
        
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
            connection.stop();
        }
    }

    public CompilationAction makeSerializable() {
        if (!(source instanceof Serializable)) {
            source = new SimpleFileCollection(source.getFiles());
        }
        if (!(classpath instanceof Serializable)) {
            classpath = new SimpleFileCollection(Lists.newArrayList(classpath));
        }
        return this;
    }
}

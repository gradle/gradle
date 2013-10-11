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

package org.gradle.api.internal.tasks.compile.daemon;

import org.gradle.api.internal.tasks.compile.CompileSpec;
import org.gradle.api.internal.tasks.compile.Compiler;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.classloader.*;
import org.gradle.internal.classpath.DefaultClassPath;
import org.gradle.internal.io.ClassLoaderObjectInputStream;
import org.gradle.util.GUtil;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.Serializable;
import java.util.concurrent.Callable;

public class InProcessCompilerDaemonFactory implements CompilerDaemonFactory {
    private static final InProcessCompilerDaemonFactory INSTANCE = new InProcessCompilerDaemonFactory();
    private final ClassLoaderFactory classLoaderFactory = new DefaultClassLoaderFactory();

    public static InProcessCompilerDaemonFactory getInstance() {
        return INSTANCE;
    }

    public CompilerDaemon getDaemon(File workingDir, final DaemonForkOptions forkOptions) {
        return new CompilerDaemon() {
            public <T extends CompileSpec> CompileResult execute(Compiler<T> compiler, T spec) {
                ClassLoader groovyClassLoader = classLoaderFactory.createIsolatedClassLoader(new DefaultClassPath(forkOptions.getClasspath()));
                FilteringClassLoader filteredGroovy = classLoaderFactory.createFilteringClassLoader(groovyClassLoader);
                for (String packageName : forkOptions.getSharedPackages()) {
                    filteredGroovy.allowPackage(packageName);
                }

                ClassLoader workerClassLoader = new MutableURLClassLoader(filteredGroovy, ClasspathUtil.getClasspath(compiler.getClass().getClassLoader()));

                try {
                    byte[] serializedWorker = GUtil.serialize(new Worker<T>(compiler, spec));
                    ClassLoaderObjectInputStream inputStream = new ClassLoaderObjectInputStream(new ByteArrayInputStream(serializedWorker), workerClassLoader);
                    Callable<?> worker = (Callable<?>) inputStream.readObject();
                    Object result = worker.call();
                    byte[] serializedResult = GUtil.serialize(result);
                    inputStream = new ClassLoaderObjectInputStream(new ByteArrayInputStream(serializedResult), getClass().getClassLoader());
                    return (CompileResult) inputStream.readObject();
                } catch (Exception e) {
                    throw UncheckedException.throwAsUncheckedException(e);
                }
            }
        };
    }

    private static class Worker<T extends CompileSpec> implements Callable<Object>, Serializable {
        private final Compiler<T> compiler;
        private final T spec;

        private Worker(Compiler<T> compiler, T spec) {
            this.compiler = compiler;
            this.spec = spec;
        }

        public Object call() throws Exception {
            return new CompileResult(compiler.execute(spec).getDidWork(), null);
        }
    }
}

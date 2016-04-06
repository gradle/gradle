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

import org.gradle.api.internal.classloading.GroovySystemLoader;
import org.gradle.api.internal.classloading.GroovySystemLoaderFactory;
import org.gradle.api.logging.LogLevel;
import org.gradle.api.logging.Logger;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.classloader.*;
import org.gradle.internal.classpath.DefaultClassPath;
import org.gradle.internal.io.ClassLoaderObjectInputStream;
import org.gradle.internal.nativeintegration.services.NativeServices;
import org.gradle.language.base.internal.compile.CompileSpec;
import org.gradle.language.base.internal.compile.Compiler;
import org.gradle.util.GUtil;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.Serializable;
import java.util.concurrent.Callable;

public class InProcessCompilerDaemonFactory implements CompilerDaemonFactory {
    private final ClassLoaderFactory classLoaderFactory;
    private final File gradleUserHomeDir;
    private final GroovySystemLoaderFactory groovySystemLoaderFactory = new GroovySystemLoaderFactory();

    public InProcessCompilerDaemonFactory(ClassLoaderFactory classLoaderFactory, File gradleUserHomeDir) {
        this.classLoaderFactory = classLoaderFactory;
        this.gradleUserHomeDir = gradleUserHomeDir;
    }

    @Override
    public CompilerDaemon getDaemon(File workingDir, final DaemonForkOptions forkOptions) {
        return new CompilerDaemon() {
            public <T extends CompileSpec> CompileResult execute(Compiler<T> compiler, T spec) {
                ClassLoader groovyClassLoader = classLoaderFactory.createIsolatedClassLoader(new DefaultClassPath(forkOptions.getClasspath()));
                GroovySystemLoader groovyLoader = groovySystemLoaderFactory.forClassLoader(groovyClassLoader);
                FilteringClassLoader filteredGroovy = classLoaderFactory.createFilteringClassLoader(groovyClassLoader);
                for (String packageName : forkOptions.getSharedPackages()) {
                    filteredGroovy.allowPackage(packageName);
                }

                FilteringClassLoader loggingClassLoader = classLoaderFactory.createFilteringClassLoader(compiler.getClass().getClassLoader());
                loggingClassLoader.allowPackage("org.slf4j");
                loggingClassLoader.allowClass(Logger.class);
                loggingClassLoader.allowClass(LogLevel.class);

                ClassLoader groovyAndLoggingClassLoader = new CachingClassLoader(new MultiParentClassLoader(loggingClassLoader, filteredGroovy));

                ClassLoader workerClassLoader = new MutableURLClassLoader(groovyAndLoggingClassLoader, ClasspathUtil.getClasspath(compiler.getClass().getClassLoader()));

                try {
                    byte[] serializedWorker = GUtil.serialize(new Worker<T>(compiler, spec, gradleUserHomeDir));
                    ClassLoaderObjectInputStream inputStream = new ClassLoaderObjectInputStream(new ByteArrayInputStream(serializedWorker), workerClassLoader);
                    Callable<?> worker = (Callable<?>) inputStream.readObject();
                    Object result = worker.call();
                    byte[] serializedResult = GUtil.serialize(result);
                    inputStream = new ClassLoaderObjectInputStream(new ByteArrayInputStream(serializedResult), getClass().getClassLoader());
                    return (CompileResult) inputStream.readObject();
                } catch (Exception e) {
                    throw UncheckedException.throwAsUncheckedException(e);
                } finally {
                    groovyLoader.shutdown();
                }
            }
        };
    }

    private static class Worker<T extends CompileSpec> implements Callable<Object>, Serializable {
        private final Compiler<T> compiler;
        private final T spec;
        private final File gradleUserHome;

        private Worker(Compiler<T> compiler, T spec, File gradleUserHome) {
            this.compiler = compiler;
            this.spec = spec;
            this.gradleUserHome = gradleUserHome;
        }

        @Override
        public Object call() throws Exception {
            // We have to initialize this here because we're in an isolated classloader
            NativeServices.initialize(gradleUserHome);
            return new CompileResult(compiler.execute(spec).getDidWork(), null);
        }
    }
}

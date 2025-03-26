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

package org.gradle.api.internal.tasks.scala;

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

import org.gradle.api.internal.ClassPathRegistry;
import org.gradle.api.internal.tasks.compile.BaseForkOptionsConverter;
import org.gradle.api.internal.tasks.compile.MinimalJavaCompilerDaemonForkOptions;
import org.gradle.api.internal.tasks.compile.daemon.AbstractDaemonCompiler;
import org.gradle.api.internal.tasks.compile.daemon.CompilerWorkerExecutor;
import org.gradle.initialization.ClassLoaderRegistry;
import org.gradle.internal.classloader.FilteringClassLoader;
import org.gradle.internal.classloader.VisitableURLClassLoader;
import org.gradle.internal.classpath.ClassPath;
import org.gradle.internal.classpath.DefaultClassPath;
import org.gradle.language.base.internal.compile.CompilerParameters;
import org.gradle.process.JavaForkOptions;
import org.gradle.process.internal.JavaForkOptionsFactory;
import org.gradle.workers.internal.DaemonForkOptions;
import org.gradle.workers.internal.DaemonForkOptionsBuilder;
import org.gradle.workers.internal.HierarchicalClassLoaderStructure;
import org.gradle.workers.internal.KeepAliveMode;

import java.io.File;

public class DaemonScalaCompiler<T extends ScalaJavaJointCompileSpec> extends AbstractDaemonCompiler<T> {

    private final HashedClasspath hashedScalaClasspath;
    private final Iterable<File> zincClasspath;
    private final JavaForkOptionsFactory forkOptionsFactory;
    private final File daemonWorkingDir;
    private final ClassPathRegistry classPathRegistry;
    private final ClassLoaderRegistry classLoaderRegistry;

    public DaemonScalaCompiler(File daemonWorkingDir, HashedClasspath hashedScalaClasspath, CompilerWorkerExecutor compilerWorkerExecutor, Iterable<File> zincClasspath, JavaForkOptionsFactory forkOptionsFactory, ClassPathRegistry classPathRegistry, ClassLoaderRegistry classLoaderRegistry) {
        super(compilerWorkerExecutor);
        this.hashedScalaClasspath = hashedScalaClasspath;
        this.zincClasspath = zincClasspath;
        this.forkOptionsFactory = forkOptionsFactory;
        this.daemonWorkingDir = daemonWorkingDir;
        this.classPathRegistry = classPathRegistry;
        this.classLoaderRegistry = classLoaderRegistry;
    }

    @Override
    protected CompilerParameters getCompilerParameters(T spec) {
        return new ScalaCompilerParameters<T>(ZincScalaCompilerFacade.class.getName(), new Object[] {hashedScalaClasspath}, spec);
    }

    @Override
    protected DaemonForkOptions toDaemonForkOptions(T spec) {
        MinimalJavaCompilerDaemonForkOptions javaOptions = spec.getCompileOptions().getForkOptions();
        MinimalScalaCompileOptions compileOptions = spec.getScalaCompileOptions();
        MinimalScalaCompilerDaemonForkOptions forkOptions = compileOptions.getForkOptions();
        JavaForkOptions javaForkOptions = new BaseForkOptionsConverter(forkOptionsFactory).transform(javaOptions, forkOptions);
        javaForkOptions.systemProperty("xsbt.skip.cp.lookup", true);
        javaForkOptions.setWorkingDir(daemonWorkingDir);
        javaForkOptions.setExecutable(spec.getJavaExecutable().getAbsolutePath());

        ClassPath compilerClasspath = classPathRegistry.getClassPath("SCALA-COMPILER").plus(DefaultClassPath.of(zincClasspath));

        HierarchicalClassLoaderStructure classLoaderStructure = new HierarchicalClassLoaderStructure(classLoaderRegistry.getGradleWorkerExtensionSpec())
                .withChild(getScalaFilterSpec())
                .withChild(new VisitableURLClassLoader.Spec("compiler", compilerClasspath.getAsURLs()));

        return new DaemonForkOptionsBuilder(forkOptionsFactory)
            .javaForkOptions(javaForkOptions)
            .withClassLoaderStructure(classLoaderStructure)
            .keepAliveMode(KeepAliveMode.valueOf(compileOptions.getKeepAliveMode()))
            .build();
    }

    private FilteringClassLoader.Spec getScalaFilterSpec() {
        FilteringClassLoader.Spec gradleApiAndScalaSpec = classLoaderRegistry.getGradleApiFilterSpec();

        // These should come from the compiler classloader
        gradleApiAndScalaSpec.disallowPackage("org.gradle.api.internal.tasks.scala");

        // Guava
        gradleApiAndScalaSpec.allowPackage("com.google");

        return gradleApiAndScalaSpec;
    }

}


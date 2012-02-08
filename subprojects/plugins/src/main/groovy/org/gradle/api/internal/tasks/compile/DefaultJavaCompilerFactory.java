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
package org.gradle.api.internal.tasks.compile;

import org.gradle.api.AntBuilder;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.tasks.compile.daemon.DaemonJavaCompiler;
import org.gradle.api.internal.tasks.compile.fork.ForkingJavaCompiler;
import org.gradle.api.tasks.compile.CompileOptions;
import org.gradle.internal.Factory;
import org.gradle.util.Jvm;

public class DefaultJavaCompilerFactory implements JavaCompilerFactory {
    private final ProjectInternal project;
    private final Factory<AntBuilder> antBuilderFactory;
    private final JavaCompilerFactory inProcessCompilerFactory;
    private Jvm jvmInstance;

    public DefaultJavaCompilerFactory(ProjectInternal project, Factory<AntBuilder> antBuilderFactory, JavaCompilerFactory inProcessCompilerFactory) {
        this(project, antBuilderFactory, inProcessCompilerFactory, Jvm.current());
    }

    DefaultJavaCompilerFactory(ProjectInternal project, Factory<AntBuilder> antBuilderFactory, JavaCompilerFactory inProcessCompilerFactory, Jvm jvm){
        this.project = project;
        this.antBuilderFactory = antBuilderFactory;
        this.inProcessCompilerFactory = inProcessCompilerFactory;
        this.jvmInstance = jvm;
    }

    public JavaCompiler create(CompileOptions options) {
        if (options.isUseAnt()) {
            return new AntJavaCompiler(antBuilderFactory);
        }
        JavaCompiler normalizingJavaCompiler = new NormalizingJavaCompiler(createTargetCompiler(options));
        if (jvmInstance.isJava7()) {
            return new Jdk7CompliantJavaCompiler(normalizingJavaCompiler);
        } else {
            return normalizingJavaCompiler;
        }
    }

    private JavaCompiler createTargetCompiler(CompileOptions options) {
        if (!options.isFork()) {
            return inProcessCompilerFactory.create(options);
        }
        if (options.getForkOptions().isUseCompilerDaemon()) {
            return new DaemonJavaCompiler(project, inProcessCompilerFactory.create(options));
        }
        return new ForkingJavaCompiler(project);
    }
}

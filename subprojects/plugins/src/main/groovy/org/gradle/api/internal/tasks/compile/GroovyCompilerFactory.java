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

import org.gradle.api.internal.ClassPathRegistry;
import org.gradle.api.internal.project.IsolatedAntBuilder;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.tasks.compile.daemon.DaemonGroovyCompiler;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.tasks.compile.CompileOptions;
import org.gradle.api.tasks.compile.GroovyCompileOptions;

public class GroovyCompilerFactory {
    private static final Logger LOGGER = Logging.getLogger(GroovyCompilerFactory.class);
    
    private final ProjectInternal project;
    private final IsolatedAntBuilder antBuilder;
    private final ClassPathRegistry classPathRegistry;
    private final DefaultJavaCompilerFactory javaCompilerFactory;

    public GroovyCompilerFactory(ProjectInternal project, IsolatedAntBuilder antBuilder, ClassPathRegistry classPathRegistry, DefaultJavaCompilerFactory javaCompilerFactory) {
        this.project = project;
        this.antBuilder = antBuilder;
        this.classPathRegistry = classPathRegistry;
        this.javaCompilerFactory = javaCompilerFactory;
    }

    Compiler<GroovyJavaJointCompileSpec> create(GroovyCompileOptions groovyOptions, CompileOptions javaOptions) {
        if (groovyOptions.isUseAnt()) {
            return new AntGroovyCompiler(antBuilder, classPathRegistry);
        }
        
        if (!groovyOptions.isFork()) {
            LOGGER.warn("Falling back to Ant groovyc task ('GroovyCompileOptions.useAnt = true') because 'GroovyCompileOptions.fork' is set to 'false'.");
            return new AntGroovyCompiler(antBuilder, classPathRegistry);
        }

        javaCompilerFactory.setGroovyJointCompilation(true);
        Compiler<JavaCompileSpec> javaCompiler = javaCompilerFactory.create(javaOptions);
        Compiler<GroovyJavaJointCompileSpec> groovyCompiler = new ApiGroovyCompiler(javaCompiler);
        groovyCompiler = new DaemonGroovyCompiler(project, groovyCompiler);
        return new NormalizingGroovyCompiler(groovyCompiler);
    }
}

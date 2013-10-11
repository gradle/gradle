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

import com.google.common.collect.Iterables;
import org.gradle.api.internal.ClassPathRegistry;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.tasks.compile.Compiler;
import org.gradle.api.internal.tasks.compile.GroovyJavaJointCompileSpec;
import org.gradle.api.tasks.WorkResult;
import org.gradle.api.tasks.compile.ForkOptions;
import org.gradle.api.tasks.compile.GroovyForkOptions;
import org.gradle.internal.UncheckedException;

import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

public class DaemonGroovyCompiler implements Compiler<GroovyJavaJointCompileSpec> {
    private final ProjectInternal project;
    private final Compiler<GroovyJavaJointCompileSpec> delegate;
    private final CompilerDaemonFactory daemonFactory;

    public DaemonGroovyCompiler(ProjectInternal project, Compiler<GroovyJavaJointCompileSpec> delegate, CompilerDaemonFactory daemonFactory) {
        this.project = project;
        this.delegate = delegate;
        this.daemonFactory = daemonFactory;
    }

    public WorkResult execute(GroovyJavaJointCompileSpec spec) {
        DaemonForkOptions daemonForkOptions = createDaemonForkOptions(spec);
        CompilerDaemon daemon = daemonFactory.getDaemon(project.getRootProject().getProjectDir(), daemonForkOptions);
        CompileResult result = daemon.execute(delegate, spec);
        if (result.isSuccess()) {
            return result;
        }
        throw UncheckedException.throwAsUncheckedException(result.getException());
    }

    private DaemonForkOptions createDaemonForkOptions(GroovyJavaJointCompileSpec spec) {
        return createJavaForkOptions(spec).mergeWith(createGroovyForkOptions(spec));
    }
    
    private DaemonForkOptions createJavaForkOptions(GroovyJavaJointCompileSpec spec) {
        ForkOptions options = spec.getCompileOptions().getForkOptions();
        return new DaemonForkOptions(options.getMemoryInitialSize(), options.getMemoryMaximumSize(), options.getJvmArgs());
    }

    private DaemonForkOptions createGroovyForkOptions(GroovyJavaJointCompileSpec spec) {
        GroovyForkOptions options = spec.getGroovyCompileOptions().getForkOptions();
        // Ant is optional dependency of groovy(-all) module but mandatory dependency of Groovy compiler;
        // that's why we add it here. The following assumes that any Groovy compiler version supported by Gradle
        // is compatible with Gradle's current Ant version.
        Collection<File> antFiles = project.getServices().get(ClassPathRegistry.class).getClassPath("ANT").getAsFiles();
        Iterable<File> groovyFiles = Iterables.concat(spec.getGroovyClasspath(), antFiles);
        List<String> groovyPackages = Arrays.asList("groovy", "org.codehaus.groovy", "groovyjarjarantlr", "groovyjarjarasm", "groovyjarjarcommonscli", "org.apache.tools.ant", "com.sun.tools.javac");
        return new DaemonForkOptions(options.getMemoryInitialSize(), options.getMemoryMaximumSize(),
                options.getJvmArgs(), groovyFiles, groovyPackages);
    }
}

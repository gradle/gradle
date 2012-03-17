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

import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.tasks.compile.Compiler;
import org.gradle.api.internal.tasks.compile.GroovyJavaJointCompileSpec;
import org.gradle.api.tasks.WorkResult;
import org.gradle.api.tasks.compile.ForkOptions;
import org.gradle.api.tasks.compile.GroovyForkOptions;
import org.gradle.internal.UncheckedException;

import java.io.File;
import java.util.Collections;

public class DaemonGroovyCompiler implements Compiler<GroovyJavaJointCompileSpec> {
    private final ProjectInternal project;
    private final Compiler<GroovyJavaJointCompileSpec> delegate;

    public DaemonGroovyCompiler(ProjectInternal project, Compiler<GroovyJavaJointCompileSpec> delegate) {
        this.project = project;
        this.delegate = delegate;
    }

    public WorkResult execute(GroovyJavaJointCompileSpec spec) {
        DaemonForkOptions daemonForkOptions = createDaemonForkOptions(spec);
        CompilerDaemon daemon = CompilerDaemonManager.getInstance().getDaemon(project, daemonForkOptions);
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
        return new DaemonForkOptions(options.getMemoryInitialSize(), options.getMemoryMaximumSize(), options.getJvmArgs(), Collections.<File>emptyList());
    }

    private DaemonForkOptions createGroovyForkOptions(GroovyJavaJointCompileSpec spec) {
        GroovyForkOptions options = spec.getGroovyCompileOptions().getForkOptions();
        return new DaemonForkOptions(options.getMemoryInitialSize(), options.getMemoryMaximumSize(), Collections.<String>emptyList(), spec.getGroovyClasspath());
    }
}

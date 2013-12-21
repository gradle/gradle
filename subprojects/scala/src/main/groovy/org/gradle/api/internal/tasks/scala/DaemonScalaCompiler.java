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

import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.tasks.compile.Compiler;
import org.gradle.api.internal.tasks.compile.daemon.CompileResult;
import org.gradle.api.internal.tasks.compile.daemon.CompilerDaemon;
import org.gradle.api.internal.tasks.compile.daemon.CompilerDaemonFactory;
import org.gradle.api.internal.tasks.compile.daemon.DaemonForkOptions;
import org.gradle.api.tasks.WorkResult;
import org.gradle.api.tasks.compile.ForkOptions;
import org.gradle.api.tasks.scala.ScalaForkOptions;
import org.gradle.internal.UncheckedException;

import java.util.Arrays;
import java.util.List;

public class DaemonScalaCompiler implements org.gradle.api.internal.tasks.compile.Compiler<ScalaJavaJointCompileSpec> {
    private final ProjectInternal project;
    private final Compiler<ScalaJavaJointCompileSpec> delegate;
    private final CompilerDaemonFactory daemonFactory;

    public DaemonScalaCompiler(ProjectInternal project, Compiler<ScalaJavaJointCompileSpec> delegate, CompilerDaemonFactory daemonFactory) {
        this.project = project;
        this.delegate = delegate;
        this.daemonFactory = daemonFactory;
    }

    public WorkResult execute(ScalaJavaJointCompileSpec spec) {
        DaemonForkOptions daemonForkOptions = createDaemonForkOptions(spec);
        CompilerDaemon daemon = daemonFactory.getDaemon(project.getRootProject().getProjectDir(), daemonForkOptions);
        CompileResult result = daemon.execute(delegate, spec);
        if (result.isSuccess()) {
            return result;
        }
        throw UncheckedException.throwAsUncheckedException(result.getException());
    }

    private DaemonForkOptions createDaemonForkOptions(ScalaJavaJointCompileSpec spec) {
        return createJavaForkOptions(spec).mergeWith(createScalaForkOptions(spec));
    }

    private DaemonForkOptions createJavaForkOptions(ScalaJavaJointCompileSpec spec) {
        ForkOptions options = spec.getCompileOptions().getForkOptions();
        return new DaemonForkOptions(options.getMemoryInitialSize(), options.getMemoryMaximumSize(), options.getJvmArgs());
    }

    private DaemonForkOptions createScalaForkOptions(ScalaJavaJointCompileSpec spec) {
        ScalaForkOptions options = spec.getScalaCompileOptions().getForkOptions();
        List<String> sharedPackages = Arrays.asList("scala", "com.typesafe.zinc", "xsbti", "com.sun.tools.javac");
        return new DaemonForkOptions(options.getMemoryInitialSize(), options.getMemoryMaximumSize(),
                options.getJvmArgs(), spec.getZincClasspath(), sharedPackages);
    }
}


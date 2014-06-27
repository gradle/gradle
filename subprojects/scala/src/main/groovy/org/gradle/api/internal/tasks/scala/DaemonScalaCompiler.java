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

import org.gradle.api.internal.tasks.compile.daemon.AbstractDaemonCompiler;
import org.gradle.api.internal.tasks.compile.daemon.CompilerDaemonFactory;
import org.gradle.api.internal.tasks.compile.daemon.DaemonForkOptions;
import org.gradle.api.tasks.compile.ForkOptions;
import org.gradle.api.tasks.scala.ScalaForkOptions;
import org.gradle.language.base.internal.compile.Compiler;

import java.io.File;
import java.util.Arrays;
import java.util.List;

public class DaemonScalaCompiler extends AbstractDaemonCompiler<ScalaJavaJointCompileSpec> {
    public DaemonScalaCompiler(File daemonWorkingDir, Compiler<ScalaJavaJointCompileSpec> delegate, CompilerDaemonFactory daemonFactory) {
        super(daemonWorkingDir, delegate, daemonFactory);
    }

    @Override
    protected DaemonForkOptions toDaemonOptions(ScalaJavaJointCompileSpec spec) {
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


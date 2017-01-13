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
import org.gradle.process.internal.daemon.WorkerDaemonFactory;
import org.gradle.process.internal.daemon.DaemonForkOptions;
import org.gradle.api.tasks.compile.ForkOptions;
import org.gradle.api.tasks.scala.ScalaForkOptions;
import org.gradle.language.base.internal.compile.Compiler;

import java.io.File;
import java.util.Arrays;

public class DaemonScalaCompiler<T extends ScalaJavaJointCompileSpec> extends AbstractDaemonCompiler<T> {
    private static final Iterable<String> SHARED_PACKAGES =
            Arrays.asList("scala", "com.typesafe.zinc", "xsbti", "com.sun.tools.javac", "sbt");
    private final Iterable<File> zincClasspath;

    public DaemonScalaCompiler(File daemonWorkingDir, Compiler<T> delegate, WorkerDaemonFactory daemonFactory, Iterable<File> zincClasspath) {
        super(daemonWorkingDir, delegate, daemonFactory);
        this.zincClasspath = zincClasspath;
    }

    @Override
    protected DaemonForkOptions toDaemonOptions(T spec) {
        return createJavaForkOptions(spec).mergeWith(createScalaForkOptions(spec));
    }

    private DaemonForkOptions createJavaForkOptions(T spec) {
        ForkOptions options = spec.getCompileOptions().getForkOptions();
        return new DaemonForkOptions(options.getMemoryInitialSize(), options.getMemoryMaximumSize(), options.getJvmArgs());
    }

    private DaemonForkOptions createScalaForkOptions(T spec) {
        ScalaForkOptions options = spec.getScalaCompileOptions().getForkOptions();
        return new DaemonForkOptions(options.getMemoryInitialSize(), options.getMemoryMaximumSize(),
                options.getJvmArgs(), zincClasspath, SHARED_PACKAGES);
    }
}


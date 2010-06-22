/*
 * Copyright 2010 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gradle.api.internal.tasks.scala;

import org.gradle.api.internal.TaskOutputsInternal;
import org.gradle.api.internal.tasks.compile.IncrementalJavaSourceCompiler;
import org.gradle.api.internal.tasks.compile.SimpleStaleClassCleaner;
import org.gradle.api.internal.tasks.compile.StaleClassCleaner;
import org.gradle.api.tasks.scala.ScalaCompileOptions;

import java.io.File;

public class IncrementalScalaCompiler extends IncrementalJavaSourceCompiler<ScalaJavaJointCompiler>
        implements ScalaJavaJointCompiler {
    private final TaskOutputsInternal taskOutputs;

    public IncrementalScalaCompiler(ScalaJavaJointCompiler compiler, TaskOutputsInternal taskOutputs) {
        super(compiler);
        this.taskOutputs = taskOutputs;
    }

    public ScalaCompileOptions getScalaCompileOptions() {
        return getCompiler().getScalaCompileOptions();
    }

    public void setScalaClasspath(Iterable<File> classpath) {
        getCompiler().setScalaClasspath(classpath);
    }

    @Override
    protected StaleClassCleaner createCleaner() {
        return new SimpleStaleClassCleaner(taskOutputs);
    }
}

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
package org.gradle.api.internal.tasks.compile;

import org.gradle.api.internal.TaskOutputsInternal;
import org.gradle.api.tasks.compile.GroovyCompileOptions;

import java.io.File;

public class IncrementalGroovyCompiler extends IncrementalJavaSourceCompiler<GroovyJavaJointCompiler> implements GroovyJavaJointCompiler {
    private final TaskOutputsInternal taskOutputs;

    public IncrementalGroovyCompiler(GroovyJavaJointCompiler compiler, TaskOutputsInternal taskOutputs) {
        super(compiler);
        this.taskOutputs = taskOutputs;
    }

    public GroovyCompileOptions getGroovyCompileOptions() {
        return getCompiler().getGroovyCompileOptions();
    }

    public void setGroovyClasspath(Iterable<File> classpath) {
        getCompiler().setGroovyClasspath(classpath);
    }

    @Override
    protected StaleClassCleaner createCleaner() {
        return new SimpleStaleClassCleaner(taskOutputs);
    }
}

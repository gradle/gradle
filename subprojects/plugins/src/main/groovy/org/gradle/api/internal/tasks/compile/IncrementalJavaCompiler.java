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

import org.gradle.api.AntBuilder;
import org.gradle.internal.Factory;
import org.gradle.api.internal.TaskOutputsInternal;

public class IncrementalJavaCompiler extends IncrementalJavaCompilerSupport<JavaCompiler> implements JavaCompiler {
    private final Factory<AntBuilder> antBuilderFactory;
    private final TaskOutputsInternal taskOutputs;

    public IncrementalJavaCompiler(JavaCompiler compiler, Factory<AntBuilder> antBuilderFactory,
                                   TaskOutputsInternal taskOutputs) {
        super(compiler);
        this.antBuilderFactory = antBuilderFactory;
        this.taskOutputs = taskOutputs;
    }

    protected StaleClassCleaner createCleaner() {
        if (getCompileOptions().isUseDepend()) {
            AntDependsStaleClassCleaner cleaner = new AntDependsStaleClassCleaner(antBuilderFactory);
            cleaner.setDependencyCacheDir(dependencyCacheDir);
            return cleaner;
        } else {
            return new SimpleStaleClassCleaner(taskOutputs);
        }
    }
}

/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.api.plugins.quality.internal.forking;

import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.file.FileCollection;
import org.gradle.internal.Factory;
import org.gradle.process.internal.WorkerProcessBuilder;


public class AntProcessBuilder {
    private final Project project;
    private final AntWorkerManager antWorkerManager;
    private FileCollection classpath;
    private AntExecutionSpec antExecutionSpec;
    private Factory<WorkerProcessBuilder> workerProcessBuilderFactory;

    public AntProcessBuilder(Project project) {
        this.project = project;
        antWorkerManager = new AntWorkerManager();
    }

    public AntProcessBuilder withClasspath(FileCollection classpath) {
        this.classpath = classpath;
        return this;
    }

    public AntProcessBuilder withAntExecutionSpec(AntExecutionSpec antExecutionSpec) {
        this.antExecutionSpec = antExecutionSpec;
        return this;
    }

    public AntProcessBuilder withWorkerProcessBuilderFactory(Factory<WorkerProcessBuilder> workerProcessBuilderFactory) {
        this.workerProcessBuilderFactory = workerProcessBuilderFactory;
        return this;
    }

    public AntResult execute() {
        if (null == classpath || null == antExecutionSpec || null == workerProcessBuilderFactory) {
            throw new GradleException("Unable to execute Ant task without classpath, antExecutionSpec, and workerProcessBuilderFactory");
        }

        AntWorkerSpec antWorkerSpec = new AntWorkerSpec(project.getProjectDir(), classpath, antExecutionSpec);
        return antWorkerManager.runAntTask(workerProcessBuilderFactory, antWorkerSpec);
    }
}

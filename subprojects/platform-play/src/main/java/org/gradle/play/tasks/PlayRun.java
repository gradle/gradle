/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.play.tasks;

import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.ConventionTask;
import org.gradle.api.tasks.TaskAction;
import org.gradle.internal.Factory;
import org.gradle.play.internal.run.PlayRunResult;
import org.gradle.play.internal.run.PlayRunSpec;
import org.gradle.play.internal.run.PlayRunWorkerManager;
import org.gradle.process.internal.WorkerProcessBuilder;

import javax.inject.Inject;

/**
 * A Task to run a play application.
 * */
public class PlayRun extends ConventionTask {


    private FileCollection classpath;

    public void setClasspath(FileCollection classpath) {
        this.classpath = classpath;
    }

    public FileCollection getClasspath() {
        return classpath;
    }

    @TaskAction public void run(){
        PlayRunSpec spec = generateSpec();
        PlayRunWorkerManager manager = new PlayRunWorkerManager();
        PlayRunResult result = manager.runWorker(getProject().getProjectDir(), getWorkerProcessBuilderFactory(), getClasspath(), spec);
    }

    @Inject
    public Factory<WorkerProcessBuilder> getWorkerProcessBuilderFactory() {
        throw new UnsupportedOperationException();
    }

    private PlayRunSpec generateSpec() {
        return new PlayRunSpec();
    }
}

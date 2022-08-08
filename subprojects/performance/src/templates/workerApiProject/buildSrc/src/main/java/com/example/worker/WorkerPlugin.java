/*
 * Copyright 2019 the original author or authors.
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

package com.example.worker;

import org.gradle.api.Action;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.workers.IsolationMode;

public class WorkerPlugin implements Plugin<Project> {
    @Override
    public void apply(final Project project) {
        project.getPluginManager().apply("base");
        project.getTasks().create("noIsolation", WorkerTask.class);

        project.getTasks().create("classloaderIsolation", WorkerTask.class, new Action<WorkerTask>() {
            @Override
            public void execute(WorkerTask workerTask) {
                workerTask.setIsolationMode(IsolationMode.CLASSLOADER);
            }
        });

        project.getTasks().create("processIsolation", WorkerTask.class, new Action<WorkerTask>() {
            @Override
            public void execute(WorkerTask workerTask) {
                workerTask.setIsolationMode(IsolationMode.PROCESS);
            }
        });

        project.getTasks().withType(WorkerTask.class, new Action<WorkerTask>() {
            @Override
            public void execute(WorkerTask workerTask) {
                Object maybeOutputSize = project.findProperty("outputSize");
                int outputSize = Integer.parseInt(maybeOutputSize == null ? "1" : maybeOutputSize.toString());
                workerTask.setOutputSize(outputSize);
            }
        });
    }
}

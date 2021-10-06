/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.api.tasks.compile;

import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.TaskAction;
import org.gradle.workers.WorkQueue;
import org.gradle.workers.WorkerExecutor;

import javax.inject.Inject;

public abstract class TurbineCompile extends AbstractCompile {
    @Inject
    public abstract WorkerExecutor getWorkerExecutor();

    @Classpath
    public abstract ConfigurableFileCollection getTurbineClasspath();

    @TaskAction
    public void doCompilation() {
        WorkerExecutor executor = getWorkerExecutor();
        WorkQueue queue = executor.processIsolation(spec -> {
           spec.getClasspath().from(getTurbineClasspath());
        });

        queue.submit(TurbineCompileAction.class, turbineWorkParameters -> {
            turbineWorkParameters.getSources().from(getSource());
            turbineWorkParameters.getClasspath().from(getClasspath());
            turbineWorkParameters.getOutput().set(getDestinationDirectory().file("ijar.jar"));
        });
    }

}

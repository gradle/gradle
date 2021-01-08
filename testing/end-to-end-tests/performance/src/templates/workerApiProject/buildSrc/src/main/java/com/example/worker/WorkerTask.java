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
import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;
import org.gradle.workers.IsolationMode;
import org.gradle.workers.WorkerConfiguration;
import org.gradle.workers.WorkerExecutor;

import javax.inject.Inject;
import java.io.File;

public class WorkerTask extends DefaultTask {
    private int outputSize = 1;
    private File outputDir = new File(getProject().getBuildDir(), getName());
    private IsolationMode isolationMode = IsolationMode.NONE;

    @Inject
    public WorkerExecutor getWorkerExecutor() {
        throw new UnsupportedOperationException();
    }

    @TaskAction
    public void executeTask() {
        for (int i = 0; i < outputSize; i++) {
            final int index = i;
            getWorkerExecutor().submit(UnitOfWork.class, new Action<WorkerConfiguration>() {
                @Override
                public void execute(WorkerConfiguration workerConfiguration) {
                    workerConfiguration.setDisplayName(getName() + " work for " + index);
                    workerConfiguration.setIsolationMode(isolationMode);

                    File outputFile = new File(outputDir, "out-" + index + ".txt");
                    workerConfiguration.setParams(index, outputFile);
                }
            });
        }
    }

    @OutputDirectory
    public File getOutputDir() {
        return outputDir;
    }

    @Input
    public int getOutputSize() {
        return outputSize;
    }

    public void setOutputSize(int outputSize) {
        this.outputSize = outputSize;
    }

    @Input
    public IsolationMode getIsolationMode() {
        return isolationMode;
    }

    public void setIsolationMode(IsolationMode isolationMode) {
        this.isolationMode = isolationMode;
    }
}
